package sigs

import scala.collection.mutable
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/** A reactive value, in the style of the JavaScript signals proposal
  * (https://github.com/tc39/proposal-signals).
  *
  * {{{
  * val a = Signal.State(1)
  * val b = Signal.State(2)
  * val sum = Signal.Computed { a() + b() }   // depends on a and b
  * val w = Signal.Watcher(() => println(sum.now), sum)
  * a.set(10)                                 // prints 12
  * w.dispose()
  * }}}
  *
  * There are three kinds of nodes:
  *   - [[Signal.State]] holds a value that is set from outside.
  *   - [[Signal.Computed]] derives its value from other signals. It is lazy
  *     (computed when first read), cached, and only recomputed when one of the
  *     signals it read last time changed.
  *   - [[Signal.Watcher]] runs a callback when a signal's value changes. It is
  *     what keeps a part of the graph "live".
  *
  * Reading: `s.now` returns the current value. Inside a `Computed`, `s()`
  * returns it too and records `s` as a dependency. `s()` needs the computed as
  * a given, so it only compiles where dependencies can be tracked.
  *
  * Linking: a signal only holds references to its live consumers (watchers,
  * and computeds that are themselves watched). Changes are pushed along these
  * links: consumers are marked stale, and recomputed when next read. A
  * computed nobody watches is not linked; instead it compares its sources'
  * versions when read. So it holds no references from its sources and can be
  * garbage collected like any other object.
  *
  * Not thread-safe: use a signal graph from one thread at a time.
  */
sealed trait Signal[+A]:

  /** The current value, without recording a dependency. For a computed that
    * failed, rethrows its exception.
    */
  def now: A

  /** The current value, recorded as a dependency of the computed being
    * evaluated: when this signal changes, that computed is recomputed.
    */
  final def apply()(using c: Signal.Computed[?]): A = c.read(this)

  // ---- combinators ----------------------------------------------------------
  // Each returns a Computed: lazy, cached, and not propagating equal values.

  /** This signal's value transformed by `f`. */
  final def map[B](f: A => B): Signal[B] = Signal.Computed { f(this.apply()) }

  /** The value of the signal `f` returns for this signal's value. Changes when
    * either this signal or the current inner signal changes. `f` itself only
    * runs when this signal changes, so signals it creates are kept until then.
    */
  final def flatMap[B](f: A => Signal[B]): Signal[B] =
    val inner = map(f)
    Signal.Computed { inner()() }

  /** Both values, as a pair. */
  final def zip[B](that: Signal[B]): Signal[(A, B)] = Signal.Computed { (this.apply(), that()) }

  // ---- graph internals ------------------------------------------------------

  /** Incremented every time the value changes. */
  private[sigs] var version: Long = 0

  /** Live consumers, which get notified when this signal may have changed. */
  private[sigs] val consumers = mutable.ArrayBuffer.empty[Signal.Consumer]

  /** Brings the value up to date. */
  private[sigs] def refresh(): Unit

  private[sigs] final def link(c: Signal.Consumer): Unit =
    consumers += c
    if consumers.length == 1 then onFirstLink()

  private[sigs] final def unlink(c: Signal.Consumer): Unit =
    val i = consumers.indexWhere(_ eq c)
    if i >= 0 then
      consumers.remove(i)
      if consumers.isEmpty then onLastUnlink()

  protected def onFirstLink(): Unit = ()
  protected def onLastUnlink(): Unit = ()

object Signal:

  /** Something that is notified when a signal it depends on may have changed. */
  private[sigs] trait Consumer:
    private[sigs] def markStale(): Unit

  // ---- global state (see "Not thread-safe" above) ---------------------------

  /** Incremented whenever any State changes. Lets an unlinked computed skip
    * checking its sources when nothing at all has changed since last time.
    */
  private var globalVersion = 0L
  private var computingDepth = 0
  private var batchDepth = 0
  private val pendingWatchers = mutable.ArrayBuffer.empty[Watcher]

  /** Run `body`, and notify watchers once at the end instead of after each
    * `set`. Batches can be nested; watchers run when the outermost one ends.
    */
  def batch[T](body: => T): T =
    batchDepth += 1
    try body
    finally
      batchDepth -= 1
      if batchDepth == 0 then runWatchers()

  private def runWatchers(): Unit =
    // A watcher may set states, which queues more watchers. They are appended
    // to the list and handled by this same loop.
    batchDepth += 1
    try
      var i = 0
      while i < pendingWatchers.length do
        val w = pendingWatchers(i)
        w.queued = false
        i += 1
        w.check()
    finally
      pendingWatchers.foreach(_.queued = false)   // only left over after an exception
      pendingWatchers.clear()
      batchDepth -= 1

  // ---- State ----------------------------------------------------------------

  /** A value set from outside. `set` does nothing when the new value is equal
    * (by `isEqual`) to the current one.
    */
  final class State[A](initial: A, isEqual: (A, A) => Boolean = (a: A, b: A) => a == b)
      extends Signal[A]:
    private var value = initial

    def now: A = value

    def set(a: A): Unit =
      if computingDepth > 0 then
        throw IllegalStateException("a State cannot be set while a Computed is being evaluated")
      if !isEqual(value, a) then
        value = a
        version += 1
        globalVersion += 1
        batch { consumers.foreach(_.markStale()) }

    def update(f: A => A): Unit = set(f(value))

    private[sigs] def refresh(): Unit = ()

    override def toString = s"State($value)"

  // ---- Computed -------------------------------------------------------------

  /** A value derived from other signals. Inside `compute`, read signals with
    * `s()` to depend on them. An exception thrown by `compute` is cached like a
    * value, and rethrown on every read until a dependency changes.
    *
    * When the result is equal (by `isEqual`) to the previous one, the computed
    * counts as unchanged, and nothing that depends on it is recomputed.
    */
  final class Computed[A](compute: Computed[A] ?=> A, isEqual: (A, A) => Boolean = (a: A, b: A) => a == b)
      extends Signal[A], Consumer:

    private var result: Try[A] | Null = null           // null until first computed

    // Dependencies from the last evaluation, with the version seen of each.
    private var sources = mutable.ArrayBuffer.empty[Signal[?]]
    private var sourceVersions = mutable.ArrayBuffer.empty[Long]

    // Dependencies being recorded by the evaluation in progress.
    private var nextSources: mutable.ArrayBuffer[Signal[?]] = null
    private var nextVersions: mutable.ArrayBuffer[Long] = null
    private var computing = false

    /** Whether this computed is linked to its sources (because it is watched). */
    private var linked = false
    /** When linked: whether a source may have changed since the last refresh. */
    private var stale = true
    /** When not linked: globalVersion at the last refresh. */
    private var checkedAt = -1L

    def now: A =
      refresh()
      result.nn.get

    private[sigs] def read[B](s: Signal[B]): B =
      if !computing then
        throw IllegalStateException("a signal was read with () outside the evaluation of its Computed; use .now")
      s.refresh()
      if !nextSources.exists(_ eq s) then
        nextSources += s
        nextVersions += s.version
      s.now

    private[sigs] def refresh(): Unit =
      if computing then throw IllegalStateException("cycle: a Computed depends on itself")
      val upToDate =
        result != null && (if linked then !stale else checkedAt == globalVersion)
      if !upToDate then
        if result == null || sourcesChanged() then recompute()
        stale = false
        checkedAt = globalVersion

    /** Whether any source has a new version. Sources are checked in the order
      * they were read, so one that is no longer needed is not recomputed.
      */
    private def sourcesChanged(): Boolean =
      var i = 0
      while i < sources.length do
        val s = sources(i)
        s.refresh()
        if s.version != sourceVersions(i) then return true
        i += 1
      false

    private def recompute(): Unit =
      val oldSources = sources
      nextSources = mutable.ArrayBuffer.empty
      nextVersions = mutable.ArrayBuffer.empty
      computing = true
      computingDepth += 1
      val next =
        try Success(compute(using this))
        catch case NonFatal(e) => Failure(e)
        finally
          computing = false
          computingDepth -= 1
      sources = nextSources
      sourceVersions = nextVersions
      nextSources = null
      nextVersions = null

      if linked then
        for s <- sources if !oldSources.exists(_ eq s) do s.link(this)
        for s <- oldSources if !sources.exists(_ eq s) do s.unlink(this)

      val changed = (result, next) match
        case (Success(a), Success(b)) => !isEqual(a.asInstanceOf[A], b)
        case _                        => true    // first value, or a failure
      if changed then
        result = next
        version += 1

    private[sigs] def markStale(): Unit =
      if !stale then
        stale = true
        consumers.foreach(_.markStale())

    // Becoming watched: bring the value up to date, then link to the sources so
    // that they push changes from now on.
    override protected def onFirstLink(): Unit =
      refresh()
      sources.foreach(_.link(this))
      linked = true
      stale = false

    // No longer watched: unlink, so the sources no longer reference this.
    override protected def onLastUnlink(): Unit =
      sources.foreach(_.unlink(this))
      linked = false
      stale = true

    override def toString = result match
      case null => "Computed(<not computed>)"
      case r    => s"Computed(${r.fold(e => s"<failed: $e>", _.toString)})"

  // ---- Watcher --------------------------------------------------------------

  /** Calls `fn` each time the value of `upstream` changes, until disposed.
    *
    * `fn` runs after the change has been fully propagated (at the end of the
    * outermost `set` or `batch`), so it may read any signal, and may set
    * states. It runs once per change, however many paths lead from the
    * changed states to `upstream`, and not at all when `upstream` is
    * recomputed to an equal value. It does not run when the watcher is
    * created.
    *
    * While a watcher exists, `upstream` and everything it depends on are
    * linked (live). Call `dispose` to unlink them.
    */
  final class Watcher(val fn: () => Unit, private[sigs] val upstream: Signal[?]) extends Consumer:
    private var active = true
    private[sigs] var queued = false

    upstream.link(this)
    upstream.refresh()
    private var seenVersion = upstream.version

    private[sigs] def markStale(): Unit =
      if active && !queued then
        queued = true
        pendingWatchers += this

    private[sigs] def check(): Unit =
      if active then
        upstream.refresh()
        if upstream.version != seenVersion then
          seenVersion = upstream.version
          fn()

    def isActive: Boolean = active

    /** Stop watching, and unlink `upstream` if nothing else watches it. */
    def dispose(): Unit =
      if active then
        active = false
        upstream.unlink(this)
