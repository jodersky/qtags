package qtags.bench

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.*

final case class User(id: Int, name: String, email: String, admin: Boolean)

/** Builds and renders the same page with both libraries: a page shell, a nav
  * list, and a table with one row per user. Some names contain `&` or `<`, so
  * escaping is exercised. Both libraries build the page from scratch on every
  * call, as a server does per request.
  */
object Pages:
  val navItems = Seq("Home" -> "/", "Users" -> "/users", "Settings" -> "/settings")

  def users(n: Int): Seq[User] =
    (1 to n).map(i =>
      val name = if i % 10 == 0 then s"Smith & Sons <$i>" else s"User $i"
      User(i, name, s"user$i@example.com", i % 7 == 0)
    )

  def qtagsPage(users: Seq[User]): String =
    import qtags.*
    renderString {
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

  def scalatagsPage(users: Seq[User]): String =
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
    ).render

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
class PageBench:
  @Param(Array("10", "100", "1000"))
  var rows: Int = 0

  var users: Seq[User] = Nil

  @Setup
  def setup(): Unit =
    users = Pages.users(rows)
    // Make sure both libraries do the same work. The only accepted difference
    // is that scalatags closes void elements XHTML-style (`<meta ... />`).
    val q = Pages.qtagsPage(users)
    val s = Pages.scalatagsPage(users).replace(" />", ">")
    if q != s then
      throw IllegalStateException(s"outputs differ:\nqtags:     $q\nscalatags: $s")

  @Benchmark def qtags: String = Pages.qtagsPage(users)

  @Benchmark def scalatags: String = Pages.scalatagsPage(users)
