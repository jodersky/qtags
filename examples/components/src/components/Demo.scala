package components

import qtags as h

final case class Person(name: String, role: String, email: String)

/** A page using every component in `ui`. Run with
  * `./mill examples.components.run > demo.html` and open it in a browser.
  */
def demoPage: h.Frag =
  val people = Seq(
    Person("Ada", "Engineer", "ada@example.com"),
    Person("Grace", "Admiral & Engineer", "grace@example.com")
  )
  ui.page("Component demo", links = Seq("Home" -> "/", "About" -> "/about")) {
    ui.alert(ui.Tone.Info) { h.text("Everything on this page is a ui component.") }

    ui.sidebarLayout(sidebar = { h.p { h.text("A sidebar block.") } }) {
      ui.card("People") {
        ui.table(people)(
          ui.Column("Name", p => h.strong { h.text(p.name) }),
          ui.Column("Role", p => h.text(p.role)),
          ui.Column("Email", p => h.a { h.href("mailto:" + p.email); h.text(p.email) })
        )
      }

      ui.card("Invite someone") {
        h.form {
          h.action("/invite"); h.method("post")
          ui.textField("email", "Email", placeholderText = "name@example.com")
          ui.button("Send invite") { h.tpe("submit") }
          h.text(" ")
          ui.button("Cancel", ui.Variant.Secondary)()
        }
      }
    }
  }

@main def demo(): Unit =
  print(h.renderString(demoPage))
