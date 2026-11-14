package backends

import qtags as h

/** A backend that writes indented HTML, one element or text per line, for
  * reading and debugging. (The added whitespace can change how inline content
  * is displayed, so it's not meant for serving pages.)
  *
  * A backend gets a stream of events: `open`, `attr`, `text`, `raw`, `close`
  * and `closeVoid`. Attributes come right after `open`, before any content, so
  * the start tag is left open (`<a href="..."`) until the first content or the
  * end of the element closes it with `>`.
  */
final class PrettyHtml(out: java.lang.StringBuilder, indent: String = "  ") extends h.Html:
  private var depth = 0
  private var tagOpen = false            // we've written "<div" but not ">"

  def open(name: String, start: String): Unit =
    closeOpen()
    newline()
    out.append(start)                    // `start` is "<" + name, precomputed
    tagOpen = true
    depth += 1

  def attr(name: String, prefix: String, value: String): Unit =
    if !tagOpen then throw IllegalStateException(s"attribute $name after content")
    out.append(prefix)                   // ` name="`
    h.escapeAttr(value, out)
    out.append('"')

  def text(s: String): Unit =
    closeOpen()
    newline()
    h.escapeText(s, out)

  def raw(html: String): Unit =
    closeOpen()
    newline()
    out.append(html)

  def close(name: String, end: String): Unit =
    closeOpen()
    depth -= 1
    newline()
    out.append(end)                      // "</" + name + ">"

  def closeVoid(name: String): Unit =
    if !tagOpen then throw IllegalStateException(s"void element <$name> cannot have content")
    out.append('>')
    tagOpen = false
    depth -= 1

  private def closeOpen(): Unit =
    if tagOpen then
      out.append('>')
      tagOpen = false

  private def newline(): Unit =
    if out.length > 0 then out.append('\n')
    for _ <- 0 until depth do out.append(indent)

/** Render `f` as indented HTML. */
def renderPretty(f: h.Frag): String =
  val out = java.lang.StringBuilder()
  f(using PrettyHtml(out))
  out.toString
