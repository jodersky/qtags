package qtags

import org.scalajs.dom
import utest.*

object DomTest extends TestSuite:
  def item(s: String): Frag = li { cls("item"); text(s) }

  def html(f: DomFrag): String =
    val host = dom.document.createElement("div")
    renderInto(host)(f)
    host.innerHTML

  val tests = Tests:
    test("same output as string backend"):
      val f: Frag = div { cls("a\"b"); ul { item("x < y") } }
      assert(html(f) == renderString(f))

    test("raw: same output as string backend"):
      val f: Frag = div { raw("<b>&amp;</b>x"); script { raw("if (a < b && c) {}") } }
      assert(html(f) == renderString(f))

    test("doctype adds nothing"):
      assert(html { doctype; div { text("x") } } == "<div>x</div>")

    test("event handlers"):
      var clicks = 0
      val host = dom.document.createElement("div")
      renderInto(host):
        div { onClick(_ => clicks += 1); text("click me") }
      host.firstChild.asInstanceOf[dom.HTMLElement].click()
      assert(clicks == 1)

    test("attribute after content throws"):
      assertThrows[IllegalStateException]:
        renderDom(div { text("x"); cls("late") })
