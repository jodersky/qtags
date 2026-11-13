package qtags

/** Backend that writes HTML text into a StringBuilder.
  *
  * For streaming, pass `flushAt` and `flush`: after an element is closed, once
  * `out` holds at least `flushAt` chars, `flush(out)` is called and `out` is
  * cleared. Flushing only happens between elements, never inside a start tag.
  */
final class StringHtml(
    out: java.lang.StringBuilder,
    flushAt: Int = Int.MaxValue,
    flush: java.lang.StringBuilder => Unit = null
) extends Html:
  private var tagOpen = false            // we've written "<div" but not ">"

  private def closeOpen(): Unit =
    if tagOpen then
      out.append('>')
      tagOpen = false

  def open(name: String, start: String): Unit =
    closeOpen()
    out.append(start)
    tagOpen = true

  def attr(name: String, prefix: String, value: String): Unit =
    if !tagOpen then throw IllegalStateException(s"attribute $name after content")
    out.append(prefix)
    escapeAttr(value, out)
    out.append('"')

  def text(s: String): Unit =
    closeOpen()
    escapeText(s, out)

  def raw(html: String): Unit =
    closeOpen()
    out.append(html)

  def close(name: String, end: String): Unit =
    closeOpen()
    out.append(end)
    maybeFlush()

  def closeVoid(name: String): Unit =
    if !tagOpen then throw IllegalStateException(s"void element <$name> cannot have content")
    out.append('>')
    tagOpen = false
    maybeFlush()

  private def maybeFlush(): Unit =
    if out.length >= flushAt then
      flush(out)
      out.setLength(0)

// Escaping. Both scan for the first char needing escape and copy clean runs in
// bulk, so the common case (nothing to escape) is a single append.

/** Escape for a double-quoted attribute value: & " < > */
def escapeAttr(s: String, out: java.lang.StringBuilder): Unit =
  var start = 0
  var i = 0
  val n = s.length
  while i < n do
    val rep = s.charAt(i) match
      case '&' => "&amp;"
      case '"' => "&quot;"
      case '<' => "&lt;"
      case '>' => "&gt;"
      case _   => null
    if rep != null then
      out.append(s, start, i).append(rep)
      start = i + 1
    i += 1
  out.append(s, start, n)

/** Escape for text content: & < > */
def escapeText(s: String, out: java.lang.StringBuilder): Unit =
  var start = 0
  var i = 0
  val n = s.length
  while i < n do
    val rep = s.charAt(i) match
      case '&' => "&amp;"
      case '<' => "&lt;"
      case '>' => "&gt;"
      case _   => null
    if rep != null then
      out.append(s, start, i).append(rep)
      start = i + 1
    i += 1
  out.append(s, start, n)
