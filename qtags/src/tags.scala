package qtags

// All current (non-obsolete) HTML elements, per the WHATWG HTML standard.
// svg and math are not included: they need XML namespaces in the DOM backend.
//
// Void elements (br, img, input, ...) can't have content; their body may only
// contain attributes, and they are called with an empty argument list when they
// have none: `br()`.
//
// Most of these are exported directly from `qtags`; see exports.scala.
object tags:

  // Document metadata
  inline def html (inline body: Frag)(using Html)       = tag("html")(body)
  inline def head (inline body: Frag)(using Html)       = tag("head")(body)
  inline def title(inline body: Frag)(using Html)       = tag("title")(body)
  inline def base (inline attrs: Frag = ())(using Html) = voidTag("base")(attrs)
  inline def link (inline attrs: Frag = ())(using Html) = voidTag("link")(attrs)
  inline def meta (inline attrs: Frag = ())(using Html) = voidTag("meta")(attrs)
  inline def style(inline body: Frag)(using Html)       = tag("style")(body)
  inline def body (inline body: Frag)(using Html)       = tag("body")(body)

  // Sections
  inline def article(inline body: Frag)(using Html)       = tag("article")(body)
  inline def section(inline body: Frag)(using Html)       = tag("section")(body)
  inline def nav    (inline body: Frag)(using Html)       = tag("nav")(body)
  inline def aside  (inline body: Frag)(using Html)       = tag("aside")(body)
  inline def h1     (inline body: Frag)(using Html)       = tag("h1")(body)
  inline def h2     (inline body: Frag)(using Html)       = tag("h2")(body)
  inline def h3     (inline body: Frag)(using Html)       = tag("h3")(body)
  inline def h4     (inline body: Frag)(using Html)       = tag("h4")(body)
  inline def h5     (inline body: Frag)(using Html)       = tag("h5")(body)
  inline def h6     (inline body: Frag)(using Html)       = tag("h6")(body)
  inline def hgroup (inline body: Frag)(using Html)       = tag("hgroup")(body)
  inline def header (inline body: Frag)(using Html)       = tag("header")(body)
  inline def footer (inline body: Frag)(using Html)       = tag("footer")(body)
  inline def address(inline body: Frag)(using Html)       = tag("address")(body)
  inline def search (inline body: Frag)(using Html)       = tag("search")(body)

  // Grouping content
  inline def p         (inline body: Frag)(using Html)       = tag("p")(body)
  inline def hr        (inline attrs: Frag = ())(using Html) = voidTag("hr")(attrs)
  inline def pre       (inline body: Frag)(using Html)       = tag("pre")(body)
  inline def blockquote(inline body: Frag)(using Html)       = tag("blockquote")(body)
  inline def ol        (inline body: Frag)(using Html)       = tag("ol")(body)
  inline def ul        (inline body: Frag)(using Html)       = tag("ul")(body)
  inline def menu      (inline body: Frag)(using Html)       = tag("menu")(body)
  inline def li        (inline body: Frag)(using Html)       = tag("li")(body)
  inline def dl        (inline body: Frag)(using Html)       = tag("dl")(body)
  inline def dt        (inline body: Frag)(using Html)       = tag("dt")(body)
  inline def dd        (inline body: Frag)(using Html)       = tag("dd")(body)
  inline def figure    (inline body: Frag)(using Html)       = tag("figure")(body)
  inline def figcaption(inline body: Frag)(using Html)       = tag("figcaption")(body)
  inline def main      (inline body: Frag)(using Html)       = tag("main")(body)
  inline def div       (inline body: Frag)(using Html)       = tag("div")(body)

  // Text-level semantics
  inline def a     (inline body: Frag)(using Html)       = tag("a")(body)
  inline def em    (inline body: Frag)(using Html)       = tag("em")(body)
  inline def strong(inline body: Frag)(using Html)       = tag("strong")(body)
  inline def small (inline body: Frag)(using Html)       = tag("small")(body)
  inline def s     (inline body: Frag)(using Html)       = tag("s")(body)
  inline def cite  (inline body: Frag)(using Html)       = tag("cite")(body)
  inline def q     (inline body: Frag)(using Html)       = tag("q")(body)
  inline def dfn   (inline body: Frag)(using Html)       = tag("dfn")(body)
  inline def abbr  (inline body: Frag)(using Html)       = tag("abbr")(body)
  inline def ruby  (inline body: Frag)(using Html)       = tag("ruby")(body)
  inline def rt    (inline body: Frag)(using Html)       = tag("rt")(body)
  inline def rp    (inline body: Frag)(using Html)       = tag("rp")(body)
  inline def data  (inline body: Frag)(using Html)       = tag("data")(body)
  inline def time  (inline body: Frag)(using Html)       = tag("time")(body)
  inline def code  (inline body: Frag)(using Html)       = tag("code")(body)
  inline def `var` (inline body: Frag)(using Html)       = tag("var")(body)
  inline def samp  (inline body: Frag)(using Html)       = tag("samp")(body)
  inline def kbd   (inline body: Frag)(using Html)       = tag("kbd")(body)
  inline def sub   (inline body: Frag)(using Html)       = tag("sub")(body)
  inline def sup   (inline body: Frag)(using Html)       = tag("sup")(body)
  inline def i     (inline body: Frag)(using Html)       = tag("i")(body)
  inline def b     (inline body: Frag)(using Html)       = tag("b")(body)
  inline def u     (inline body: Frag)(using Html)       = tag("u")(body)
  inline def mark  (inline body: Frag)(using Html)       = tag("mark")(body)
  inline def bdi   (inline body: Frag)(using Html)       = tag("bdi")(body)
  inline def bdo   (inline body: Frag)(using Html)       = tag("bdo")(body)
  inline def span  (inline body: Frag)(using Html)       = tag("span")(body)
  inline def br    (inline attrs: Frag = ())(using Html) = voidTag("br")(attrs)
  inline def wbr   (inline attrs: Frag = ())(using Html) = voidTag("wbr")(attrs)

  // Edits
  inline def ins(inline body: Frag)(using Html)       = tag("ins")(body)
  inline def del(inline body: Frag)(using Html)       = tag("del")(body)

  // Embedded content
  inline def picture (inline body: Frag)(using Html)       = tag("picture")(body)
  inline def source  (inline attrs: Frag = ())(using Html) = voidTag("source")(attrs)
  inline def img     (inline attrs: Frag = ())(using Html) = voidTag("img")(attrs)
  inline def iframe  (inline body: Frag)(using Html)       = tag("iframe")(body)
  inline def embed   (inline attrs: Frag = ())(using Html) = voidTag("embed")(attrs)
  inline def `object`(inline body: Frag)(using Html)       = tag("object")(body)
  inline def video   (inline body: Frag)(using Html)       = tag("video")(body)
  inline def audio   (inline body: Frag)(using Html)       = tag("audio")(body)
  inline def track   (inline attrs: Frag = ())(using Html) = voidTag("track")(attrs)
  inline def map     (inline body: Frag)(using Html)       = tag("map")(body)
  inline def area    (inline attrs: Frag = ())(using Html) = voidTag("area")(attrs)

  // Tabular data
  inline def table   (inline body: Frag)(using Html)       = tag("table")(body)
  inline def caption (inline body: Frag)(using Html)       = tag("caption")(body)
  inline def colgroup(inline body: Frag)(using Html)       = tag("colgroup")(body)
  inline def col     (inline attrs: Frag = ())(using Html) = voidTag("col")(attrs)
  inline def tbody   (inline body: Frag)(using Html)       = tag("tbody")(body)
  inline def thead   (inline body: Frag)(using Html)       = tag("thead")(body)
  inline def tfoot   (inline body: Frag)(using Html)       = tag("tfoot")(body)
  inline def tr      (inline body: Frag)(using Html)       = tag("tr")(body)
  inline def td      (inline body: Frag)(using Html)       = tag("td")(body)
  inline def th      (inline body: Frag)(using Html)       = tag("th")(body)

  // Forms
  inline def form    (inline body: Frag)(using Html)       = tag("form")(body)
  inline def label   (inline body: Frag)(using Html)       = tag("label")(body)
  inline def input   (inline attrs: Frag = ())(using Html) = voidTag("input")(attrs)
  inline def button  (inline body: Frag)(using Html)       = tag("button")(body)
  inline def select  (inline body: Frag)(using Html)       = tag("select")(body)
  inline def datalist(inline body: Frag)(using Html)       = tag("datalist")(body)
  inline def optgroup(inline body: Frag)(using Html)       = tag("optgroup")(body)
  inline def option  (inline body: Frag)(using Html)       = tag("option")(body)
  inline def textarea(inline body: Frag)(using Html)       = tag("textarea")(body)
  inline def output  (inline body: Frag)(using Html)       = tag("output")(body)
  inline def progress(inline body: Frag)(using Html)       = tag("progress")(body)
  inline def meter   (inline body: Frag)(using Html)       = tag("meter")(body)
  inline def fieldset(inline body: Frag)(using Html)       = tag("fieldset")(body)
  inline def legend  (inline body: Frag)(using Html)       = tag("legend")(body)

  // Interactive elements
  inline def details(inline body: Frag)(using Html)       = tag("details")(body)
  inline def summary(inline body: Frag)(using Html)       = tag("summary")(body)
  inline def dialog (inline body: Frag)(using Html)       = tag("dialog")(body)

  // Scripting
  inline def script  (inline body: Frag)(using Html)       = tag("script")(body)
  inline def noscript(inline body: Frag)(using Html)       = tag("noscript")(body)
  inline def template(inline body: Frag)(using Html)       = tag("template")(body)
  inline def slot    (inline body: Frag)(using Html)       = tag("slot")(body)
  inline def canvas  (inline body: Frag)(using Html)       = tag("canvas")(body)
