# indentation

Writing qtags pages with Scala 3's indentation-based syntax.

A tag's content is an argument, so instead of braces you can write the tag
followed by `:` and put its content in an indented block. Short content fits
in parentheses:

```scala
import qtags as h

def page(items: Seq[String]): h.Frag =
  h.div:
    h.cls("shop")
    h.h1(h.text("Shop"))
    h.ul:
      for item <- items do
        h.li(h.text(item))
```

This is the same as:

```scala
import qtags as h

def page(items: Seq[String]): h.Frag =
  h.div {
    h.cls("shop")
    h.h1 { h.text("Shop") }
    h.ul {
      for item <- items do
        h.li { h.text(item) }
    }
  }
```

Your own components work the same way: a component that takes its content as
a last parameter list, `def card(title: String)(content: h.Frag)`, is called
with `card("Fruit"):` and an indented block.

## Run

```sh
./mill examples.indentation.run
```

[Main.scala](src/indentation/Main.scala) writes a page twice, with braces and
with indentation, including a component, `if`, `for` and `match`. It prints
the page and checks that both versions render the same HTML.
