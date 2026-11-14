package qtags.bench

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.*

/** The benchmark from the "Performance" section of the scalatags docs: render a
  * small page to a String as many times as possible within a fixed time (60 s
  * on the website) and count the renders.
  *
  * The scalatags template is copied from scalatags' own
  * `scalatags/test/src/scalatags/generic/PerfTests.scala`; the qtags one
  * produces the same markup.
  */
object PerfPage:
  val titleString = "This is my title"
  val firstParaString = "This is my first paragraph"
  val contentpara = "contentpara"
  val first = "first"

  def scalatagsPage(): String =
    import scalatags.Text.all.*

    def para(n: Int, m: Modifier*) = p(
      m,
      title := ("this is paragraph " + n)
    )

    html(
      head(
        script("console.log(1)")
      ),
      body(
        h1(color := "red")(titleString),
        div(backgroundColor := "blue")(
          para(0,
            cls := contentpara + " " + first,
            firstParaString
          ),
          a(href := "www.google.com")(
            p("Goooogle")
          ),
          for (i <- 0 until 5) yield {
            para(i,
              cls := contentpara,
              color := (if (i % 2 == 0) "red" else "green"),
              "Paragraph ",
              i
            )
          }
        )
      )
    ).toString

  def qtagsPage(): String =
    import qtags.*
    renderString {
      html {
        head {
          script { raw("console.log(1)") }
        }
        body {
          h1 { style("color: red;"); text(titleString) }
          div {
            style("background-color: blue;")
            p {
              cls(contentpara + " " + first)
              title("this is paragraph 0")
              text(firstParaString)
            }
            a { href("www.google.com"); p { text("Goooogle") } }
            for i <- 0 until 5 do
              p {
                cls(contentpara)
                style(if i % 2 == 0 then "color: red;" else "color: green;")
                title("this is paragraph " + i)
                text("Paragraph ")
                text(i.toString)
              }
          }
        }
      }
    }

  /** Make sure both libraries do the same work: the pages must be identical. */
  def check(): Unit =
    val q = qtagsPage()
    val s = scalatagsPage()
    if q != s then
      throw IllegalStateException(s"outputs differ:\nqtags:     $q\nscalatags: $s")

/** Runs the benchmark the way scalatags does: one render to check the output,
  * then a loop that renders for a fixed time and counts.
  *
  *   ./mill bench.runMain qtags.bench.ScalatagsPerf [seconds] [qtags|scalatags]
  *
  * The default is 60 seconds per library, both libraries, as on the website.
  */
object ScalatagsPerf:
  def main(args: Array[String]): Unit =
    val seconds = args.headOption.fold(60)(_.toInt)
    val libraries = Seq("qtags" -> (() => PerfPage.qtagsPage()), "scalatags" -> (() => PerfPage.scalatagsPage()))
      .filter((name, _) => args.lengthIs < 2 || args.drop(1).contains(name))

    PerfPage.check()
    val counts =
      for (name, calc) <- libraries yield
        System.err.println(s"Benchmarking $name for $seconds s")
        val d = seconds * 1000L
        val start = System.currentTimeMillis()
        var i = 0L
        while System.currentTimeMillis() - start < d do
          i += 1
          calc()
        name -> i

    println("| library | renders | renders/s |")
    println("|---|---:|---:|")
    for (name, i) <- counts do println(f"| $name | $i | ${i.toDouble / seconds}%.0f |")

/** The same page under JMH, as throughput (renders per second), with warmup and
  * forks for steadier numbers than the plain loop above.
  *
  *   ./mill bench.runJmh ScalatagsPerfBench
  */
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
class ScalatagsPerfBench:
  @Setup def setup(): Unit = PerfPage.check()

  @Benchmark def qtags: String = PerfPage.qtagsPage()

  @Benchmark def scalatags: String = PerfPage.scalatagsPage()
