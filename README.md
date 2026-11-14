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
4-5x faster and almost 10x less memory usage than scalatags, which itself is
already very efficient!

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

TODO

## qtags.sigs

TODO

## Examples

- [components](examples/components): building reusable components on top of
  qtags.
- [rendering](examples/rendering): the different ways to render HTML.
- [keg-server](examples/keg-server): using qtags in a web server.
- [sigs-basic](examples/sigs-basic): basic usage of reactive signals in the
  browser.
- [sigs-multi](examples/sigs-multi): components with their own state, added and
  removed at run time.
- [sigs-todomvc](examples/sigs-todomvc): a complete browser app.

## Performance

The [benchmark](bench/src/qtags/bench/PageBench.scala) renders the same page
with qtags and scalatags 0.13.1. This page includes a table of variable rows.
Both libraries produce the same HTML, and write it out as UTF-8 bytes, as a
web server would.

<table>
<thead>
<tr><th rowspan="2">rows</th><th rowspan="2">page size</th><th colspan="3">CPU</th><th colspan="3">memory overhead</th></tr>
<tr><th>qtags</th><th>scalatags</th><th>speedup</th><th>qtags</th><th>scalatags</th><th>factor</th></tr>
</thead>
<tbody>
<tr><td align="right">10</td><td align="right">1.5 KB</td><td align="right">2.05 ± 0.34 µs</td><td align="right">14.2 ± 1.8 µs</td><td align="right">6.9×</td><td align="right">8.2 KB</td><td align="right">30.7 KB</td><td align="right">3.7×</td></tr>
<tr><td align="right">100</td><td align="right">11.9 KB</td><td align="right">15.5 ± 1.7 µs</td><td align="right">125 ± 15 µs</td><td align="right">8.1×</td><td align="right">27.9 KB</td><td align="right">235.0 KB</td><td align="right">8.4×</td></tr>
<tr><td align="right">1000</td><td align="right">120.0 KB</td><td align="right">173 ± 13 µs</td><td align="right">1.25 ± 0.20 ms</td><td align="right">7.2×</td><td align="right">286.0 KB</td><td align="right">2.2 MB</td><td align="right">7.7×</td></tr>
<tr><td align="right">10000</td><td align="right">1.2 MB</td><td align="right">2.18 ± 0.30 ms</td><td align="right">13.9 ± 2.6 ms</td><td align="right">6.4×</td><td align="right">4.5 MB</td><td align="right">24.2 MB</td><td align="right">5.3×</td></tr>
</tbody>
</table>

Overhead: memory allocated while rendering a page, minus the size of the
generated HTML. This includes any intermediate buffers as well as resizing
thereof. Tuning initial buffer sizes for typical usage patterns could improve
this metric (by default, qtags renders into an 8kB buffer).

Average time and allocation per page, measured with JMH (`-prof gc`) on a
Lenovo ThinkPad X1 Carbon Gen 11 (2023): Intel Core i7-1365U (13th gen, 10
cores, 12 threads, up to 5.2 GHz), 32 GB LPDDR5 at 6000 MT/s, OpenJDK 25.
Run `scripts/bench` to measure on yours.

## Development

The build uses [Mill](https://mill-build.org).

```
scripts/test         # all tests, on all platforms
scripts/bench        # the benchmark, printed as a table
./mill qtags.jvm.test             # one module's tests
./mill __.compile                 # compile everything
```

The Scala.js DOM tests run under [jsdom](https://github.com/jsdom/jsdom);
`scripts/test` installs it on first run (`npm ci` in `qtags/test/js`). They
require npm.

Native builds need clang.
