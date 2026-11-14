# components

Shows how qtags can be used to build a component library.

Components are plain functions that return an `h.Frag`. A component that
takes a block of children takes an `h.Frag` parameter and places it where the children
go:

```scala
import qtags as h

def card(heading: String)(content: h.Frag): h.Frag =
  h.section {
    h.cls("card")
    h.h2 { h.text(heading) }
    content
  }

h.renderString {
  card("People") {
    h.p { h.text("Ada, Grace") }
  }
}
// <section class="card"><h2>People</h2><p>Ada, Grace</p></section>
```

## Run

```sh
./mill examples.components.run > demo.html
```

Then open `demo.html` in a browser.

## Files

- [ui.scala](src/components/ui.scala): the components, in an object `ui` so
  that names like `ui.button` and `ui.table` don't clash with qtags' own tags.
  Each one shows a different pattern:
  - `page`: a full-page layout with a content block
  - `card`, `alert`: a component wrapping a block of children
  - `sidebarLayout`: a component taking several blocks of children
  - `button`: variants as an enum, plus extra attributes from the caller
  - `table`: a generic component driven by data, with one `Column` per field
  - `textField`: a label and an input wired together
- [Demo.scala](src/components/Demo.scala): a page using every component, and a
  `main` that prints it.

The [keg-server](../keg-server) example reuses these components.
