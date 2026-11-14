package todomvc

import org.scalajs.dom
import qtags as h
import qtags.sigs
import scala.scalajs.js

/** TodoMVC (https://todomvc.com), rendered in the browser with qtags and
  * reactive signals.
  *
  * The state is a few signals. The view is rendered once; after that, each
  * change updates only the DOM that depends on it: `sigs.bindText`/`bindAttr`/
  * `bindProp` keep single nodes equal to a signal, `sigs.dyn` re-renders a
  * region, and `sigs.each` keeps one element per todo, matched by id. Elements are never needlessly
  * replaced, so focus and cursor position are kept.
  */

final case class Todo(id: Int, title: String, completed: Boolean)

enum Filter(val hash: String, val label: String):
  case All       extends Filter("#/", "All")
  case Active    extends Filter("#/active", "Active")
  case Completed extends Filter("#/completed", "Completed")

  def accepts(t: Todo): Boolean = this match
    case All       => true
    case Active    => !t.completed
    case Completed => t.completed

object Filter:
  def fromHash(hash: String): Filter = values.find(_.hash == hash).getOrElse(All)

/** Where todos are saved: `localStorage` in the browser, memory in tests. */
trait Storage:
  def load(): Option[String]
  def save(json: String): Unit

object Storage:
  def local(key: String): Storage = new Storage:
    def load() = Option(dom.window.localStorage.getItem(key))
    def save(json: String) = dom.window.localStorage.setItem(key, json)

class TodoApp(root: dom.Element, storage: Storage):

  // ---- state ------------------------------------------------------------------

  val todos = sigs.State(load())
  val filter = sigs.State(Filter.All)
  private val editing = sigs.State(Option.empty[Int])

  // Derived values. Their equality cut-off matters: e.g. `hasTodos` only
  // changes when the list becomes (non-)empty, so the `dyn` on it does not
  // re-render on every edit.
  private val hasTodos = todos.map(_.nonEmpty)
  private val left = todos.map(_.count(!_.completed))
  private val anyCompleted = todos.map(_.exists(_.completed))
  private val visible = sigs.Computed { todos().filter(filter().accepts) }

  private val saver = sigs.observe { save(todos()) }

  // ---- changes ----------------------------------------------------------------

  private def add(title: String): Unit =
    val t = title.trim
    if t.nonEmpty then
      val id = todos.now.map(_.id).maxOption.getOrElse(0) + 1
      todos.update(_ :+ Todo(id, t, completed = false))

  private def modify(id: Int)(f: Todo => Todo): Unit =
    todos.update(_.map(t => if t.id == id then f(t) else t))

  private def remove(id: Int): Unit = todos.update(_.filterNot(_.id == id))

  private def startEdit(id: Int, edit: dom.HTMLInputElement): Unit =
    editing.set(Some(id))
    edit.value = todos.now.find(_.id == id).fold("")(_.title)
    edit.focus()

  /** Save an edit. Called on Enter and on blur; whichever comes second is a no-op. */
  private def commitEdit(id: Int, title: String): Unit =
    if editing.now.contains(id) then
      sigs.batch {
        editing.set(None)
        val t = title.trim
        if t.isEmpty then remove(id) else modify(id)(_.copy(title = t))
      }

  // ---- view -------------------------------------------------------------------

  private val mounted = sigs.mount(root) {
    h.header {
      h.cls("header")
      h.h1 { h.text("todos") }
      h.input {
        h.cls("new-todo"); h.placeholder("What needs to be done?"); h.autofocus
        h.on("keydown") { e =>
          if key(e) == "Enter" then
            val field = target(e)
            add(field.value)
            field.value = ""
        }
      }
    }

    sigs.dyn(hasTodos) { has =>
      if has then
        h.section {
          h.cls("main")
          h.input {
            h.id("toggle-all"); h.cls("toggle-all"); h.tpe("checkbox")
            sigs.bindProp("checked", left.map(_ == 0))
            h.on("change") { e =>
              val completed = target(e).checked
              todos.update(_.map(_.copy(completed = completed)))
            }
          }
          h.label { h.`for`("toggle-all"); h.text("Mark all as complete") }
          h.ul {
            h.cls("todo-list")
            sigs.each(visible, _.id) { (id, todo) => item(id, todo) }
          }
        }
        footerView
    }
  }

  private def item(id: Int, todo: sigs.Signal[Todo]): h.DomFrag =
    h.li {
      sigs.bindAttr("class", sigs.Computed {
        Seq("completed" -> todo().completed, "editing" -> editing().contains(id))
          .collect { case (c, true) => c }.mkString(" ")
      })
      h.div {
        h.cls("view")
        h.input {
          h.cls("toggle"); h.tpe("checkbox")
          sigs.bindProp("checked", todo.map(_.completed))
          h.on("change") { _ => modify(id)(t => t.copy(completed = !t.completed)) }
        }
        h.label {
          sigs.bindText(todo.map(_.title))
          h.on("dblclick") { e =>
            val li = e.target.asInstanceOf[dom.Element].closest("li")
            startEdit(id, li.querySelector(".edit").asInstanceOf[dom.HTMLInputElement])
          }
        }
        h.button {
          h.cls("destroy")
          h.on("click") { _ => remove(id) }
        }
      }
      // Always present; the stylesheet only shows it while the item is editing.
      h.input {
        h.cls("edit")
        h.on("keydown") { e =>
          key(e) match
            case "Enter"  => commitEdit(id, target(e).value)
            case "Escape" => editing.set(None)
            case _        =>
        }
        h.on("blur") { e => commitEdit(id, target(e).value) }
      }
    }

  private def footerView: h.DomFrag =
    h.footer {
      h.cls("footer")
      h.span {
        h.cls("todo-count")
        h.strong { sigs.bindText(left.map(_.toString)) }
        sigs.bindText(left.map(n => if n == 1 then " item left" else " items left"))
      }
      h.ul {
        h.cls("filters")
        for f <- Filter.values do
          h.li {
            h.a {
              h.href(f.hash)
              sigs.bindAttr("class", filter.map(current => if current == f then "selected" else ""))
              h.text(f.label)
            }
          }
      }
      sigs.dyn(anyCompleted) { any =>
        if any then
          h.button {
            h.cls("clear-completed")
            h.text("Clear completed")
            h.on("click") { _ => todos.update(_.filterNot(_.completed)) }
          }
      }
    }

  private def key(e: dom.Event): String = e.asInstanceOf[dom.KeyboardEvent].key
  private def target(e: dom.Event): dom.HTMLInputElement = e.target.asInstanceOf[dom.HTMLInputElement]

  /** Remove the app from the page and stop all of its watchers. */
  def dispose(): Unit =
    mounted.dispose()
    saver.dispose()

  // ---- persistence ------------------------------------------------------------

  private def save(items: Seq[Todo]): Unit =
    val json = items.map(t => js.Dynamic.literal(id = t.id, title = t.title, completed = t.completed))
    storage.save(js.JSON.stringify(js.Array(json*)))

  private def load(): Vector[Todo] =
    try
      storage.load().toVector.flatMap { json =>
        js.JSON.parse(json).asInstanceOf[js.Array[js.Dynamic]].toVector.map(d =>
          Todo(d.id.asInstanceOf[Int], d.title.asInstanceOf[String], d.completed.asInstanceOf[Boolean])
        )
      }
    catch case _: Throwable => Vector.empty   // corrupt or old data: start fresh

@main def start(): Unit =
  val app = TodoApp(dom.document.querySelector(".todoapp"), Storage.local("todos-qtags"))
  def route(): Unit = app.filter.set(Filter.fromHash(dom.window.location.hash))
  dom.window.addEventListener("hashchange", (_: dom.Event) => route())
  route()
