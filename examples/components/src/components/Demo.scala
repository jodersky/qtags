package components

import qtags.*

final case class Person(name: String, role: String, email: String)

/** A page using every component in `ui`. Run with
  * `./mill examples.components.run > demo.html` and open it in a browser.
  */
def demoPage: Frag =
  val people = Seq(
    Person("Ada", "Engineer", "ada@example.com"),
    Person("Grace", "Admiral & Engineer", "grace@example.com")
  )
  ui.page("Component demo", links = Seq("Home" -> "/", "About" -> "/about")) {
    ui.alert(ui.Tone.Info) { text("Everything on this page is a ui component.") }

    ui.sidebarLayout(sidebar = { p { text("A sidebar block.") } }) {
      ui.card("People") {
        ui.table(people)(
          ui.Column("Name", p => strong { text(p.name) }),
          ui.Column("Role", p => text(p.role)),
          ui.Column("Email", p => a { href("mailto:" + p.email); text(p.email) })
        )
      }

      ui.card("Invite someone") {
        form {
          action("/invite"); method("post")
          ui.textField("email", "Email", placeholderText = "name@example.com")
          ui.button("Send invite") { tpe("submit") }
          text(" ")
          ui.button("Cancel", ui.Variant.Secondary)()
        }
      }
    }
  }

@main def demo(): Unit =
  print(renderString(demoPage))
