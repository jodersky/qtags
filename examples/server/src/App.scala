package server

import components.ui
import qtags.*

/** A to-do app: keg serves pages built with qtags and the `ui` components.
  *
  *   ./mill examples.server.run      then open http://localhost:8080
  *
  * Endpoints return `render(...)`, a geny.Writable with the HTML content type
  * and length set, which keg sends as the response body.
  */

final case class Todo(id: Int, title: String, done: Boolean)

object Store:
  private var todos = Vector(Todo(1, "Try qtags", true), Todo(2, "Write a component", false))
  private var nextId = 3
  def all: Vector[Todo] = synchronized(todos)
  def add(title: String): Unit = synchronized:
    todos :+= Todo(nextId, title, false)
    nextId += 1
  def toggle(id: Int): Unit = synchronized:
    todos = todos.map(t => if t.id == id then t.copy(done = !t.done) else t)

val links = Seq("Todos" -> "/", "Big page" -> "/big")

@keg.get("/")
def index() =
  render(Pages.todos(Store.all))

@keg.post("/todos")
def add(data: keg.FormData) =
  data.fields.get("title").map(_.trim).filter(_.nonEmpty).foreach(Store.add)
  redirect("/")

@keg.post("/todos/:todoId/toggle")
def toggle(todoId: Int) =
  Store.toggle(todoId)
  redirect("/")

/** A large page, streamed while it is rendered instead of built in memory
  * first. keg sends it without a Content-Length.
  */
@keg.get("/big")
def big() =
  renderLazy(Pages.big(10000))

// After a form POST, send the browser back to a page (Post/Redirect/Get).
def redirect(to: String) =
  keg.Response("", status = 303, headers = Seq("Location" -> to))

val app = keg.Server(keg.routes())

@main def run(): Unit =
  app.start()

object Pages:

  def todos(items: Seq[Todo]): Frag =
    ui.page("Todos", links) {
      val open = items.count(!_.done)
      ui.alert(if open == 0 then ui.Tone.Success else ui.Tone.Info) {
        text(if open == 0 then "All done!" else s"$open left to do.")
      }

      ui.card("Todo list") {
        ui.table(items)(
          ui.Column("Task", t =>
            span {
              if t.done then cls("done")
              text(t.title)
            }
          ),
          ui.Column("", t =>
            form {
              action(s"/todos/${t.id}/toggle"); method("post")
              val (label, variant) =
                if t.done then ("Reopen", ui.Variant.Secondary) else ("Done", ui.Variant.Primary)
              ui.button(label, variant) { tpe("submit") }
            }
          )
        )
      }

      ui.card("Add a todo") {
        form {
          action("/todos"); method("post")
          ui.textField("title", "Title", placeholderText = "What needs doing?")
          ui.button("Add") { tpe("submit") }
        }
      }
    }

  def big(n: Int): Frag =
    ui.page("Big page", links) {
      p { text(s"$n rows, streamed to the browser as they are rendered.") }
      ui.table(1 to n)(
        ui.Column("#", i => text(i.toString)),
        ui.Column("Square", i => text((i.toLong * i).toString))
      )
    }
