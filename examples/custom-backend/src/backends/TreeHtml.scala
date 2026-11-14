package backends

import qtags as h
import scala.collection.mutable

/** A tree of HTML nodes, as built by `TreeHtml`. */
enum Node:
  case Element(name: String, attrs: Seq[(String, String)], children: Seq[Node])
  case Text(text: String)
  case Raw(html: String)

  /** This node and all nodes below it, depth first. */
  def all: Seq[Node] = this match
    case e: Element => e +: e.children.flatMap(_.all)
    case other      => Seq(other)

  /** The text inside this node, without markup. */
  def textContent: String = this match
    case Element(_, _, children) => children.map(_.textContent).mkString
    case Text(text)              => text
    case Raw(_)                  => ""

object Node:
  extension (e: Element) def attr(name: String): Option[String] = e.attrs.collectFirst { case (`name`, v) => v }

/** A backend that builds a tree of `Node`s instead of writing text: a fragment
  * rendered with it can be inspected, e.g. in tests, without parsing HTML.
  */
final class TreeHtml extends h.Html:
  /** An element that has been opened but not closed yet. */
  private final class Open(val name: String):
    val attrs = mutable.ArrayBuffer.empty[(String, String)]
    val children = mutable.ArrayBuffer.empty[Node]

  private val root = Open("")
  private var stack = List(root)        // innermost open element first

  /** The nodes rendered so far, at the top level. */
  def nodes: Seq[Node] = root.children.toSeq

  def open(name: String, start: String): Unit =
    stack = Open(name) :: stack

  def attr(name: String, prefix: String, value: String): Unit =
    if stack.head.children.nonEmpty then throw IllegalStateException(s"attribute $name after content")
    stack.head.attrs += name -> value

  def text(s: String): Unit = stack.head.children += Node.Text(s)

  def raw(html: String): Unit = stack.head.children += Node.Raw(html)

  def close(name: String, end: String): Unit =
    val e = stack.head
    stack = stack.tail
    stack.head.children += Node.Element(e.name, e.attrs.toSeq, e.children.toSeq)

  def closeVoid(name: String): Unit =
    if stack.head.children.nonEmpty then throw IllegalStateException(s"void element <$name> cannot have content")
    close(name, "")

/** Render `f` as a tree of nodes. */
def renderTree(f: h.Frag): Seq[Node] =
  val html = TreeHtml()
  f(using html)
  html.nodes
