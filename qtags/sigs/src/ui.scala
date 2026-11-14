package qtags.sigs

import org.scalajs.dom
import qtags.{DomFrag, DomHtml, renderDom}
import Signal.{Scope, State, observe}
import scala.scalajs.js

// So that `import qtags.sigs.*` is enough to write a reactive UI. (Not
// `Signal.scope`: it would clash with the `scope` attribute from `qtags.*`.)
export Signal.{Computed, State, batch, observe}

/** Reactive UI: qtags fragments whose parts update when signals change.
  *
  * Updates are fine-grained: no virtual DOM, no diffing. Each binding observes
  * one signal and updates exactly the DOM it created:
  *
  *   - `bindText(sig)`, `bindAttr(name, sig)`, `bindProp(name, sig)` keep one
  *     text node, attribute or DOM property equal to a signal;
  *   - `dyn(sig) { value => ... }` re-renders a region when a signal changes;
  *   - `each(items, key) { ... }` renders a keyed list, reusing and moving the
  *     DOM of items that stay.
  *
  * Bindings take signals: build them with `map`, `zip`, or `Computed { ... }`.
  *
  * Clean-up relies on signal scopes (see Signal.scala): `mount` renders inside a scope, each `dyn`
  * render and list item gets its own, and every binding belongs to the scope
  * it was created in. So when a region is re-rendered or removed, the bindings
  * inside it are disposed, and removed DOM is not kept alive by the signals it
  * observed. Bindings must therefore be created while rendering inside
  * `mount`; creating one elsewhere (e.g. in an event handler) throws.
  *
  * ```scala
  * val count = Signal.State(0)
  * mount(dom.document.body) {
  *   p { bindText(count.map(n => s"Clicked $n times")) }
  *   button { text("+1"); onClick(_ => count.update(_ + 1)) }
  * }
  * ```
  */

/** Render `view` at the end of `parent`, and keep it up to date until the
  * returned scope is disposed, which also removes the view's DOM.
  */
def mount(parent: dom.Node)(view: DomFrag): Scope =
  val region = Region.create()
  parent.appendChild(region.start)
  parent.appendChild(region.end)
  val scope = Signal.scope {}
  scope.onDispose(() => region.remove())   // registered first, so it runs last
  try scope.run { region.fill(renderDom(view)) }
  catch
    case e: Throwable =>
      scope.dispose()
      throw e
  scope

/** A text node showing `sig`, updated in place when it changes. */
def bindText(sig: Signal[String])(using h: DomHtml): Unit =
  requireScope("bindText")
  val node = dom.document.createTextNode("")
  h.appendNode(node)
  observe { node.data = sig() }

/** Attribute `name` of the enclosing element, kept equal to `sig`. Like event
  * handlers, it may appear anywhere in the element's body.
  */
def bindAttr(name: String, sig: Signal[String])(using h: DomHtml): Unit =
  requireScope("bindAttr")
  val el = h.current.asInstanceOf[dom.Element]
  observe { el.setAttribute(name, sig()) }

/** DOM property `name` of the enclosing element (e.g. `checked`, `value`),
  * kept equal to `sig`. Use it instead of an attribute for state the user can
  * change, since attributes only set the initial value.
  */
def bindProp[A](name: String, sig: Signal[A])(using h: DomHtml): Unit =
  requireScope("bindProp")
  val el = h.current.asInstanceOf[js.Dynamic]
  observe { el.updateDynamic(name)(sig().asInstanceOf[js.Any]) }

/** A region showing `render(value)`, rendered again whenever `sig` changes
  * value. Bindings inside it are disposed before each re-render.
  *
  * Choose `sig` to change only when the region should: e.g.
  * `dyn(hasTodos) { has => ... }` with `hasTodos = todos.map(_.nonEmpty)`
  * re-renders when the list becomes (non-)empty, not on every change to it.
  */
def dyn[A](sig: Signal[A])(render: A => DomFrag)(using h: DomHtml): Unit =
  requireScope("dyn")
  val region = Region.create()
  h.appendNode(region.start)
  h.appendNode(region.end)
  observe {
    val content = renderDom(render(sig()))
    region.clear()
    region.fill(content)
  }

/** A keyed list: one item per element of `items`, identified by `key`.
  *
  * When `items` changes, items whose key stays keep their DOM (moved if the
  * order changed), new keys are rendered, and removed ones are disposed.
  * `render` gets each item's key and a signal of its current value, so an
  * item whose value changes (same key) updates through its own bindings.
  */
def each[A, K](items: Signal[Seq[A]], key: A => K)(render: (K, Signal[A]) => DomFrag)(
    using h: DomHtml
): Unit =
  requireScope("each")
  final class Entry(val value: State[A], val region: Region, val scope: Scope)

  val list = Region.create()
  h.appendNode(list.start)
  h.appendNode(list.end)

  // Items live across updates, so they belong to this scope rather than to the
  // observer below, whose runs each get a scope that is disposed on the next.
  val itemScopes = Signal.scope {}
  var entries = Map.empty[K, Entry]

  def newEntry(k: K, a: A): Entry =
    val value = State(a)
    val region = Region.create()
    val frag = dom.document.createDocumentFragment()
    frag.appendChild(region.start)
    val scope = itemScopes.run { Signal.scope { frag.appendChild(renderDom(render(k, value))) } }
    frag.appendChild(region.end)
    Entry(value, region, scope)

  def update(next: Seq[A]): Unit =
    val keys = next.map(key)
    if keys.distinct.length != keys.length then
      throw IllegalArgumentException(s"each: duplicate keys in ${keys.mkString(", ")}")

    val keep = keys.toSet
    for (k, e) <- entries if !keep(k) do
      e.scope.dispose()
      e.region.remove()

    // Walk the list in its new order. `cursor` is where the next item belongs;
    // items already there stay put, others are moved (or inserted) there.
    var cursor: dom.Node = list.start.nextSibling
    val updated = Map.newBuilder[K, Entry]
    for (a, k) <- next.zip(keys) do
      val e = entries.get(k) match
        case Some(e) =>
          e.value.set(a)
          e
        case None =>
          newEntry(k, a)
      if e.region.start eq cursor then cursor = e.region.end.nextSibling
      else e.region.moveBefore(cursor)
      updated += k -> e
    entries = updated.result()

  observe { update(items()) }

private def requireScope(what: String): Unit =
  if Signal.currentScope.isEmpty then
    throw IllegalStateException(
      s"$what must be used while rendering inside mount, so that it can be cleaned up"
    )

/** A range of sibling nodes delimited by two empty comment markers, so it can
  * be refilled or moved even when it holds no elements.
  */
private[sigs] final class Region(val start: dom.Comment, val end: dom.Comment):

  def clear(): Unit =
    while start.nextSibling ne end do start.parentNode.removeChild(start.nextSibling)

  def fill(content: dom.Node): Unit =
    end.parentNode.insertBefore(content, end)

  /** Move the whole range, markers included, before `ref`. */
  def moveBefore(ref: dom.Node): Unit =
    val parent = ref.parentNode
    var node: dom.Node = start
    var done = false
    while !done do
      val next = node.nextSibling
      done = node eq end
      parent.insertBefore(node, ref)
      node = next

  def remove(): Unit =
    clear()
    start.parentNode.removeChild(start)
    end.parentNode.removeChild(end)

private[sigs] object Region:
  def create(): Region =
    Region(dom.document.createComment(""), dom.document.createComment(""))
