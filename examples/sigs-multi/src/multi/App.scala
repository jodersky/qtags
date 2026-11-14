package multi

import org.scalajs.dom
import qtags as h
import qtags.sigs

/** Components added and removed at run time, each with its own state, and a
  * value derived from all of them.
  */

/** One counter: a component that owns its state. */
final class Counter(val number: Int):
  val value = sigs.State(0)

  def view(onRemove: () => Unit): h.DomFrag =
    h.li {
      h.cls("counter")
      h.span { h.cls("label"); h.text(s"Counter $number") }
      h.button { h.cls("decrement"); h.text("−"); h.on("click") { _ => value.update(_ - 1) } }
      h.input {
        h.cls("value"); h.tpe("number")
        sigs.bindProp("value", value.map(_.toString))
        // Set by the user: applied when the field is committed (Enter or
        // leaving it). Anything that isn't a number is undone.
        h.on("change") { e =>
          val field = e.target.asInstanceOf[dom.HTMLInputElement]
          field.value.trim.toIntOption.foreach(value.set)
          field.value = value.now.toString
        }
      }
      h.button { h.cls("increment"); h.text("+"); h.on("click") { _ => value.update(_ + 1) } }
      h.button { h.cls("zero"); h.text("Zero"); h.on("click") { _ => value.set(0) } }
      h.button { h.cls("remove"); h.text("Remove"); h.on("click") { _ => onRemove() } }
    }

/** The page: a list of counters, starting with one, and their sum. */
final class App:
  private var created = 0
  private def newCounter(): Counter =
    created += 1
    Counter(created)

  val counters = sigs.State(Vector(newCounter()))

  /** Reads the list and the value of every counter in it, so it changes when a
    * counter is added, removed, or changed. Its dependencies change with the
    * list: a removed counter is no longer read, so it no longer affects the sum.
    */
  val sum = sigs.Computed { counters().map(_.value()).sum }

  def add(): Unit = counters.update(_ :+ newCounter())
  def remove(c: Counter): Unit = counters.update(_.filterNot(_ eq c))

  def view: h.DomFrag =
    h.h1 { h.text("Counters") }
    h.p {
      h.cls("sum")
      h.text("Sum: ")
      h.strong { sigs.bindText(sum.map(_.toString)) }
    }
    h.ul {
      h.cls("counters")
      // Keyed by the counter itself: a counter keeps its DOM, and so its
      // input's focus, while others are added or removed around it.
      sigs.each(counters, c => c) { (c, _) => c.view(() => remove(c)) }
    }
    sigs.dyn(counters.map(_.isEmpty)) { empty =>
      if empty then h.p { h.cls("empty"); h.text("No counters.") }
    }
    h.button {
      h.cls("add")
      h.text("Add counter")
      h.on("click") { _ => add() }
    }

@main def start(): Unit =
  sigs.mount(dom.document.body)(App().view)
