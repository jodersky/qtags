package qtags.bench

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.*

final case class User(id: Int, name: String, email: String, admin: Boolean)

/** Builds and renders the same page with both libraries: a page shell, a nav
  * list, and a table with one row per user. Some names contain `&` or `<`, so
  * escaping is exercised. Both libraries build the page from scratch on every
  * call and write it out as UTF-8 bytes, as a server does per request: each
  * page is a `geny.Writable`, the type web servers such as cask take.
  */
object Pages:
  val navItems = Seq("Home" -> "/", "Users" -> "/users", "Settings" -> "/settings")

  def users(n: Int): Seq[User] =
    (1 to n).map(i =>
      val name = if i % 10 == 0 then s"Smith & Sons <$i>" else s"User $i"
      User(i, name, s"user$i@example.com", i % 7 == 0)
    )

  def qtagsPage(users: Seq[User]): geny.Writable =
    import qtags.*
    render {
      doctype
      html {
        head {
          meta { charset("utf-8") }
          tags.title { text("Users") }
          link { rel("stylesheet"); href("/style.css") }
        }
        body {
          nav {
            ul {
              cls("nav")
              for (label, url) <- navItems do
                li { a { href(url); text(label) } }
            }
          }
          main {
            h1 { text("Users") }
            table {
              cls("users")
              thead { tr { th { text("Id") }; th { text("Name") }; th { text("Email") } } }
              tbody {
                for u <- users do
                  tr {
                    id(s"user-${u.id}")
                    cls(if u.admin then "admin" else "user")
                    td { text(u.id.toString) }
                    td { a { href(s"/users/${u.id}"); text(u.name) } }
                    td { text(u.email) }
                  }
              }
            }
          }
        }
      }
    }

  /** scalatags' frags are `geny.Writable`s too, but their `writeBytesTo` is
    * slow: it allocates over twice as much as rendering to a String and
    * encoding that. So this uses the faster way.
    */
  def scalatagsPage(users: Seq[User]): geny.Writable =
    val page = scalatagsTree(users)
    new geny.Writable:
      def writeBytesTo(out: java.io.OutputStream): Unit =
        out.write(page.render.getBytes(java.nio.charset.StandardCharsets.UTF_8))

  def scalatagsTree(users: Seq[User]) =
    import scalatags.Text.all.*
    import scalatags.Text.tags2
    doctype("html")(
      html(
        head(
          meta(charset := "utf-8"),
          tags2.title("Users"),
          link(rel := "stylesheet", href := "/style.css")
        ),
        body(
          tags2.nav(
            ul(cls := "nav")(
              for (label, url) <- navItems yield li(a(href := url)(label))
            )
          ),
          tags2.main(
            h1("Users"),
            table(cls := "users")(
              thead(tr(th("Id"), th("Name"), th("Email"))),
              tbody(
                for u <- users yield
                  tr(id := s"user-${u.id}", cls := (if u.admin then "admin" else "user"))(
                    td(u.id.toString),
                    td(a(href := s"/users/${u.id}")(u.name)),
                    td(u.email)
                  )
              )
            )
          )
        )
      )
    )

  /** Write `page` as a server would, to a stream that discards the bytes. */
  def send(page: geny.Writable): Unit = page.writeBytesTo(java.io.OutputStream.nullOutputStream())

  def bytes(page: geny.Writable): Array[Byte] =
    val out = java.io.ByteArrayOutputStream()
    page.writeBytesTo(out)
    out.toByteArray

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
class PageBench:
  @Param(Array("10", "100", "1000", "10000"))
  var rows: Int = 0

  var users: Seq[User] = Nil

  @Setup
  def setup(): Unit =
    users = Pages.users(rows)
    // Make sure both libraries do the same work. The only accepted difference
    // is that scalatags closes void elements XHTML-style (`<meta ... />`).
    val q = String(Pages.bytes(Pages.qtagsPage(users)), "UTF-8")
    val s = String(Pages.bytes(Pages.scalatagsPage(users)), "UTF-8").replace(" />", ">")
    if q != s then
      throw IllegalStateException(s"outputs differ:\nqtags:     $q\nscalatags: $s")

  @Benchmark def qtags: Unit = Pages.send(Pages.qtagsPage(users))

  @Benchmark def scalatags: Unit = Pages.send(Pages.scalatagsPage(users))

/** Prints the size in bytes (UTF-8) of each library's page for each number of
  * rows given, as JSON: `{"10": {"qtags": 1234, "scalatags": 1240}, ...}`.
  * scripts/bench subtracts it from the allocations: a page can't be rendered
  * with less memory than the page itself takes.
  */
object PageSizes:
  def main(args: Array[String]): Unit =
    def size(page: geny.Writable) = Pages.bytes(page).length
    val entries = args.map: rows =>
      val users = Pages.users(rows.toInt)
      val q = size(Pages.qtagsPage(users))
      val s = size(Pages.scalatagsPage(users))
      s"\"$rows\": {\"qtags\": $q, \"scalatags\": $s}"
    println(entries.mkString("{", ", ", "}"))
