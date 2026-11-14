# rendering

The ways to render a qtags fragment, and when to use each one.

```scala
import qtags as h

val page: h.Frag = h.p { h.text("Hello, "); h.strong { h.text("world") } }

h.renderString(page)   // String
h.render(page)         // geny.Writable, rendered right away
h.renderLazy(page)     // geny.Writable, rendered while it is written
```

| Function | Returns | Renders | Use it for |
|---|---|---|---|
| `h.renderString(f)` | `String` | right away | tests, small snippets, anything that needs a `String` |
| `h.render(f)` | `geny.Writable` | right away, into memory | HTTP responses: content type and length are set, and errors are thrown before anything is sent |
| `h.renderLazy(f)` | `geny.Writable` | each time it is written, in chunks | large pages: streamed without being held in memory, but the length is unknown and an error can come after part of the page was sent |

A `geny.Writable` can be returned from a [keg](https://github.com/jodersky/keg)
or [cask](https://github.com/com-lihaoyi/cask) endpoint, sent as a request
body with [requests](https://github.com/com-lihaoyi/requests-scala), or
written to a file with `os.write`.

In the browser (Scala.js), `h.renderInto(parent)(f)` adds the fragment's
elements to a DOM node and `h.renderDom(f)` returns them as a
`DocumentFragment`. For pages that update themselves, see
[sigs-basic](../sigs-basic).

## Run

```sh
./mill examples.rendering.run
```

It renders a small fragment with `h.renderString`, a page with `h.render`, and
a page of 100,000 rows with `h.renderLazy`, which it writes to a file twice. The
output shows when each fragment runs, its content type and its length.

The code is in [Main.scala](src/rendering/Main.scala).
