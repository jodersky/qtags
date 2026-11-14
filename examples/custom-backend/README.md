# custom-backend

Rendering qtags fragments with your own backend.

Tags don't build anything themselves: they send a stream of events to a
backend, an `h.Html` passed along as a context parameter. qtags has backends
for strings, bytes and the DOM, and you can write your own by implementing
six methods:

```scala
// in package qtags
trait Html:
  def open(name: String, start: String): Unit                   // <name
  def attr(name: String, prefix: String, value: String): Unit   //  name="value"
  def text(s: String): Unit                                     // text, to escape
  def raw(html: String): Unit                                   // markup, as is
  def close(name: String, end: String): Unit                    // </name>
  def closeVoid(name: String): Unit                             // end of <br>, <img>, ...
```

`start`, `prefix` and `end` are the markup for the name (`<a`, ` href="`,
`</a>`), precomputed at compile time, for backends that write text. Attributes
always come right after `open`, before any content; backends should throw if
they don't (`attribute after content`), so that a fragment behaves the same
with every backend.

To render with a backend, apply the fragment to it:

```scala
import qtags as h
import backends.PrettyHtml

val fragment: h.Frag = h.p { h.text("Hello") }
val out = java.lang.StringBuilder()
fragment(using PrettyHtml(out))
```

This example has two backends:

- [PrettyHtml.scala](src/backends/PrettyHtml.scala) writes indented HTML, for
  reading and debugging. It reuses qtags' `h.escapeText` and `h.escapeAttr`.
- [TreeHtml.scala](src/backends/TreeHtml.scala) builds a tree of nodes instead
  of text, which can be inspected without parsing HTML, e.g. in tests.

[Main.scala](src/backends/Main.scala) renders one fragment with
`h.renderString` and with both backends.

## Run

```sh
./mill examples.custom-backend.run
```
