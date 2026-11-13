package components

import qtags.*

/** A small component library built on qtags.
  *
  * Components are plain functions that return a `Frag`. They live in an object
  * so they can reuse tag names (`ui.button`, `ui.table`) without clashing with
  * qtags' own tags: callers write `ui.card(...)`, and inside this object the
  * plain tags are reached through `tags.button` etc.
  *
  * Patterns shown here:
  *   - `page`:          a full-page layout with a content block
  *   - `card`, `alert`: a component wrapping a block of children
  *   - `sidebarLayout`: several blocks of children
  *   - `button`:        variants as an enum, plus extra attributes from the caller
  *   - `table`:         a generic, data-driven component
  *   - `textField`:     a form field: label and input wired together
  */
object ui:

  enum Variant(val classes: String):
    case Primary   extends Variant("btn btn-primary")
    case Secondary extends Variant("btn btn-secondary")
    case Danger    extends Variant("btn btn-danger")

  enum Tone:
    case Info, Success, Warning

  /** A complete HTML document: doctype, head with this library's CSS, an
    * optional navigation bar, and `content` as the main section.
    */
  def page(pageTitle: String, links: Seq[(String, String)] = Nil)(content: Frag): Frag =
    doctype
    html {
      lang("en")
      head {
        meta { charset("utf-8") }
        meta { name("viewport"); attrs.content("width=device-width, initial-scale=1") }
        tags.title { text(pageTitle) }
        tags.style { raw(stylesheet) }
      }
      body {
        if links.nonEmpty then navbar(links)
        main {
          cls("container")
          h1 { text(pageTitle) }
          content
        }
      }
    }

  def navbar(links: Seq[(String, String)]): Frag =
    nav {
      cls("navbar")
      ul { for (label, url) <- links do li { a { href(url); text(label) } } }
    }

  /** A box with a heading. The block passed in becomes its body. */
  def card(heading: String)(content: Frag): Frag =
    section {
      cls("card")
      h2 { text(heading) }
      content
    }

  def alert(tone: Tone)(content: Frag): Frag =
    div {
      cls("alert alert-" + tone.toString.toLowerCase)
      attr("role", "alert")
      content
    }

  /** Several blocks of children: pass all but the last by name, e.g.
    * `ui.sidebarLayout(sidebar = { ... }) { ... }`.
    */
  def sidebarLayout(sidebar: Frag)(content: Frag): Frag =
    div {
      cls("with-sidebar")
      aside { sidebar }
      div { content }
    }

  /** A button. `extra` can add attributes, e.g. `ui.button("Save") { tpe("submit") }`.
    * It runs while the start tag is still open, so it may only set attributes,
    * and should not set `class` (the variant already does).
    */
  def button(label: String, variant: Variant = Variant.Primary)(extra: Frag = ()): Frag =
    tags.button {
      cls(variant.classes)
      extra
      text(label)
    }

  /** A column of `ui.table`: a header and how to render a row's cell. */
  final case class Column[A](header: String, cell: A => Frag)

  /** A table with one row per element of `rows`. */
  def table[A](rows: Seq[A])(columns: Column[A]*): Frag =
    tags.table {
      cls("table")
      thead { tr { for c <- columns do th { text(c.header) } } }
      tbody {
        for row <- rows do
          tr { for c <- columns do td { c.cell(row) } }
      }
    }

  /** A labelled text input. */
  def textField(fieldName: String, labelText: String, placeholderText: String = ""): Frag =
    val fieldId = "field-" + fieldName
    div {
      cls("field")
      label { `for`(fieldId); text(labelText) }
      input {
        tpe("text"); id(fieldId); name(fieldName)
        if placeholderText.nonEmpty then placeholder(placeholderText)
      }
    }

  val stylesheet = """
    body { font-family: system-ui, sans-serif; margin: 0; color: #222; }
    .container { max-width: 50rem; margin: 0 auto; padding: 1rem; }
    .navbar { background: #234; padding: 0.5rem 1rem; }
    .navbar ul { list-style: none; display: flex; gap: 1rem; margin: 0; padding: 0; }
    .navbar a { color: white; text-decoration: none; }
    .card { border: 1px solid #ddd; border-radius: 6px; padding: 0 1rem 1rem; margin: 1rem 0; }
    .alert { padding: 0.75rem 1rem; border-radius: 6px; margin: 1rem 0; }
    .alert-info { background: #e6f0fa; } .alert-success { background: #e5f6e8; }
    .alert-warning { background: #fdf3dc; }
    .with-sidebar { display: grid; grid-template-columns: 12rem 1fr; gap: 1rem; }
    .btn { border: 0; border-radius: 4px; padding: 0.4rem 0.8rem; cursor: pointer; }
    .btn-primary { background: #2563eb; color: white; } .btn-secondary { background: #ddd; }
    .btn-danger { background: #dc2626; color: white; }
    .table { border-collapse: collapse; width: 100%; }
    .table th, .table td { text-align: left; padding: 0.4rem; border-bottom: 1px solid #eee; }
    .field { margin: 0.5rem 0; } .field label { display: block; font-size: 0.9rem; }
    .done { text-decoration: line-through; color: #888; }
  """
