package qtags.sigs

import scala.collection.mutable
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/** Reactive values for single-threaded programs such as GUIs, in the style of
  * the JavaScript signals proposal (https://github.com/tc39/proposal-signals).
  *
  * ```scala
  * val count = Signal.State(1)
  * val label = Signal.Computed { s"${count()} items" }
  * Signal.observe { println(label()) }    // prints "1 items" now, and on each change
  * count.set(2)                           // prints "2 items"
  * ```
  *
  * Three primitives:
  *   - [[Signal.State]] holds a value that is set from outside.
  *   - [[Signal.Computed]] derives a value from other signals. It is lazy
  *     (computed when first read), cached, and only recomputed when one of the
  *     signals it read last time changed.
  *   - [[Signal.observe]] runs a side effect now, and again whenever a signal it
  *     read changes.
  *
  * Reading: `s.now` returns the current value. Inside a `Computed` or
  * `observe` block, `s()` returns it too and records `s` as a dependency. `s()`
  * only compiles inside such a block.
  *
  * Ownership: observers and scopes belong to whatever is running when they
  * are created (a [[Signal.Scope]], or the current run of an observer), and are
  * disposed with it. Before an observer runs again, what its previous run
  * created is disposed. So nested reactions are cleaned up automatically; only
  * top-level ones need an explicit `dispose`.
  *
  * No leaks: a signal only holds references to what currently depends on it
  * (observers, and computeds that observers depend on). A computed nobody
  * observes is not referenced by its sources, and is garbage collected like
  * any other object.
  *
  * Not thread-safe: use a signal graph from one thread at a time.
  */
sealed trait Signal[+A]:

  /** The current value, without recording a dependency. For a computed that
    * failed, rethrows its exception.
    */
  def now: A

  /** The current value, recorded as a dependency of the `Computed` or
    * `observe` block being evaluated: when this signal changes, that block runs
    * again.
    */
  final def apply()(using t: Signal.Tracker): A = t.read(this)

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

  /** What currently depends on this signal, and is notified when it may have
    * changed.
    */
  private[sigs] val consumers = mutable.ArrayBuffer.empty[Signal.Tracker]

  /** Brings the value up to date. */
  private[sigs] def refresh(): Unit

  private[sigs] final def link(c: Signal.Tracker): Unit =
    consumers += c
    if consumers.length == 1 then onFirstLink()

  private[sigs] final def unlink(c: Signal.Tracker): Unit =
    val i = consumers.indexWhere(_ eq c)
    if i >= 0 then
      consumers.remove(i)
      if consumers.isEmpty then onLastUnlink()

  protected def onFirstLink(): Unit = ()
  protected def onLastUnlink(): Unit = ()

object Signal:

  // ---- global state (see "Not thread-safe" above) ---------------------------

  /** Incremented whenever any State changes. Lets an unobserved computed skip
    * checking its sources when nothing at all has changed since last time.
    */
  private var globalVersion = 0L
  private var computingDepth = 0
  private var batchDepth = 0
  private val pending = mutable.ArrayBuffer.empty[Observer]
  private var current: Scope | Null = null

  /** Run `body`, and run observers once at the end instead of after each
    * `set`. Batches can be nested; observers run when the outermost one ends.
    */
  def batch[T](body: => T): T =
    batchDepth += 1
    try body
    finally
      batchDepth -= 1
      if batchDepth == 0 then runPending()

  private def runPending(): Unit =
    // An observer may set states, which queues more observers. They are
    // appended to the list and handled by this same loop.
    batchDepth += 1
    try
      var i = 0
      while i < pending.length do
        val o = pending(i)
        o.queued = false
        i += 1
        o.check()
    finally
      pending.foreach(_.queued = false)   // only left over after an exception
      pending.clear()
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

  // ---- tracking -------------------------------------------------------------

  /** What `s()` needs: the `Computed` or `observe` block being evaluated, which
    * records the signals it reads. Provided by those blocks; there is no need
    * to create or name one.
    */
  sealed abstract class Tracker:
    // Dependencies from the last evaluation, with the version seen of each.
    private var sources = mutable.ArrayBuffer.empty[Signal[?]]
    private var sourceVersions = mutable.ArrayBuffer.empty[Long]
    // Dependencies being recorded by the evaluation in progress.
    private var nextSources: mutable.ArrayBuffer[Signal[?]] = null
    private var nextVersions: mutable.ArrayBuffer[Long] = null

    protected var tracking = false
    /** Whether this is linked to its sources, so that they notify it. */
    protected var linked = false

    /** Called when a source may have changed. */
    private[sigs] def markStale(): Unit

    private[sigs] def read[B](s: Signal[B]): B =
      if !tracking then
        throw IllegalStateException(
          "a signal was read with () outside of its Computed or observe block; use .now"
        )
      s.refresh()
      if !nextSources.exists(_ eq s) then
        nextSources += s
        nextVersions += s.version
      s.now

    /** Runs `body` while recording the signals it reads as the new sources,
      * and links or unlinks sources that were added or dropped.
      */
    protected def track[T](body: => T): T =
      val oldSources = sources
      nextSources = mutable.ArrayBuffer.empty
      nextVersions = mutable.ArrayBuffer.empty
      tracking = true
      try body
      finally
        tracking = false
        sources = nextSources
        sourceVersions = nextVersions
        nextSources = null
        nextVersions = null
        if linked then
          for s <- sources if !oldSources.exists(_ eq s) do s.link(this)
          for s <- oldSources if !sources.exists(_ eq s) do s.unlink(this)

    /** Whether any source has a new version. Sources are checked in the order
      * they were read, so one that is no longer needed is not recomputed.
      */
    protected def sourcesChanged(): Boolean =
      var i = 0
      while i < sources.length do
        val s = sources(i)
        s.refresh()
        if s.version != sourceVersions(i) then return true
        i += 1
      false

    protected def linkSources(): Unit =
      sources.foreach(_.link(this))
      linked = true

    protected def unlinkSources(): Unit =
      sources.foreach(_.unlink(this))
      linked = false

  // ---- Computed -------------------------------------------------------------

  /** A value derived from other signals. Inside `compute`, read signals with
    * `s()` to depend on them. An exception thrown by `compute` is cached like a
    * value, and rethrown on every read until a dependency changes.
    *
    * When the result is equal (by `isEqual`) to the previous one, the computed
    * counts as unchanged, and nothing that depends on it runs again.
    *
    * `compute` should only compute: it may run at any time, or not at all, so
    * it must not set states or observe.
    */
  final class Computed[A](compute: Computed[A] ?=> A, isEqual: (A, A) => Boolean = (a: A, b: A) => a == b)
      extends Tracker, Signal[A]:

    private var result: Try[A] | Null = null    // null until first computed
    /** When linked: whether a source may have changed since the last refresh. */
    private var stale = true
    /** When not linked: globalVersion at the last refresh. */
    private var checkedAt = -1L

    def now: A =
      refresh()
      result.nn.get

    private[sigs] def refresh(): Unit =
      if tracking then throw IllegalStateException("cycle: a Computed depends on itself")
      val upToDate =
        result != null && (if linked then !stale else checkedAt == globalVersion)
      if !upToDate then
        if result == null || sourcesChanged() then recompute()
        stale = false
        checkedAt = globalVersion

    private def recompute(): Unit =
      computingDepth += 1
      val next =
        try Success(track(compute(using this)))
        catch case NonFatal(e) => Failure(e)
        finally computingDepth -= 1
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

    // Now depended on: bring the value up to date, then link to the sources so
    // that they notify this from now on.
    override protected def onFirstLink(): Unit =
      refresh()
      linkSources()
      stale = false

    // No longer depended on: unlink, so the sources no longer reference this.
    override protected def onLastUnlink(): Unit =
      unlinkSources()
      stale = true

    override def toString = result match
      case null => "Computed(<not computed>)"
      case r    => s"Computed(${r.fold(e => s"<failed: $e>", _.toString)})"

  // ---- Scope ----------------------------------------------------------------

  /** A lifetime: collects the observers and scopes created while it runs, and
    * disposes them, along with any `onDispose` actions, when it is disposed.
    */
  final class Scope private[sigs] ():
    private val owned = mutable.ArrayBuffer.empty[() => Unit]
    private var disposed = false

    /** Run more code inside this scope, so that what it creates belongs here. */
    def run[T](body: => T): T =
      if disposed then throw IllegalStateException("this scope has been disposed")
      val previous = current
      current = this
      try body
      finally current = previous

    /** Register a clean-up action, run when this scope is disposed. */
    def onDispose(cleanup: () => Unit): Unit =
      if disposed then cleanup() else owned += cleanup

    def isDisposed: Boolean = disposed

    /** Dispose what belongs to this scope, most recently created first. */
    def dispose(): Unit =
      if !disposed then
        disposed = true
        val all = owned.reverse
        owned.clear()
        all.foreach(_())

  /** The scope code is currently running in, if any. */
  def currentScope: Option[Scope] = Option(current)

  /** A scope that belongs to the current one, if any. */
  private def childScope(): Scope =
    val s = Scope()
    if current != null then current.nn.onDispose(() => s.dispose())
    s

  /** A new scope, belonging to the current one (if any), that runs `body`. */
  def scope(body: => Unit): Scope =
    val s = childScope()
    s.run(body)
    s

  // ---- observe --------------------------------------------------------------

  /** Runs `body` now, and again whenever a signal it read with `s()` changes,
    * until the returned scope is disposed. The returned scope belongs to the
    * current one, if any.
    *
    * Re-runs happen after a change has fully propagated (at the end of the
    * outermost `set` or `batch`), at most once per change, and only if a
    * signal `body` read actually changed value. Each run executes in a fresh
    * scope, disposed before the next run, so observers and scopes created by
    * one run are cleaned up before the next. `body` may set states.
    */
  def observe(body: Tracker ?=> Unit): Scope =
    val handle = childScope()
    val o = Observer(body)
    handle.onDispose(() => o.dispose())
    o.start()
    handle

  private final class Observer(body: Tracker ?=> Unit) extends Tracker:
    private var active = true
    private[sigs] var queued = false
    private var runScope: Scope | Null = null

    def start(): Unit =
      linked = true
      run()

    private def run(): Unit =
      if runScope != null then runScope.nn.dispose()
      val s = Scope()                   // belongs to this observer, not to `current`
      runScope = s
      s.run(track(body(using this)))

    private[sigs] def markStale(): Unit =
      if active && !queued then
        queued = true
        pending += this

    def check(): Unit =
      if active && sourcesChanged() then run()

    def dispose(): Unit =
      if active then
        active = false
        if runScope != null then runScope.nn.dispose()
        unlinkSources()
