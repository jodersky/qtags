package basic

import org.scalajs.dom
import qtags.sigs
import utest.*

/** Clicks and types into the app, as a user would (runs under jsdom). */
object AppTest extends TestSuite:

  class Fixture:
    val root = dom.document.createElement("div")
    dom.document.body.textContent = ""
    dom.document.body.appendChild(root)
    val app = sigs.mount(root)(basic.app())

    def q(sel: String): dom.HTMLElement = root.querySelector(sel).asInstanceOf[dom.HTMLElement]
    def count: String = q(".count").textContent
    def greeting: String = q(".greeting").textContent
    def typeName(s: String): Unit =
      val input = q(".name").asInstanceOf[dom.HTMLInputElement]
      input.value = s
      input.dispatchEvent(dom.Event("input"))

  val tests = Tests:
    test("counter"):
      val f = Fixture()
      assert(f.count == "0")
      assert(f.q(".decrement").asInstanceOf[dom.HTMLButtonElement].disabled)
      f.q(".increment").click()
      f.q(".increment").click()
      assert(f.count == "2")
      assert(!f.q(".decrement").asInstanceOf[dom.HTMLButtonElement].disabled)
      f.q(".decrement").click()
      assert(f.count == "1")

    test("greeting reads the name and the count"):
      val f = Fixture()
      assert(f.greeting == "Hello, stranger! You have 0 clicks.")
      f.typeName("Ada")
      f.q(".increment").click()
      assert(f.greeting == "Hello, Ada! You have 1 click.")

    test("reset button only exists while the count isn't 0"):
      val f = Fixture()
      assert(f.q(".reset") == null)
      f.q(".increment").click()
      f.q(".reset").click()
      assert(f.count == "0", f.q(".reset") == null)

    test("updates are fine-grained: the input is never re-created"):
      val f = Fixture()
      val input = f.q(".name")
      f.typeName("Ada")
      f.q(".increment").click()
      assert(f.q(".name") eq input)

    test("disposing removes the view"):
      val f = Fixture()
      f.app.dispose()
      assert(f.root.textContent == "")
