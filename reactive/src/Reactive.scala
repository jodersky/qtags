package reactive

import org.scalajs.dom
import qtags.DomHtml
import sigs.Signal
import sigs.Signal.{Computed, State, Watcher}
import scala.collection.mutable
import scala.scalajs.js

/** Reactive DOM: qtags fragments whose parts update when signals change.
  *
  * Updates are fine-grained: no virtual DOM, no diffing. Each binding is a
  * signal watcher that updates exactly the DOM it created:
  *
  *   - `bindText(sig)`, `bindAttr(name, sig)`, `bindProp(name, sig)` keep one
  *     text node, attribute or DOM property equal to a signal;
  *   - `each(items, key) { ... }` renders a keyed list from a signal, reusing
  *     and moving the DOM of items that stay;
  *   - `dyn { ... }` re-renders a region (e.g. an `if`) when the signals it
  *     reads change.
  *
  * Bindings take signals, e.g. built with `map`, or `Computed { ... }` for
  * anything more involved. Inside `dyn` and `Computed` blocks, read signals
  * with `s()`. Outside them `s()` does not compile; use `s.now` for a one-off
  * read.
  *
  * Every watcher belongs to an [[Owner]], carried by the rendering context.
  * When a region is re-rendered or removed, its owner disposes everything
  * created inside it, so removed DOM is not kept alive by the signals it
  * watched.
  *
  * {{{
  * val count = Signal.State(0)
  * mount(dom.document.body) {
  *   p { bindText(count.map(n => s"Clicked $n times")) }
  *   button { text("+1"); onClick(_ => count.update(_ + 1)) }
  * }
  * }}}
  */

/** A view: a qtags fragment that may contain reactive bindings. */
type View = ReactiveHtml ?=> Unit

/** The DOM backend used while rendering a view: also knows the owner that
  * bindings created here belong to.
  */
final class ReactiveHtml(root: dom.Node, val owner: Owner) extends DomHtml(root)

/** Collects clean-up actions for a part of the page, run by `dispose`. */
final class Owner:
  private val cleanups = mutable.ArrayBuffer.empty[() => Unit]
  private var disposed = false

  def onDispose(f: () => Unit): Unit =
    if disposed then f() else cleanups += f

  /** A watcher that is disposed with this owner. */
  def watch(upstream: Signal[?])(fn: () => Unit): Watcher =
    val w = Watcher(fn, upstream)
    onDispose(() => w.dispose())
    w

  def dispose(): Unit =
    if !disposed then
      disposed = true
      cleanups.reverseIterator.foreach(_())
      cleanups.clear()

/** A mounted view. */
final class Mounted private[reactive] (owner: Owner, region: Region):
  /** Remove the view's DOM and stop all of its bindings. */
  def dispose(): Unit =
    owner.dispose()
    region.remove()

/** Render `view` at the end of `parent`, and keep it up to date. */
def mount(parent: dom.Node)(view: View): Mounted =
  val owner = Owner()
  val region = Region.create()
  parent.appendChild(region.start)
  parent.appendChild(region.end)
  region.fill(render(owner)(view))
  Mounted(owner, region)

/** A text node showing `sig`, updated in place when it changes. */
def bindText(sig: Signal[String])(using h: ReactiveHtml): Unit =
  val node = dom.document.createTextNode("")
  h.owner.watch(sig)(() => node.data = sig.now)
  node.data = sig.now
  h.appendNode(node)

/** Attribute `name` of the enclosing element, kept equal to `sig`. Like event
  * handlers, it may appear anywhere in the element's body.
  */
def bindAttr(name: String, sig: Signal[String])(using h: ReactiveHtml): Unit =
  val el = h.current.asInstanceOf[dom.Element]
  h.owner.watch(sig)(() => el.setAttribute(name, sig.now))
  el.setAttribute(name, sig.now)

/** DOM property `name` of the enclosing element (e.g. `checked`, `value`),
  * kept equal to `sig`. Use it instead of an attribute for state the user can
  * change, since attributes only set the initial value.
  */
def bindProp[A](name: String, sig: Signal[A])(using h: ReactiveHtml): Unit =
  val el = h.current.asInstanceOf[js.Dynamic]
  def set(): Unit = el.updateDynamic(name)(sig.now.asInstanceOf[js.Any])
  h.owner.watch(sig)(() => set())
  set()

/** A region rendered by `body`, and rendered again whenever a signal it reads
  * changes. Bindings inside it have their own owner, disposed on each
  * re-render.
  *
  * Keep what `body` reads small: e.g. `dyn { if hasTodos() then ... }` with
  * `hasTodos = Computed { todos().nonEmpty }` re-renders when emptiness
  * changes, not on every change to `todos`.
  */
def dyn(body: (Computed[?], ReactiveHtml) ?=> Unit)(using h: ReactiveHtml): Unit =
  val region = Region.create()
  h.appendNode(region.start)
  h.appendNode(region.end)

  val rendered = Computed[(Owner, dom.DocumentFragment)](
    {
      val c = summon[Computed[(Owner, dom.DocumentFragment)]]
      val owner = Owner()
      (owner, render(owner)((rh: ReactiveHtml) ?=> body(using c, rh)))
    },
    isEqual = (_, _) => false          // every render produces new DOM
  )

  var current: Owner | Null = null
  def show(): Unit =
    val (owner, frag) = rendered.now
    if current != null then current.nn.dispose()
    current = owner
    region.clear()
    region.fill(frag)

  val w = Watcher(() => show(), rendered)
  show()
  h.owner.onDispose { () =>
    w.dispose()
    if current != null then current.nn.dispose()
  }

/** A keyed list: one item per element of `items`, identified by `key`.
  *
  * When `items` changes, items whose key stays keep their DOM (moved if the
  * order changed), new keys are rendered, and removed ones are disposed.
  * `render` gets each item's key and a signal of its current value, so an
  * item whose value changes (same key) updates through its own bindings.
  */
def each[A, K](items: Signal[Seq[A]], key: A => K)(render: (K, Signal[A]) => View)(
    using h: ReactiveHtml
): Unit =
  final class Entry(val value: State[A], val region: Region, val owner: Owner)

  val list = Region.create()
  h.appendNode(list.start)
  h.appendNode(list.end)

  var entries = Map.empty[K, Entry]

  def newEntry(k: K, a: A): Entry =
    val value = State(a)
    val owner = Owner()
    val region = Region.create()
    val frag = dom.document.createDocumentFragment()
    frag.appendChild(region.start)
    frag.appendChild(reactive.render(owner)(render(k, value)))
    frag.appendChild(region.end)
    Entry(value, region, owner)

  def update(): Unit =
    val next = items.now
    val keys = next.map(key)
    if keys.distinct.length != keys.length then
      throw IllegalArgumentException(s"each: duplicate keys in ${keys.mkString(", ")}")

    val keep = keys.toSet
    for (k, e) <- entries if !keep(k) do
      e.owner.dispose()
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

  val w = Watcher(() => update(), items)
  update()
  h.owner.onDispose { () =>
    w.dispose()
    entries.values.foreach(_.owner.dispose())
  }

/** Render `view` into a new DocumentFragment, with bindings owned by `owner`.
  * If rendering fails, what was created so far is disposed.
  */
private def render(owner: Owner)(view: View): dom.DocumentFragment =
  val frag = dom.document.createDocumentFragment()
  try view(using ReactiveHtml(frag, owner))
  catch
    case e: Throwable =>
      owner.dispose()
      throw e
  frag

/** A range of sibling nodes delimited by two empty comment markers, so it can
  * be refilled or moved even when it holds no elements.
  */
private[reactive] final class Region(val start: dom.Comment, val end: dom.Comment):

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

private[reactive] object Region:
  def create(): Region =
    Region(dom.document.createComment(""), dom.document.createComment(""))
