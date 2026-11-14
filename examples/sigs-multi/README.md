# sigs-multi

Components added and removed at run time, each with its own state, and a value
derived from all of them. The page starts with one counter. Counters can be
added and removed, and each one can be incremented, decremented, set to zero
or set to a number typed by the user. The sum of all counters is shown above
them. It builds on [sigs-basic](../sigs-basic).

## Run

```sh
./mill show examples.sigs-multi.site
```

This prints a directory containing `index.html` and the compiled `main.js`.
Open `index.html` in a browser.

## How it works

Each counter is a component that owns its state, a `sigs.State(0)`, and
renders its own view:

```scala
import qtags as h
import qtags.sigs

final class Counter(val number: Int):
  val value = sigs.State(0)

  def view(onRemove: () => Unit): h.DomFrag =
    h.li {
      h.button { h.text("+"); h.on("click") { _ => value.update(_ + 1) } }
      // ...
    }
```

The page keeps the list of counters in a signal, and derives the sum from it:

```scala
val counters = sigs.State(Vector(Counter(1)))
val sum = sigs.Computed { counters().map(_.value()).sum }
```

- **The sum** reads the list and the value of every counter in it. Each time
  it is computed it reads only the counters that are currently in the list,
  so a new counter starts to count as soon as it is added, and a removed
  counter stops affecting the sum.
- **The list** is rendered with `sigs.each(counters, c => c)`, keyed by the
  counter itself. When a counter is added or removed, only its `<li>` is
  created or removed. The others keep their DOM, including the focus and
  cursor of their input.
- **The input** shows the value with `sigs.bindProp("value", ...)`. When the
  user commits a new number (Enter, or leaving the field), it is set as the
  counter's value. Anything that isn't a number is undone.
- **Removed counters** need no clean-up. `sigs.each` disposes the bindings of
  the removed `<li>`, and once nothing refers to the counter any more, its
  state is garbage collected.

## Test

The tests click and type into the app under
[jsdom](https://github.com/jsdom/jsdom). Install jsdom once, then run them:

```sh
(cd qtags/test/js && npm ci)
./mill examples.sigs-multi.test
```

## Files

- [App.scala](src/multi/App.scala): the counter component and the page.
- [index.html](index.html): the page that loads the app.
- [AppTest.scala](test/src/AppTest.scala): the tests.
