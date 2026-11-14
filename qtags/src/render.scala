package qtags

import java.io.OutputStream

val HtmlContentType = "text/html; charset=utf-8"

/** Render to an HTML string. */
def renderString(f: Frag): String =
  val out = java.lang.StringBuilder()
  f(using StringHtml(out))
  out.toString

/** Render to a `geny.Writable`, e.g. to return from a cask endpoint or send with
  * requests. The page is rendered right away, so errors are thrown here, and
  * the content type and length are both known.
  *
  * The page is rendered straight to UTF-8 bytes, which are written out as
  * they are: no String or other copy of the page is made.
  */
def render(f: Frag): geny.Writable =
  val html = ByteHtml()
  f(using html)
  new geny.Writable:
    override def httpContentType = Some(HtmlContentType)
    override def contentLength = Some(html.size.toLong)
    def writeBytesTo(out: OutputStream): Unit = html.writeTo(out)

def renderWritable(
  sizeHint: Int
)(f: Frag): geny.Writable =
  val html = ByteHtml(initialSize = sizeHint)
  f(using html)
  new geny.Writable:
    override def httpContentType = Some(HtmlContentType)
    override def contentLength = Some(html.size.toLong)
    def writeBytesTo(out: OutputStream): Unit = html.writeTo(out)

/** Render to a `geny.Writable` that streams the page while rendering it,
  * without holding the whole page in memory: bytes are written out about
  * every `chunkSize` bytes. The content length is unknown.
  *
  * The fragment runs each time the Writable is written, not when `renderLazy`
  * is called. If it throws, part of the page may already have been sent.
  */
def renderLazy(f: Frag, chunkSize: Int = 8192): geny.Writable =
  new geny.Writable:
    override def httpContentType = Some(HtmlContentType)
    def writeBytesTo(out: OutputStream): Unit =
      val html = ByteHtml(chunkSize * 2, chunkSize, _.writeTo(out))
      f(using html)
      html.writeTo(out)
