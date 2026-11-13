package sandbox

import qtags.*

// A component is just a function that takes a Frag and requires an Html context.
def myList(body: Frag)(using Html): Unit =
  ul {
    cls("my-list")
    body
  }

// Components can take ordinary data too.
def myItems(items: Seq[String]): Frag =
  myList {
    for i <- items do li { text(i) }
  }

def main(args: Array[String]): Unit =

  // myItems(Nil)
  val rendered = renderString {
    div {
      myList {
        li { text("hello") }
        li { text("world") }
      }
      myItems(Seq("a", "b & c"))
    }
  }
  println(rendered)
