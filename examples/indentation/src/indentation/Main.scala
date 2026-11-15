package indentation

import qtags as h

/** The same page written twice: with braces, and with Scala 3's
  * indentation-based syntax, where `tag:` followed by an indented block
  * replaces `tag { ... }`. Run with `./mill examples.indentation.run`.
  */

final case class Item(name: String, price: Int, stock: Int)

val items = Seq(Item("Apple", 2, 12), Item("Pear & quince", 3, 0), Item("Fig", 4, 2))

// ---- braces -------------------------------------------------------------------

def cardWithBraces(title: String)(content: h.Frag): h.Frag =
  h.section {
    h.cls("card")
    h.h2 { h.text(title) }
    content
  }

def withBraces: h.Frag =
  h.div {
    h.cls("shop")
    h.h1 { h.text("Shop") }
    cardWithBraces("Fruit") {
      h.ul {
        for item <- items do
          h.li {
            if item.stock == 0 then h.cls("sold-out")
            h.strong { h.text(item.name) }
            h.text(s": ${item.price} CHF, ")
            item.stock match
              case 0 => h.em { h.text("sold out") }
              case n if n < 5 => h.text(s"only $n left")
              case _ => h.text("in stock")
          }
      }
    }
  }

// ---- indentation ----------------------------------------------------------------

// A component taking a block works the same way: `card("Fruit"):` and an
// indented block.
def card(title: String)(content: h.Frag): h.Frag =
  h.section:
    h.cls("card")
    h.h2(h.text(title))   // short content fits in parentheses on one line
    content

def withIndentation: h.Frag =
  h.div:
    h.cls("shop")
    h.h1(h.text("Shop"))
    card("Fruit"):
      h.ul:
        for item <- items do
          h.li:
            if item.stock == 0 then h.cls("sold-out")
            h.strong(h.text(item.name))
            h.text(s": ${item.price} CHF, ")
            item.stock match
              case 0 => h.em(h.text("sold out"))
              case n if n < 5 => h.text(s"only $n left")
              case _ => h.text("in stock")

@main def run(): Unit =
  val indented = h.renderString(withIndentation)
  println(indented)
  require(indented == h.renderString(withBraces), "both versions should render the same HTML")
  println("Both versions render the same HTML.")
