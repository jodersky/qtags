package qtags.sigs

import org.scalajs.dom
import qtags.*
import utest.*

object UiTest extends TestSuite:

  def newRoot(): dom.Element =
    dom.document.body.textContent = ""
    val root = dom.document.createElement("div")
    dom.document.body.appendChild(root)
    root

  /** innerHTML without the empty comment markers that delimit regions. */
  def html(el: dom.Element): String = el.innerHTML.replace("<!---->", "")

  val tests = Tests:

    test("static view"):
      val root = newRoot()
      mount(root) { p { cls("x"); text("hi") } }
      assert(html(root) == """<p class="x">hi</p>""")

    test("bindText updates the same text node in place"):
      val root = newRoot()
      val n = State(1)
      mount(root) { p { bindText(n.map(x => s"n = $x")) } }
      val node = root.querySelector("p").firstChild
      n.set(2)
      assert(html(root) == "<p>n = 2</p>", root.querySelector("p").firstChild eq node)

    test("bindAttr and bindProp"):
      val root = newRoot()
      val done = State(false)
      mount(root) {
        input {
          tpe("checkbox")
          bindAttr("class", done.map(d => if d then "done" else "todo"))
          bindProp("checked", done)
        }
      }
      val box = root.querySelector("input").asInstanceOf[dom.HTMLInputElement]
      assert(box.className == "todo", !box.checked)
      done.set(true)
      assert(box.className == "done", box.checked)

    test("dyn re-renders its region only when its signal changes"):
      val root = newRoot()
      val show = State(true)
      val other = State(0)
      var renders = 0
      mount(root) {
        div {
          text("before ")
          dyn(show) { s =>
            renders += 1
            if s then strong { text("shown") } else em { text("hidden") }
          }
          text(" after")
        }
      }
      assert(html(root) == "<div>before <strong>shown</strong> after</div>", renders == 1)
      other.set(1)
      assert(renders == 1)
      show.set(false)
      assert(html(root) == "<div>before <em>hidden</em> after</div>", renders == 2)

    test("dyn disposes the bindings of what it replaced"):
      val root = newRoot()
      val show = State(true)
      val n = State(0)
      var runs = 0
      mount(root) {
        dyn(show) { s =>
          if s then p { bindText(Computed { runs += 1; n().toString }) }
        }
      }
      n.set(1)
      assert(runs == 2, html(root) == "<p>1</p>")
      show.set(false)
      n.set(2)                                 // the old binding is gone
      assert(runs == 2, html(root) == "")

    test("each: keyed list keeps, moves, adds and removes items"):
      val root = newRoot()
      val items = State(Seq("a" -> 1, "b" -> 2, "c" -> 3))
      var rendered = Seq.empty[String]
      mount(root) {
        ul {
          each(items, _._1) { (k, item) =>
            rendered :+= k
            li { bindText(item.map(i => s"$k${i._2}")) }
          }
        }
      }
      def lis = (0 until root.querySelectorAll("li").length).map(root.querySelectorAll("li")(_))
      val before = lis
      assert(html(root) == "<ul><li>a1</li><li>b2</li><li>c3</li></ul>")

      items.set(Seq("c" -> 3, "a" -> 10, "d" -> 4))   // reorder, update a, drop b, add d
      assert(html(root) == "<ul><li>c3</li><li>a10</li><li>d4</li></ul>")
      assert(rendered == Seq("a", "b", "c", "d"))     // only d was newly rendered
      assert(lis(0) eq before(2), lis(1) eq before(0)) // c and a kept their elements

    test("each: removed items stop updating"):
      val root = newRoot()
      val items = State(Seq(1, 2))
      val n = State(0)
      var runs = 0
      mount(root) {
        each(items, identity) { (k, _) => span { bindText(Computed { runs += 1; s"$k:${n()}" }) } }
      }
      assert(runs == 2)
      items.set(Seq(1))
      n.set(1)
      assert(runs == 3, html(root) == "<span>1:1</span>")

    test("each: duplicate keys are rejected"):
      val root = newRoot()
      assertThrows[IllegalArgumentException]:
        mount(root) { each(State(Seq(1, 1)), identity) { (_, _) => text("x") } }

    test("mount.dispose removes the DOM and stops all bindings"):
      val root = newRoot()
      val n = State(0)
      var runs = 0
      val m = mount(root) {
        p { text("x") }
        dyn(n) { _ => bindText(Computed { runs += 1; n().toString }) }
      }
      m.dispose()
      n.set(1)
      assert(root.innerHTML == "", runs == 1)

    test("handlers update state, and the view follows"):
      val root = newRoot()
      val count = State(0)
      mount(root) {
        button { bindText(count.map(c => s"clicked $c")); onClick(_ => count.update(_ + 1)) }
      }
      val b = root.querySelector("button").asInstanceOf[dom.HTMLElement]
      b.click(); b.click()
      assert(b.textContent == "clicked 2")

    test("signals built with map and Computed"):
      val root = newRoot()
      val n = State(1)
      val items = State(Seq(1, 2))
      val label = n.map(x => s"n = $x")
      val size = n.map(x => if x > 1 then "many" else "one")
      val tens = items.map(_.map(_ * 10))
      mount(root) {
        p { bindAttr("class", size); bindText(label) }
        each(tens, identity) { (k, _) => span { text(k.toString) } }
      }
      n.set(2)
      items.set(Seq(3))
      assert(html(root) == """<p class="many">n = 2</p><span>30</span>""")

    test("bindings outside mount throw"):
      val n = State("x")
      val root = newRoot()
      assertThrows[IllegalStateException]:
        qtags.renderInto(root) { p { bindText(n) } }

    test("reading a signal outside a tracked block does not compile"):
      val n = State(0)
      import scala.compiletime.testing.typeChecks
      assert(typeChecks("mount(newRoot()) { p { bindText(Computed { n().toString }) } }"))   // control
      assert(!typeChecks("mount(newRoot()) { p { text(n().toString) } }"))
      assert(!typeChecks("mount(newRoot()) { p { bindText(Computed { n().toString }); text(n().toString) } }"))
