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

Apart from context functions, qtags has no magic. No special implicit imports or
wildcard imports needed. Behavior is local and can easily be debugged.

Qtags can generate plain strings, target the DOM directly from scalajs, and is
[geny](https://github.com/com-lihaoyi/geny)-compatible.

This repository has two modules:

- `qtags`: the HTML DSL, rendering to strings, streams or the DOM (available for
  the JVM, Scala.js, ans Scala Native)

- `qtags.sigs`: an experimental helper module for building reactive UIs
  (available for Scala.js only)

## qtags


## qtags.sigs

## Examples

| Example | Run with |
|---|---|
| [components](examples/components) – a component library | `./mill examples.components.run > demo.html` |
| [rendering](examples/rendering) – the render functions and when to use each | `./mill examples.rendering.run` |
| [keg-server](examples/keg-server) – a to-do app served by [keg](https://github.com/jodersky/keg) | `./mill examples.keg-server.run`, then open http://localhost:8080 |
| [sigs-basic](examples/sigs-basic) – the basics of signals in the browser | `./mill show examples.sigs-basic.site`, then open `index.html` in the printed directory |
| [sigs-multi](examples/sigs-multi) – counters added and removed at run time, and their sum | `./mill show examples.sigs-multi.site`, then open `index.html` in the printed directory |
| [sigs-todomvc](examples/sigs-todomvc) – TodoMVC in the browser | `./mill show examples.sigs-todomvc.site`, then open `index.html` in the printed directory |

## Performance

The [benchmark](bench/src/qtags/bench/PageBench.scala) renders the same page
with qtags and scalatags 0.13.1. Both libraries produce the same HTML.

| rows | qtags | scalatags | speedup | qtags allocated | scalatags allocated |
|---:|---:|---:|---:|---:|---:|
| 10 | 3.41 ± 0.21 µs | 16.2 ± 1.3 µs | 4.7× | 7.6 KB | 30.7 KB |
| 100 | 28.8 ± 7.8 µs | 127 ± 16 µs | 4.4× | 59.4 KB | 234.9 KB |
| 1000 | 264 ± 35 µs | 1298 ± 124 µs | 4.9× | 541.3 KB | 2214.2 KB |

Average time and allocation per page, measured with JMH on one machine. Run
`scripts/bench` to measure on yours.

## Development

The build uses [Mill](https://mill-build.org).

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
