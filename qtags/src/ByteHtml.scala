package qtags

import java.io.OutputStream

/** Backend that writes HTML as UTF-8 bytes into a growable array, encoding as
  * it goes. `size` is the number of bytes so far, and `writeTo` writes them
  * out without copying.
  *
  * For streaming, pass `flushAt` and `flush`: after an element is closed, once
  * the buffer holds at least `flushAt` bytes, `flush(this)` is called (it
  * should `writeTo` somewhere) and the buffer is cleared. Flushing only
  * happens between elements, never inside a start tag.
  *
  * A lone surrogate, which UTF-8 can't encode, is written as `?`, as the JDK
  * does.
  */
final class ByteHtml(
    initialSize: Int = 8192,
    flushAt: Int = Int.MaxValue,
    flush: ByteHtml => Unit = null
) extends Html:
  private var buf = new Array[Byte](math.max(initialSize, 16))
  private var pos = 0
  private var tagOpen = false            // we've written "<div" but not ">"

  def size: Int = pos

  def writeTo(out: OutputStream): Unit = out.write(buf, 0, pos)

  // ---- Html -----------------------------------------------------------------

  def open(name: String, start: String): Unit =
    closeOpen()
    append(start)
    tagOpen = true

  def attr(name: String, prefix: String, value: String): Unit =
    if !tagOpen then throw IllegalStateException(s"attribute $name after content")
    append(prefix)
    escape(value, attr = true)
    appendAscii('"')

  def text(s: String): Unit =
    closeOpen()
    escape(s, attr = false)

  def raw(html: String): Unit =
    closeOpen()
    append(html)

  def close(name: String, end: String): Unit =
    closeOpen()
    append(end)
    maybeFlush()

  def closeVoid(name: String): Unit =
    if !tagOpen then throw IllegalStateException(s"void element <$name> cannot have content")
    appendAscii('>')
    tagOpen = false
    maybeFlush()

  // ---- internals ------------------------------------------------------------

  private def closeOpen(): Unit =
    if tagOpen then
      appendAscii('>')
      tagOpen = false

  private def maybeFlush(): Unit =
    if pos >= flushAt then
      flush(this)
      pos = 0

  private def ensure(n: Int): Unit =
    if pos + n > buf.length then
      buf = java.util.Arrays.copyOf(buf, math.max(buf.length * 2, pos + n))

  private def appendAscii(c: Char): Unit =
    ensure(1)
    buf(pos) = c.toByte
    pos += 1

  private def append(s: String): Unit = append(s, 0, s.length)

  /** Encode `s(start until end)`. A char takes at most 3 bytes (a surrogate
    * pair 4 for its 2 chars), so one `ensure` covers the whole range.
    */
  private def append(s: String, start: Int, end: Int): Unit =
    ensure(3 * (end - start))
    val buf = this.buf
    var pos = this.pos
    var i = start
    while i < end do
      val c = s.charAt(i)
      if c < 0x80 then
        buf(pos) = c.toByte
        pos += 1
      else if c < 0x800 then
        buf(pos) = (0xc0 | (c >> 6)).toByte
        buf(pos + 1) = (0x80 | (c & 0x3f)).toByte
        pos += 2
      else if Character.isHighSurrogate(c) && i + 1 < end && Character.isLowSurrogate(s.charAt(i + 1)) then
        val cp = Character.toCodePoint(c, s.charAt(i + 1))
        buf(pos) = (0xf0 | (cp >> 18)).toByte
        buf(pos + 1) = (0x80 | ((cp >> 12) & 0x3f)).toByte
        buf(pos + 2) = (0x80 | ((cp >> 6) & 0x3f)).toByte
        buf(pos + 3) = (0x80 | (cp & 0x3f)).toByte
        pos += 4
        i += 1
      else if Character.isSurrogate(c) then
        buf(pos) = '?'.toByte
        pos += 1
      else
        buf(pos) = (0xe0 | (c >> 12)).toByte
        buf(pos + 1) = (0x80 | ((c >> 6) & 0x3f)).toByte
        buf(pos + 2) = (0x80 | (c & 0x3f)).toByte
        pos += 3
      i += 1
    this.pos = pos

  /** Escape & < > (and " in attribute values), copying clean runs in bulk.
    * Runs are split only at those chars, never inside a surrogate pair.
    */
  private def escape(s: String, attr: Boolean): Unit =
    var start = 0
    var i = 0
    val n = s.length
    while i < n do
      val rep = s.charAt(i) match
        case '&'         => "&amp;"
        case '<'         => "&lt;"
        case '>'         => "&gt;"
        case '"' if attr => "&quot;"
        case _           => null
      if rep != null then
        append(s, start, i)
        append(rep)
        start = i + 1
      i += 1
    append(s, start, n)
