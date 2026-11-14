package todomvc

import org.scalajs.dom
import utest.*

/** Drives the app through DOM events, as a user would (runs under jsdom). */
object TodoAppTest extends TestSuite:

  class MemoryStorage extends Storage:
    var data: Option[String] = None
    def load() = data
    def save(json: String) = data = Some(json)

  class Fixture(storage: Storage = MemoryStorage()):
    val root = dom.document.createElement("section")
    dom.document.body.textContent = ""
    dom.document.body.appendChild(root)
    val app = TodoApp(root, storage)

    def q(sel: String): dom.HTMLElement = root.querySelector(sel).asInstanceOf[dom.HTMLElement]
    def all(sel: String): Seq[dom.HTMLElement] =
      val nodes = root.querySelectorAll(sel)
      (0 until nodes.length).map(i => nodes(i).asInstanceOf[dom.HTMLElement])
    def titles: Seq[String] = all(".todo-list li label").map(_.textContent)
    def count: String = q(".todo-count").textContent

    def keydown(el: dom.HTMLElement, k: String): Unit =
      el.dispatchEvent(dom.KeyboardEvent("keydown", new dom.KeyboardEventInit { key = k; bubbles = true }))
    def typeInto(sel: String, text: String): Unit =
      val input = q(sel).asInstanceOf[dom.HTMLInputElement]
      input.value = text
      keydown(input, "Enter")
    def add(titles: String*): Unit = titles.foreach(typeInto(".new-todo", _))
    def dblclick(el: dom.HTMLElement): Unit =
      el.dispatchEvent(dom.MouseEvent("dblclick", new dom.MouseEventInit { bubbles = true }))

  val tests = Tests:
    test("starts empty, without main section or footer"):
      val f = Fixture()
      assert(f.q(".main") == null, f.q(".footer") == null)

    test("add todos, trimmed; blank input is ignored"):
      val f = Fixture()
      f.q(".new-todo").focus()
      f.add("  buy milk ", "", "walk & <talk>")
      assert(f.titles == Seq("buy milk", "walk & <talk>"))
      assert(f.count == "2 items left")
      assert(f.q(".new-todo").asInstanceOf[dom.HTMLInputElement].value == "")
      assert(dom.document.activeElement == f.q(".new-todo"))   // never re-created, so focus stays

    test("updates are fine-grained: toggling keeps the same elements"):
      val f = Fixture()
      f.add("a", "b")
      val lis = f.all(".todo-list li")
      val newTodo = f.q(".new-todo")
      newTodo.focus()
      f.all(".toggle").head.click()
      assert(f.all(".todo-list li") == lis, f.q(".new-todo") eq newTodo)
      assert(dom.document.activeElement == newTodo)

    test("toggle, toggle all, clear completed"):
      val f = Fixture()
      f.add("a", "b")
      f.all(".toggle").head.click()
      assert(f.all(".todo-list li").map(_.className) == Seq("completed", ""))
      assert(f.count == "1 item left")
      assert(!f.q(".toggle-all").asInstanceOf[dom.HTMLInputElement].checked)
      f.q(".toggle-all").click()
      assert(f.count == "0 items left")
      assert(f.all(".toggle").forall(_.asInstanceOf[dom.HTMLInputElement].checked))
      f.q(".clear-completed").click()
      assert(f.titles.isEmpty, f.q(".footer") == null)

    test("filters"):
      val f = Fixture()
      f.add("a", "b")
      f.all(".toggle")(1).click()
      f.app.filter.set(Filter.Active)
      assert(f.titles == Seq("a"))
      f.app.filter.set(Filter.Completed)
      assert(f.titles == Seq("b"))
      assert(f.q(".filters .selected").textContent == "Completed")

    test("edit: Enter saves, Escape cancels, empty deletes"):
      val f = Fixture()
      f.add("a", "b")
      f.dblclick(f.all(".todo-list label").head)
      assert(f.all(".todo-list li").head.className == "editing")
      assert(dom.document.activeElement == f.q(".editing .edit"))
      f.typeInto(".editing .edit", " a2 ")
      assert(f.titles == Seq("a2", "b"), f.q(".editing") == null)

      f.dblclick(f.all(".todo-list label").head)
      f.q(".editing .edit").asInstanceOf[dom.HTMLInputElement].value = "ignored"
      f.keydown(f.q(".editing .edit"), "Escape")
      assert(f.titles == Seq("a2", "b"), f.q(".editing") == null)

      f.dblclick(f.all(".todo-list label").head)
      f.typeInto(".editing .edit", "   ")
      assert(f.titles == Seq("b"))

    test("destroy"):
      val f = Fixture()
      f.add("a", "b")
      f.all(".destroy").head.click()
      assert(f.titles == Seq("b"))

    test("todos persist across app instances"):
      val storage = MemoryStorage()
      val f1 = Fixture(storage)
      f1.add("a", "b")
      f1.all(".toggle").head.click()
      f1.app.dispose()
      val f2 = Fixture(storage)
      assert(f2.titles == Seq("a", "b"), f2.count == "1 item left")
