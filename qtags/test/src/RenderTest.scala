package qtags

import utest.*

object RenderTest extends TestSuite:
  def myList(body: Frag): Frag = ul { cls("my-list"); body }

  val tests = Tests:
    test("nesting and attributes"):
      val s = renderString(div { cls("a"); ul { li { text("x") } } })
      assert(s == """<div class="a"><ul><li>x</li></ul></div>""")

    test("escaping"):
      val s = renderString(div { cls("a\"b&c"); text("x < y & z") })
      assert(s == """<div class="a&quot;b&amp;c">x &lt; y &amp; z</div>""")

    test("components"):
      val s = renderString(myList { li { text("a") }; li { text("b") } })
      assert(s == """<ul class="my-list"><li>a</li><li>b</li></ul>""")

    test("attribute after content throws"):
      assertThrows[IllegalStateException]:
        renderString(div { text("x"); cls("late") })

    test("void elements"):
      val s = renderString(p { text("a"); br(); img { attr("src", "x.png") } })
      assert(s == """<p>a<br><img src="x.png"></p>""")

    test("void element with content throws"):
      assertThrows[IllegalStateException]:
        renderString(br { text("x") })

    test("keyword tags"):
      assert(renderString(`var` { text("x") }) == "<var>x</var>")

    test("attributes"):
      val s = renderString(input { tpe("checkbox"); `for`("x"); disabled; data("k", "v"); aria("label", "l") })
      assert(s == """<input type="checkbox" for="x" disabled="" data-k="v" aria-label="l">""")

    test("exports resolve clashes"):
      val s = renderString(form { title("t"); tags.title { text("x") }; label { attrs.form("f") } })
      assert(s == """<form title="t"><title>x</title><label form="f"></label></form>""")

    test("raw"):
      val s = renderString(div { raw("<b>&amp;</b>"); script { raw("if (a < b && c) {}") } })
      assert(s == """<div><b>&amp;</b><script>if (a < b && c) {}</script></div>""")

    test("doctype"):
      val s = renderString { doctype; html { body { text("x") } } }
      assert(s == "<!DOCTYPE html><html><body>x</body></html>")

    def bytes(w: geny.Writable): Array[Byte] =
      val out = java.io.ByteArrayOutputStream()
      w.writeBytesTo(out)
      out.toByteArray

    test("render: content type and length"):
      val f: Frag = p { text("héllo") }      // é is 2 bytes in UTF-8
      val w = render(f)
      val b = bytes(w)
      assert(w.httpContentType == Some("text/html; charset=utf-8"))
      assert(w.contentLength == Some(b.length.toLong))
      assert(String(b, "UTF-8") == "<p>héllo</p>")
      assert(b.length == "<p>héllo</p>".length + 1)

    test("render: length matches the bytes written, for any text"):
      def check(f: Frag) =
        val w = render(f)
        val b = bytes(w)
        assert(w.contentLength == Some(b.length.toLong))
        b
      // 1, 2, 3 and 4 byte UTF-8 sequences
      assert(String(check(text("a é € 🙂")), "UTF-8") == "a é € 🙂")
      // A surrogate pair split across the 8192-char chunks it is written in
      val long = "x" * 8191 + "🙂" + "y" * 10
      assert(String(check(text(long)), "UTF-8") == long)
      // A lone surrogate is written as "?"
      assert(String(check(text("a\uD83Db")), "UTF-8") == "a?b")

    test("UTF-8 output matches the JDK's, also across chunk boundaries"):
      val s = (0 until 300).map(i => Seq("a", "é", "€", "🙂", "\uD83D", "\uDE42", "&")(i * 7 % 13 % 7)).mkString
      val expected = s.replace("&", "&amp;").getBytes("UTF-8").toSeq
      assert(bytes(render(text(s))).toSeq == expected)
      for chunk <- Seq(1, 4, 5, 7) do
        assert(bytes(renderLazy(text(s), chunkSize = chunk)).toSeq == expected)

    test("all backends write the same page and check the same rules"):
      val page: Frag =
        doctype
        html {
          body {
            div { cls("a\"b&c"); id("x"); text("x < y & z é") }
            p { text("a"); br(); img { attr("src", "x.png") } }
            script { raw("if (a < b && c) {}") }
          }
        }
      val s = renderString(page)
      assert(String(bytes(render(page)), "UTF-8") == s)
      assert(String(bytes(renderLazy(page, chunkSize = 3)), "UTF-8") == s)
      for r <- Seq[Frag => Any](renderString, render, f => bytes(renderLazy(f))) do
        assertThrows[IllegalStateException](r(div { text("x"); cls("late") }))
        assertThrows[IllegalStateException](r(br { text("x") }))

    test("render: can be written more than once"):
      val w = render(p { text("héllo") })
      assert(bytes(w).toSeq == bytes(w).toSeq)

    test("renderLazy: streams the same output"):
      // Big enough to flush many times; the emoji (a surrogate pair) makes sure
      // chunk boundaries don't break multi-char code points.
      val f: Frag = ul { for i <- 1 to 2000 do li { cls("c"); text(s"item $i é 🙂") } }
      val w = renderLazy(f, chunkSize = 100)
      assert(w.httpContentType == Some("text/html; charset=utf-8"))
      assert(w.contentLength == None)
      assert(String(bytes(w), "UTF-8") == renderString(f))

    test("renderLazy: renders again on each write"):
      var n = 0
      val w = renderLazy { n += 1; text(n.toString) }
      assert(n == 0)
      assert(String(bytes(w), "UTF-8") == "1")
      assert(String(bytes(w), "UTF-8") == "2")
