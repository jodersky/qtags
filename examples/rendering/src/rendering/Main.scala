package rendering

import java.nio.file.Files
import qtags as h
import scala.util.Using

/** The ways to render a fragment on the JVM: `h.renderString`, `h.render`
  * and `h.renderLazy`. Run with `./mill examples.rendering.run`.
  */

def greeting(name: String): h.Frag =
  h.p {
    h.cls("greeting")
    h.text("Hello, ")
    h.strong { h.text(name) }
  }

/** A page with `n` rows. `runs` counts how often the fragment is rendered. */
var runs = 0
def report(n: Int): h.Frag =
  runs += 1
  h.doctype
  h.html {
    h.body {
      h.h1 { h.text("Report") }
      h.table {
        for i <- 1 to n do
          h.tr { h.td { h.text(i.toString) }; h.td { h.text(s"row $i") } }
      }
    }
  }

@main def run(): Unit =
  println("== renderString: a String, rendered right away")
  val s: String = h.renderString(greeting("Ada & Grace"))
  println(s)
  // <p class="greeting">Hello, <strong>Ada &amp; Grace</strong></p>

  println()
  println("== render: a geny.Writable, rendered right away")
  // Use it where a Writable is accepted: as the body of an HTTP response
  // (keg, cask, requests), or written to a file. The page is rendered into memory
  // on this line, so errors are thrown here, and its length is known.
  runs = 0
  val eager: geny.Writable = h.render(report(3))
  println(s"rendered ${runs} time(s)")
  println(s"content type:   ${eager.httpContentType.get}")
  println(s"content length: ${eager.contentLength.get} bytes")
  eager.writeBytesTo(System.out)
  System.out.flush()
  println()

  println()
  println("== renderLazy: a geny.Writable that streams while it renders")
  // Nothing is rendered yet: the fragment runs each time the Writable is
  // written, in chunks, so a large page never sits in memory as a whole. The
  // length is unknown until it has been written.
  runs = 0
  val lazyPage: geny.Writable = h.renderLazy(report(100_000))
  println(s"rendered ${runs} time(s)")
  println(s"content length: ${lazyPage.contentLength.getOrElse("unknown")}")

  val file = Files.createTempFile("report", ".html")
  def writeFile(): Unit = Using.resource(Files.newOutputStream(file))(lazyPage.writeBytesTo)
  writeFile()
  println(s"rendered ${runs} time(s), wrote ${Files.size(file)} bytes to $file")
  writeFile()
  println(s"rendered ${runs} time(s) after writing it again")
  Files.delete(file)
