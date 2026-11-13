package sigs

import utest.*

object SignalTest extends TestSuite:
  import Signal.{Computed, State, Watcher}

  /** A computed that counts its evaluations. */
  class Counted[A](f: Computed[A] ?=> A):
    var runs = 0
    val sig = Computed[A] { runs += 1; f }

  /** A watcher that records the upstream value each time it fires. */
  def record[A](s: Signal[A]): (Watcher, collection.mutable.Buffer[A]) =
    val seen = collection.mutable.Buffer.empty[A]
    (Watcher(() => seen += s.now, s), seen)

  val tests = Tests:

    test("state"):
      val s = State(1)
      assert(s.now == 1)
      s.set(2)
      s.update(_ * 10)
      assert(s.now == 20)

    test("computed is lazy and cached"):
      val a = State(1)
      val c = Counted { a() * 2 }
      assert(c.runs == 0)
      assert(c.sig.now == 2, c.sig.now == 2)
      assert(c.runs == 1)
      a.set(5)
      assert(c.runs == 1)                      // not watched: nothing runs until read
      assert(c.sig.now == 10, c.runs == 2)

    test("unwatched computed is not linked to its sources"):
      val a = State(1)
      val c = Computed { a() + 1 }
      assert(c.now == 2)
      assert(a.consumers.isEmpty)

    test("diamond: computed once per change, never sees a mix of old and new"):
      val a = State(1)
      val b = Computed { a() + 1 }
      val c = Computed { a() * 10 }
      val d = Counted { (b(), c()) }
      val (_, seen) = record(d.sig)
      assert(d.runs == 1)
      a.set(2)
      assert(d.runs == 2, seen == Seq((3, 20)))

    test("dynamic dependencies"):
      val flag = State(true)
      val a = State("a")
      val b = State("b")
      val c = Counted { if flag() then a() else b() }
      val (w, seen) = record(c.sig)
      b.set("b2")                              // not a dependency yet
      assert(c.runs == 1, seen.isEmpty, b.consumers.isEmpty)
      flag.set(false)
      assert(seen == Seq("b2"), a.consumers.isEmpty, b.consumers.length == 1)
      a.set("a2")                              // no longer a dependency
      assert(c.runs == 2, seen == Seq("b2"))

    test("equal values stop propagation"):
      val a = State(1)
      val parity = Computed { a() % 2 }
      val label = Counted { if parity() == 0 then "even" else "odd" }
      val (_, seen) = record(label.sig)
      a.set(3)                                 // parity unchanged: label not recomputed
      assert(label.runs == 1, seen.isEmpty)
      a.set(4)
      assert(label.runs == 2, seen == Seq("even"))
      a.set(4)                                 // equal State value: nothing at all
      assert(label.runs == 2, seen == Seq("even"))

    test("custom equality"):
      val s = State("a", isEqual = _.equalsIgnoreCase(_))
      val (_, seen) = record(s)
      s.set("A")
      assert(s.now == "a", seen.isEmpty)

    test("watcher: fires after each change, can read the new value"):
      val a = State(1)
      val doubled = Computed { a() * 2 }
      val (w, seen) = record(doubled)
      assert(seen.isEmpty)                     // not on creation
      a.set(2)
      a.set(3)
      assert(seen == Seq(4, 6))

    test("watcher: dispose unlinks the whole chain"):
      val a = State(1)
      val b = Computed { a() + 1 }
      val c = Computed { b() + 1 }
      val (w, seen) = record(c)
      assert(a.consumers.length == 1, b.consumers.length == 1, c.consumers.length == 1)
      w.dispose()
      assert(a.consumers.isEmpty, b.consumers.isEmpty, c.consumers.isEmpty)
      a.set(5)
      assert(seen.isEmpty, c.now == 7)         // still correct when read

    test("watcher: shared upstream stays linked until the last watcher goes"):
      val a = State(1)
      val c = Computed { a() + 1 }
      val (w1, _) = record(c)
      val (w2, seen2) = record(c)
      w1.dispose()
      a.set(2)
      assert(seen2 == Seq(3), a.consumers.length == 1)
      w2.dispose()
      assert(a.consumers.isEmpty)

    test("watcher can set state; the follow-up change is delivered too"):
      val a = State(0)
      val b = State(0)
      Watcher(() => b.set(a.now * 10), a)
      val (_, seenB) = record(b)
      a.set(1)
      a.set(2)
      assert(b.now == 20, seenB == Seq(10, 20))

    test("batch: one notification for several changes"):
      val a = State(1)
      val b = State(2)
      val sum = Computed { a() + b() }
      val (_, seen) = record(sum)
      Signal.batch {
        a.set(10)
        b.set(20)
      }
      assert(seen == Seq(30))

    test("exceptions are cached and rethrown until a dependency changes"):
      val a = State(0)
      val c = Counted { 10 / a() }
      val e1 = assertThrows[ArithmeticException](c.sig.now)
      assertThrows[ArithmeticException](c.sig.now)
      assert(c.runs == 1)
      a.set(5)
      assert(c.sig.now == 2)

    test("watched computed recovers from failure"):
      val a = State(0)
      val c = Computed { 10 / a() }
      var fired = 0
      Watcher(() => fired += 1, c)
      a.set(2)
      assert(fired == 1, c.now == 5)

    test("cycles are detected"):
      lazy val c: Computed[Int] = Computed { c() + 1 }
      val e = assertThrows[IllegalStateException](c.now)
      assert(e.getMessage.contains("cycle"))

    test("setting a state inside a computed fails"):
      val a = State(0)
      val c = Computed { a.set(1); 1 }
      assertThrows[IllegalStateException](c.now)
      assert(a.now == 0)

    test("tracked read outside evaluation fails"):
      val a = State(0)
      var escaped: Computed[?] = null
      val c = Computed { escaped = summon[Computed[Int]]; 1 }
      c.now
      assertThrows[IllegalStateException](a()(using escaped))

    test("now inside a computed does not create a dependency"):
      val a = State(1)
      val b = State(1)
      val c = Counted { a() + b.now }
      Watcher(() => (), c.sig)
      b.set(2)
      assert(c.runs == 1, c.sig.now == 2)      // stale value of b, by design
      a.set(2)
      assert(c.runs == 2, c.sig.now == 4)

    test("map: lazy, cached, cut off on equal results"):
      val a = State(1)
      var runs = 0
      val parity = a.map { x => runs += 1; x % 2 }
      assert(runs == 0)
      val (_, seen) = record(parity)
      a.set(3)                                 // recomputed, but the result is equal
      a.set(4)
      assert(runs == 3, seen == Seq(0))

    test("flatMap: follows the current inner signal"):
      val useA = State(true)
      val a = State("a")
      val b = State("b")
      var calls = 0
      val s = useA.flatMap { u => calls += 1; if u then a else b }
      val (_, seen) = record(s)
      a.set("a2")                              // inner change: f is not called again
      assert(seen == Seq("a2"), calls == 1)
      useA.set(false)
      assert(seen == Seq("a2", "b"), calls == 2)
      a.set("a3")                              // old inner: no longer a dependency
      assert(seen == Seq("a2", "b"), a.consumers.isEmpty)

    test("flatMap: signals created by f survive inner changes"):
      val user = State("ann")
      var created = Seq.empty[State[String]]
      val draft = user.flatMap { _ =>             // a fresh draft per user
        val s = State("")
        created :+= s
        s
      }
      record(draft)
      created.head.set("hello")                   // inner change
      assert(draft.now == "hello", created.length == 1)
      user.set("bob")                             // outer change: new draft
      assert(draft.now == "", created.length == 2)

    test("for-comprehension and zip"):
      val a = State(1)
      val b = State(10)
      val sum = for x <- a; y <- b yield x + y
      val pair = a.zip(b)
      val (_, seenSum) = record(sum)
      val (_, seenPair) = record(pair)
      b.set(20)
      a.set(2)
      assert(seenSum == Seq(21, 22), seenPair == Seq((1, 20), (2, 20)))

