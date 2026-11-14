package backends

import qtags as h

/** One fragment, rendered by qtags' own string backend and by the two custom
  * backends in this example. Run with `./mill examples.custom-backend.run`.
  */
def page: h.Frag =
  h.nav {
    h.ul {
      h.li { h.a { h.href("/"); h.text("Home") } }
      h.li { h.a { h.href("/about"); h.text("About & contact") } }
    }
  }
  h.p { h.text("Hello"); h.br(); h.strong { h.text("world") } }

@main def run(): Unit =
  println("== renderString")
  println(h.renderString(page))

  println()
  println("== renderPretty (PrettyHtml)")
  println(renderPretty(page))

  println()
  println("== renderTree (TreeHtml)")
  val nodes = renderTree(page)
  val links = nodes.flatMap(_.all).collect { case e @ Node.Element("a", _, _) => e }
  for link <- links do println(s"link to ${link.attr("href").get}: ${link.textContent}")
