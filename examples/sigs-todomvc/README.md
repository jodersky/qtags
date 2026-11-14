# sigs-todomvc

[TodoMVC](https://todomvc.com) running in the browser, written with qtags'
DOM backend and the reactive signals of `qtags.sigs`. It is compiled with
Scala.js and uses the standard TodoMVC stylesheet.

## Run

```sh
./mill show examples.sigs-todomvc.site
```

This prints a directory containing `index.html` and the compiled `main.js`.
Open `index.html` in a browser. Todos are saved in the browser's
`localStorage`.

## Test

The tests drive the app through DOM events, as a user would, under
[jsdom](https://github.com/jsdom/jsdom). Install jsdom once, then run them:

```sh
(cd qtags/test/js && npm ci)
./mill examples.sigs-todomvc.test
```

## How it works

The state is a few signals: the list of todos, the current filter and the todo
being edited. The view is rendered once with `sigs.mount`. After that, each change
updates only the parts of the page that depend on it. The app imports
`qtags as h` and `qtags.sigs`, and writes every name with its prefix:

- `sigs.bindText`, `sigs.bindAttr` and `sigs.bindProp` keep a single text node, attribute or
  property equal to a signal.
- `sigs.dyn` re-renders a region when its signal changes, e.g. the footer appears
  only while there are todos.
- `sigs.each` keeps one `<li>` per todo, matched by id, so editing one todo doesn't
  re-render the others.

Elements are never replaced needlessly, so focus and cursor position are kept
while typing.

## Files

- [App.scala](src/todomvc/App.scala): the app, its state, view and storage.
- [index.html](index.html): the page that loads the app.
- [TodoAppTest.scala](test/src/TodoAppTest.scala): the tests.
