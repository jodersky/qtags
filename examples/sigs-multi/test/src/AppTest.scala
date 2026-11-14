package multi

import org.scalajs.dom
import qtags.sigs
import utest.*

/** Clicks and types into the app, as a user would (runs under jsdom). */
object AppTest extends TestSuite:

  /** One counter's `<li>`. */
  class CounterEl(el: dom.Element):
    def click(button: String): Unit = el.querySelector("." + button).asInstanceOf[dom.HTMLElement].click()
    def input = el.querySelector(".value").asInstanceOf[dom.HTMLInputElement]
    def value: String = input.value
    def enter(s: String): Unit =
      input.value = s
      input.dispatchEvent(dom.Event("change"))

  class Fixture:
    val root = dom.document.createElement("div")
    dom.document.body.textContent = ""
    dom.document.body.appendChild(root)
    val app = App()
    val mounted = sigs.mount(root)(app.view)

    def q(sel: String): dom.HTMLElement = root.querySelector(sel).asInstanceOf[dom.HTMLElement]
    def counters: Seq[dom.HTMLElement] =
      val nodes = root.querySelectorAll(".counter")
      (0 until nodes.length).map(i => nodes(i).asInstanceOf[dom.HTMLElement])
    def counter(i: Int) = CounterEl(counters(i))
    def sum: String = q(".sum strong").textContent
    def labels: Seq[String] = counters.map(_.querySelector(".label").textContent)

  val tests = Tests:
    test("starts with one counter at 0"):
      val f = Fixture()
      assert(f.labels == Seq("Counter 1"), f.counter(0).value == "0", f.sum == "0")

    test("increment, decrement, zero"):
      val f = Fixture()
      val c = f.counter(0)
      c.click("increment"); c.click("increment"); c.click("increment")
      c.click("decrement")
      assert(c.value == "2", f.sum == "2")
      c.click("zero")
      assert(c.value == "0", f.sum == "0")

    test("set by the user; anything else is undone"):
      val f = Fixture()
      val c = f.counter(0)
      c.enter("42")
      assert(c.value == "42", f.sum == "42")
      c.enter("")
      assert(c.value == "42", f.sum == "42")

    test("the sum follows added and removed counters"):
      val f = Fixture()
      f.q(".add").click()
      f.q(".add").click()
      assert(f.labels == Seq("Counter 1", "Counter 2", "Counter 3"))
      f.counter(0).enter("1")
      f.counter(1).enter("10")
      f.counter(2).enter("100")
      assert(f.sum == "111")
      f.counter(1).click("remove")
      assert(f.labels == Seq("Counter 1", "Counter 3"), f.sum == "101")
      f.counter(0).click("increment")
      assert(f.sum == "102")

    test("counters keep their DOM and state while others come and go"):
      val f = Fixture()
      f.q(".add").click()
      val second = f.counters(1)
      f.counter(1).enter("5")
      f.counter(0).click("remove")
      f.q(".add").click()
      assert(f.counters.head eq second, f.counter(0).value == "5", f.sum == "5")
      assert(f.labels == Seq("Counter 2", "Counter 3"))   // numbers aren't reused

    test("removing every counter"):
      val f = Fixture()
      assert(f.q(".empty") == null)
      f.counter(0).enter("7")
      f.counter(0).click("remove")
      assert(f.counters.isEmpty, f.sum == "0", f.q(".empty") != null)
      f.q(".add").click()
      assert(f.q(".empty") == null, f.counter(0).value == "0")
