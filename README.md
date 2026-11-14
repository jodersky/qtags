# qtags

HTML generation for Scala 3, written as plain code and built on context
functions.

```scala
import qtags as h

h.renderString {
  h.div {
    h.cls("greeting")
    h.h1 { h.text("Hello") }
    h.ul { for name <- Seq("Ann", "Bob & co") do h.li { h.text(name) } }
  }
}
// <div class="greeting"><h1>Hello</h1><ul><li>Ann</li><li>Bob &amp; co</li></ul></div>
```

qtags is inspired by [scalatags](https://github.com/com-lihaoyi/scalatags), but
it builds no tree of objects to generate HTML. Instead, tags are simple
functions which directly generate HTML on a hidden builder object, passed around
as `given` parameters through context functions. The lack of allocation of
intermediate objects keeps qtags extremely fast and memory efficient, around
4-5x faster and 4x less memory usage than scalatags, which itself is already
very efficient!

This repository has two modules:

- `qtags`: the HTML DSL, rendering to strings, streams or the DOM (available for
  the JVM, Scala.js, ans Scala Native)

- `qtags.sigs`: an experimental helper module for building reactive UIs
  (available for Scala.js only)

## qtags

### Tags, attributes and text

`import qtags.*` brings in every HTML element and attribute. Attributes are
statements at the start of a tag's block, before any content:

```scala
import qtags.*

a { href("/docs"); cls("nav-link"); text("Docs") }
input { tpe("checkbox"); id("agree"); checked }    // boolean attributes take no argument
img { src("logo.png"); alt("Logo") }               // void elements: attributes only
br()                                               // ...and `()` when there are none
```

alternatively, using indentation-based syntax:

```scala
import qtags.*

a:
  href("/docs")
  cls("nav-link")
  text("Docs")
input:
  tpe("checkbox")
  id("agree")
  checked
img:
  src("logo.png")
  alt("Logo")
br()
```

- Text is escaped. `raw(html)` inserts trusted markup as-is, which is also how
  to write `<script>` and `<style>` content.
- `doctype` writes `<!DOCTYPE html>`, for full pages.
- Attribute names follow HTML, in camelCase (`httpEquiv`, `acceptCharset`).
  Scala keywords need backticks (`` `for` ``, `` `type` ``, `` `class` ``), and
  `class` and `type` also have the aliases `cls` and `tpe`.
- `data(name, value)` and `aria(name, value)` write `data-*` and `aria-*`
  attributes; `attr(name, value)` writes any other.
- A few names are both a tag and an attribute. `import qtags.*` exports the
  more common one; the other is in `tags` or `attrs`:

  | Exported as a tag | Exported as an attribute |
  |---|---|
  | `abbr cite form label slot span` | `data style title` |

  ```scala
  form { title("Sign up"); label { attrs.form("signup"); text("Name") } }
  head { tags.title { text("Page title") } }
  ```

Mistakes the types don't catch are caught when rendering: an attribute after
content, or content inside a void element, throws an `IllegalStateException`.

### Components

A component is a function returning a `Frag` (a fragment of HTML,
`Html ?=> Unit`). Blocks of children are just `Frag` parameters:

```scala
def card(heading: String)(content: Frag): Frag =
  section {
    cls("card")
    h2 { text(heading) }
    content
  }

card("Welcome") {
  p { text("Components are plain functions.") }
}
```

[`examples/components`](examples/components/src/components/ui.scala) is a small
component library showing the usual patterns: a page layout, components taking
several blocks of children, variants, extra attributes passed in by the caller,
and a generic table.

### Rendering

| Function | Returns |
|---|---|
| `renderString(frag)` | a `String` |
| `render(frag)` | a [`geny.Writable`](https://github.com/com-lihaoyi/geny) with the HTML content type and length set; ready to return from a web server endpoint |
| `renderLazy(frag)` | a `geny.Writable` that streams the page while rendering it, without building it in memory first |
| `renderInto(element)(frag)`, `renderDom(frag)` | real DOM nodes (Scala.js only) |

With the DOM backend, fragments can also attach event handlers, which only
compiles where a DOM is available:

```scala
renderInto(dom.document.body) {
  button { text("Click"); onClick(_ => println("clicked")) }
}
```

## Reactive UI: qtags.sigs

`qtags.sigs` renders qtags views into the browser's DOM, and keeps them up to
date as the application's state changes. It is two things in one package:
signals, which model the state ([Signal.scala](qtags/sigs/src/Signal.scala), not
tied to qtags), and bindings between signals and the DOM
([ui.scala](qtags/sigs/src/ui.scala)).

```scala
import org.scalajs.dom
import qtags.*
import qtags.sigs.*

val count = State(0)

mount(dom.document.body) {
  p { bindText(count.map(n => s"Clicked $n times")) }
  button { text("+1"); onClick(_ => count.update(_ + 1)) }
}
```

### Signals

Signals are values that change over time, in the style of the
[JavaScript signals proposal](https://github.com/tc39/proposal-signals). There
are three primitives: `State` holds a value, `Computed` derives one from other
signals, and `observe` runs a side effect whenever the signals it uses change.

```scala
val first = State("Ada")
val last  = State("Lovelace")
val full  = Computed { s"${first()} ${last()}" }   // s() reads and tracks s

val o = observe { println(full()) }   // prints "Ada Lovelace" now...
last.set("Byron")                     // ...and "Ada Byron" after each change
o.dispose()
```

- Reading with `s()` only compiles inside a `Computed` or `observe` block, and
  records a dependency; use `s.now` anywhere else.
- Computeds are lazy, cached, and stop propagating when they recompute to an
  equal value. Changes are glitch-free: nothing ever sees a mix of old and new
  values.
- `observe` runs immediately, then again after each change to what it read.
  Observers created inside another observer's run belong to that run, and are
  disposed before it runs again; `Signal.scope { ... }` groups observers whose
  lifetime spans several runs. Only observed signals are linked to their
  sources, so nothing else can leak.
- `batch { ... }` groups several changes into one notification.
- `map`, `flatMap` and `zip` combine signals, and work in for-comprehensions:
  `for a <- x; b <- y yield a + b`.

Signals are single-threaded, as a browser UI is.

### Binding signals to the DOM

Updates are fine-grained, with no virtual DOM: each binding updates exactly the
DOM it created when its signal changes.

| | |
|---|---|
| `bindText(sig)` | a text node kept equal to `sig` |
| `bindAttr(name, sig)`, `bindProp(name, sig)` | an attribute or DOM property (such as `checked`) of the enclosing element |
| `each(items, key) { (key, item) => ... }` | a keyed list. Items that stay keep their DOM, and each gets a signal of its current value |
| `dyn(sig) { value => ... }` | a region, re-rendered when `sig` changes (for `if`s and the like) |
| `mount(parent)(view)` | renders a view and returns its scope; disposing that removes the view and stops all its bindings |

Each binding is an observer belonging to the region it was created in, so it is
disposed when the region is re-rendered or removed, and removed DOM is never
kept alive by the signals it observed. Bindings must be created while rendering
inside `mount`; elsewhere they throw.
[`examples/todomvc`](examples/todomvc/src/todomvc/App.scala) is a complete
[TodoMVC](https://todomvc.com).

## Examples

| Example | Run with |
|---|---|
| [components](examples/components/src/components) – a component library | `./mill examples.components.run > demo.html` |
| [server](examples/server/src/App.scala) – a to-do app served by [keg](https://github.com/jodersky/keg) | `./mill examples.server.run`, then open http://localhost:8080 |
| [todomvc](examples/todomvc/src/todomvc/App.scala) – TodoMVC in the browser | `./mill show examples.todomvc.site`, then open `index.html` in the printed directory |

## Performance

The [benchmark](bench/src/qtags/bench/PageBench.scala) renders the same page
with qtags and scalatags 0.13.1: a page shell, a nav list and a table with one
row per user, built from scratch on every call. Both libraries produce the same
HTML.

| rows | qtags | scalatags | speedup | qtags allocated | scalatags allocated |
|---:|---:|---:|---:|---:|---:|
| 10 | 3.41 ± 0.21 µs | 16.2 ± 1.3 µs | 4.7× | 7.6 KB | 30.7 KB |
| 100 | 28.8 ± 7.8 µs | 127 ± 16 µs | 4.4× | 59.4 KB | 234.9 KB |
| 1000 | 264 ± 35 µs | 1298 ± 124 µs | 4.9× | 541.3 KB | 2214.2 KB |

Average time and allocation per page, measured with JMH on one machine. Run
`scripts/bench` to measure on yours.

## Development

The build uses [Mill](https://mill-build.org); `./mill` downloads it.

```
scripts/test         # all tests, on all platforms
scripts/bench        # the benchmark, printed as a table
./mill qtags.jvm.test             # one module's tests
./mill __.compile                 # compile everything
```

The Scala.js DOM tests run under [jsdom](https://github.com/jsdom/jsdom);
`scripts/test` installs it on first run (`npm ci` in `qtags/test/js`). Native
builds need clang.

Cross-platform modules keep shared code in `<module>/src`, and
platform-specific code in `src-jvm`, `src-js` and `src-native`. The same layout
applies to tests under `<module>/test`.
