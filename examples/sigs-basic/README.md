# sigs-basic

The basics of `qtags.sigs`, the reactive signals for qtags in the browser: a
counter, a text field and a button that only exists when it is needed. Start
here, then see [sigs-multi](../sigs-multi) and
[sigs-todomvc](../sigs-todomvc).

```scala
import org.scalajs.dom
import qtags as h
import qtags.sigs

val count = sigs.State(0)

sigs.mount(dom.document.body) {
  h.button {
    h.text("+")
    h.on("click") { _ => count.update(_ + 1) }
  }
  h.span { sigs.bindText(count.map(_.toString)) }
}
```

The view is rendered once. When `count` changes, only the text node created
by `sigs.bindText` is updated; nothing else is re-rendered.

## Run

```sh
./mill show examples.sigs-basic.site
```

This prints a directory containing `index.html` and the compiled `main.js`.
Open `index.html` in a browser.

## What it shows

| Part of the page | Shows |
|---|---|
| Counter | `sigs.State` set from an event handler with `h.on`; `map` to derive a signal; `sigs.bindText` for a text node and `sigs.bindProp` for the `disabled` property of the − button |
| Name | `sigs.Computed`, which reads two signals with `signal()` and is recomputed when either changes |
| Reset button | `sigs.dyn`, which re-renders a region when a signal changes: the button is only in the page while the count isn't 0 |

## Test

The tests click and type into the app under
[jsdom](https://github.com/jsdom/jsdom). Install jsdom once, then run them:

```sh
(cd qtags/test/js && npm ci)
./mill examples.sigs-basic.test
```

## Files

- [App.scala](src/basic/App.scala): the app.
- [index.html](index.html): the page that loads it.
- [AppTest.scala](test/src/AppTest.scala): the tests.
