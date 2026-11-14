# keg-server

A to-do app served by [keg](https://github.com/jodersky/keg). Pages are built
on the server with qtags and the `ui` components from the
[components](../components) example. Forms post back to the server, so the app
works without any JavaScript.

## Run

```sh
./mill examples.keg-server.run
```

Then open http://localhost:8080.

## Routes

| Route | What it does |
|---|---|
| `GET /` | the to-do list, with a form to add a todo |
| `POST /todos` | adds a todo, then redirects to `/` |
| `POST /todos/:id/toggle` | marks a todo done or open, then redirects to `/` |
| `GET /big` | a table with 10,000 rows, streamed |

## How pages are sent

- `h.render(page)` renders the page into memory and returns a `geny.Writable`
  with the HTML content type and length set, which keg sends as the response
  body.
- `h.renderLazy(page)` streams the page to the client while it is rendered,
  without building it in memory first. `/big` uses it. keg then sends no
  `Content-Length`.

Todos are kept in memory, so they reset when the server restarts.

The code is all in [App.scala](src/App.scala).
