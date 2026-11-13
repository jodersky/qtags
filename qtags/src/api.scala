package qtags

/** A rendering backend. Tags and components emit a stream of events into it.
  *
  * Contract (every backend enforces it, so fragments behave the same
  * everywhere): `attr` is only valid right after `open`, before any child
  * content of that element.
  *
  * Methods that take a name also get the matching markup, precomputed: `start`
  * is `<name`, `end` is `</name>` and `prefix` is ` name="`. The tag and
  * attribute helpers pass literal names, so the compiler folds these into
  * constants. Backends that write markup append them in a single call
  * (measurably faster than appending the pieces); others ignore them.
  */
trait Html:
  def open(name: String, start: String): Unit
  def attr(name: String, prefix: String, value: String): Unit
  def text(s: String): Unit
  /** Insert trusted HTML markup as-is, without escaping. */
  def raw(html: String): Unit
  def close(name: String, end: String): Unit
  /** Close a void element (br, img, ...). Throws if it was given content. */
  def closeVoid(name: String): Unit

/** A fragment that can be rendered by any backend. Backend-specific fragments
  * use a subtype in the context, e.g. `DomHtml ?=> Unit` on Scala.js; a `Frag`
  * can be used wherever one of those is expected.
  */
type Frag = Html ?=> Unit

inline def tag(inline name: String)(inline body: Frag)(using h: Html): Unit =
  h.open(name, "<" + name)
  body                                    // applied with the given h
  h.close(name, "</" + name + ">")

/** A void element: no content and no end tag; `attrs` may only set attributes. */
inline def voidTag(inline name: String)(inline attrs: Frag)(using h: Html): Unit =
  h.open(name, "<" + name)
  attrs
  h.closeVoid(name)

// Attributes are statements in the body, written while the start tag is still open
inline def attr(inline name: String, value: String)(using h: Html): Unit =
  h.attr(name, " " + name + "=\"", value)

inline def text(s: String)(using h: Html): Unit = h.text(s)

/** Insert `html` without escaping. Only use it with trusted input: it is the
  * one way to produce markup qtags doesn't check. Use it for the content of
  * `<script>` and `<style>`, where escaped text would be wrong (`a &lt; b`).
  * There, make sure the content doesn't contain `</script>` or `</style>`.
  */
inline def raw(html: String)(using h: Html): Unit = h.raw(html)

/** `<!DOCTYPE html>`, to put before the `html` tag of a full page:
  * `renderString { doctype; html { ... } }`.
  * In the DOM backend it adds nothing, since a live document already has its
  * doctype.
  */
inline def doctype(using h: Html): Unit = h.raw("<!DOCTYPE html>")
