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
  /** Start an element. Until its first content, the element's start tag is
    * still open for attributes. Every `open` is matched by a `close`, or by
    * `closeVoid` for a void element.
    *
    * @param name  the element's name, e.g. `div`
    * @param start the markup that begins the element's start tag: `<` followed
    *              by the name, e.g. `<div`. It exists for speed: tags pass a
    *              literal name, so the compiler builds this string at compile
    *              time, and backends that write HTML text append it in one
    *              call instead of building it from `name` on every render.
    *              Other backends, like the DOM backend, use `name`.
    */
  def open(name: String, start: String): Unit

  /** Add an attribute to the element just opened. Throws if the element
    * already has content.
    *
    * @param name   the attribute's name, e.g. `class`
    * @param prefix the markup that comes before the attribute's value: a
    *               space, the name, `=` and an opening quote, e.g. ` class="`.
    *               Like `start` in `open`, it is built at compile time so
    *               that backends that write HTML text can append it in one
    *               call, then the escaped value and a closing `"`. Other
    *               backends use `name`.
    * @param value  the attribute's value, as plain text. Backends that write
    *               HTML text must escape it.
    */
  def attr(name: String, prefix: String, value: String): Unit

  /** Add text content to the current element.
    *
    * @param s plain text. Backends that write HTML text must escape it.
    */
  def text(s: String): Unit

  /** Insert trusted HTML markup as-is, without escaping. */
  def raw(html: String): Unit

  /** End the current element.
    *
    * @param name the element's name, e.g. `div`
    * @param end  the element's end tag, e.g. `</div>`. Like `start` in
    *             `open`, it is built at compile time so that backends that
    *             write HTML text can append it in one call. Other backends
    *             use `name`.
    */
  def close(name: String, end: String): Unit

  /** End the current element, a void element such as `br` or `img`, which has
    * no end tag. Throws if it was given content.
    *
    * @param name the element's name, e.g. `br`
    */
  def closeVoid(name: String): Unit

/** A fragment that can be rendered by any backend. Backend-specific fragments
  * use a subtype in the context, e.g. `DomHtml ?=> Unit` on Scala.js; a `Frag`
  * can be used wherever one of those is expected.
  *
  * Tags become a `Frag` where one is expected: as an argument of type `Frag`,
  * or as the value of a `def` or `val` declared as `Frag`. A `val` needs that
  * type: `val x: Frag = p { ... }` is a fragment, to be rendered wherever `x`
  * is used. Without it, what `val x = p { ... }` does depends on where it is:
  *
  *   - outside a fragment, it doesn't compile (no given `Html`)
  *   - inside a fragment, e.g. in the body of a `def` returning a `Frag`, the
  *     given `Html` is in scope, so the `p` is rendered on the spot, where the
  *     `val` is, and `x` is `()`. This compiles, and so does using `x` where
  *     a `Frag` is expected, e.g. `div { x }`: `()` is a `Frag` which renders
  *     nothing.
  *
  * ```scala
  * def page: Frag =
  *   val nav = ul { ... }    // renders the ul here, before the div
  *   div { nav }             // an empty div
  *
  * def page: Frag =
  *   val nav: Frag = ul { ... }
  *   div { nav }             // the ul inside the div
  * ```
  *
  * The same goes for anything else that infers its type from a fragment
  * rather than being told it, e.g. `Some(p { ... })` assigned to an untyped
  * `val` holds `Some(())`.
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
  * `renderString { doctype(); html { ... } }`.
  * In the DOM backend it adds nothing, since a live document already has its
  * doctype.
  */
inline def doctype(inline tpe: String = "html")(using h: Html): Unit = h.raw(s"<!DOCTYPE $tpe>")
