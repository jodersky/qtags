package qtags

import org.scalajs.dom

/** Backend that builds real DOM nodes under `root`. Open for extension, e.g.
  * by backends that also track reactive bindings.
  */
open class DomHtml(root: dom.Node) extends Html:
  private var stack: List[dom.Node] = List(root)
  private var tagOpen = false            // same attr-ordering rule as StringHtml

  /** The element currently being built (the innermost open tag). */
  def current: dom.Node = stack.head

  def open(name: String, start: String): Unit =
    val e = dom.document.createElement(name)
    stack.head.appendChild(e)
    stack = e :: stack
    tagOpen = true

  def attr(name: String, prefix: String, value: String): Unit =
    if !tagOpen then throw IllegalStateException(s"attribute $name after content")
    stack.head.asInstanceOf[dom.Element].setAttribute(name, value)

  def text(s: String): Unit =
    appendNode(dom.document.createTextNode(s))

  /** Append any node as content of the current element. */
  def appendNode(node: dom.Node): Unit =
    tagOpen = false
    stack.head.appendChild(node)

  def raw(html: String): Unit =
    tagOpen = false
    stack.head.nodeName match
      // Content of these elements is plain text in the DOM, not markup.
      case "SCRIPT" | "STYLE" =>
        stack.head.appendChild(dom.document.createTextNode(html))
      case _ =>
        val t = dom.document.createElement("template").asInstanceOf[dom.HTMLTemplateElement]
        t.innerHTML = html
        stack.head.appendChild(t.content)

  def close(name: String, end: String): Unit =
    stack = stack.tail
    tagOpen = false

  def closeVoid(name: String): Unit =
    if !tagOpen then throw IllegalStateException(s"void element <$name> cannot have content")
    stack = stack.tail
    tagOpen = false

/** A fragment that needs the DOM backend, e.g. because it attaches event handlers. */
type DomFrag = DomHtml ?=> Unit

/** Attach an event listener to the enclosing element. Unlike attributes, this
  * may appear anywhere in the element's body. Only compiles inside a DomFrag.
  */
def on(event: String)(handler: dom.Event => Unit)(using h: DomHtml): Unit =
  h.current.addEventListener(event, handler)

def onClick(handler: dom.MouseEvent => Unit)(using h: DomHtml): Unit =
  h.current.addEventListener("click", handler)

/** Render `f` and append the resulting nodes to `parent`. */
def renderInto(parent: dom.Node)(f: DomFrag): Unit =
  f(using DomHtml(parent))

/** Render `f` into a new detached DocumentFragment. */
def renderDom(f: DomFrag): dom.DocumentFragment =
  val frag = dom.document.createDocumentFragment()
  renderInto(frag)(f)
  frag
