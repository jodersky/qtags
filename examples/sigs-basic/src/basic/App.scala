package basic

import org.scalajs.dom
import qtags as h
import qtags.sigs

/** The basics of qtags.sigs: state in signals, and a view that updates itself
  * when they change.
  *
  * The view is rendered once. Each `sigs.bind...` call keeps one piece of the
  * DOM equal to a signal, and `sigs.dyn` re-renders a region when its signal
  * changes. Nothing else is ever re-rendered.
  */
def app(): h.DomFrag =
  // State: values set from event handlers.
  val count = sigs.State(0)
  val name = sigs.State("")

  // Derived values. `map` derives from one signal; `Computed` can read
  // several, with `signal()`, and is recomputed when any of them changes.
  val clicks = count.map(n => if n == 1 then "1 click" else s"$n clicks")
  val greeting = sigs.Computed {
    val who = if name().trim.isEmpty then "stranger" else name().trim
    s"Hello, $who! You have ${clicks()}."
  }

  h.h1 { h.text("Signals") }

  h.section {
    h.h2 { h.text("Counter") }
    h.button {
      h.cls("decrement")
      h.text("−")
      sigs.bindProp("disabled", count.map(_ == 0))      // a DOM property
      h.on("click") { _ => count.update(_ - 1) }
    }
    h.span {
      h.cls("count")
      sigs.bindText(count.map(_.toString))              // a text node
    }
    h.button {
      h.cls("increment")
      h.text("+")
      h.on("click") { _ => count.update(_ + 1) }
    }
  }

  h.section {
    h.h2 { h.text("Name") }
    h.input {
      h.cls("name"); h.tpe("text"); h.placeholder("Your name")
      h.on("input") { e => name.set(e.target.asInstanceOf[dom.HTMLInputElement].value) }
    }
    h.p {
      h.cls("greeting")
      sigs.bindText(greeting)
    }
  }

  // A region that is rendered again whenever `count == 0` changes: the reset
  // button only exists while there is something to reset.
  sigs.dyn(count.map(_ == 0)) { zero =>
    if !zero then
      h.button {
        h.cls("reset")
        h.text("Reset")
        h.on("click") { _ => count.set(0) }
      }
  }

@main def start(): Unit =
  sigs.mount(dom.document.body)(app())
