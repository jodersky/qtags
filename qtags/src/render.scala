package qtags

import java.io.{OutputStream, OutputStreamWriter}
import java.nio.charset.StandardCharsets.UTF_8

val HtmlContentType = "text/html; charset=utf-8"

/** Render to an HTML string. */
def renderString(f: Frag): String =
  val out = java.lang.StringBuilder()
  f(using StringHtml(out))
  out.toString

/** Render to a `geny.Writable`, e.g. to return from a cask endpoint or send with
  * requests. The page is rendered right away, so errors are thrown here, and
  * the content type and length are both known.
  */
def render(f: Frag): geny.Writable =
  val bytes = renderString(f).getBytes(UTF_8)
  new geny.Writable:
    override def httpContentType = Some(HtmlContentType)
    override def contentLength = Some(bytes.length.toLong)
    def writeBytesTo(out: OutputStream): Unit = out.write(bytes)

/** Render to a `geny.Writable` that streams the page while rendering it,
  * without holding the whole page in memory. The content length is unknown.
  *
  * The fragment runs each time the Writable is written, not when `renderLazy`
  * is called. If it throws, part of the page may already have been sent.
  */
def renderLazy(f: Frag, chunkSize: Int = 8192): geny.Writable =
  new geny.Writable:
    override def httpContentType = Some(HtmlContentType)
    def writeBytesTo(out: OutputStream): Unit =
      val writer = OutputStreamWriter(out, UTF_8)
      val chars = new Array[Char](chunkSize)
      def drain(sb: java.lang.StringBuilder): Unit =
        var i = 0
        while i < sb.length do
          val n = math.min(chars.length, sb.length - i)
          sb.getChars(i, i + n, chars, 0)
          writer.write(chars, 0, n)
          i += n
      val sb = java.lang.StringBuilder(chunkSize * 2)
      f(using StringHtml(sb, chunkSize, drain))
      drain(sb)
      writer.flush()                      // don't close: `out` belongs to the caller
