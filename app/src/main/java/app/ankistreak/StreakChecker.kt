package app.ankistreak

import android.content.Context
import java.time.LocalTime

/**
 * The heart of the app: asks AnkiDroid how many cards are left today.
 * When none are left, today counts toward the streak.
 */
object StreakChecker {
    enum class State { NO_ANKI, NO_PERMISSION, ERROR, PENDING, DONE }

    data class Result(
        val state: State,
        val learn: Int = 0,
        val review: Int = 0,
        val new: Int = 0,
        val remaining: Int = 0,
        val justCompleted: Boolean = false,
        val trackedNames: List<String> = emptyList(),
    )

    /** [fromUi] = the app is open on screen, so show feedback there instead of notifications. */
    @Synchronized
    fun check(context: Context, fromUi: Boolean): Result {
        val ctx = context.applicationContext
        val store = StreakStore(ctx)
        val result = compute(ctx, store)
        if (!fromUi) maybeNotify(ctx, store, result)
        if (result.justCompleted) Notifications.cancelReminder(ctx)
        StreakWidget.updateAll(ctx)
        return result
    }

    private fun compute(ctx: Context, store: StreakStore): Result {
        if (!AnkiBridge.isInstalled(ctx)) return Result(State.NO_ANKI)
        if (!AnkiBridge.hasPermission(ctx)) return Result(State.NO_PERMISSION)
        val decks = AnkiBridge.readDecks(ctx) ?: return Result(State.ERROR)

        val relevant = relevantDecks(decks, store.trackedDecks)
        val learn = relevant.sumOf { it.learn }
        val review = relevant.sumOf { it.review }
        val new = relevant.sumOf { it.new }
        val remaining = learn + review + if (store.ignoreNew) 0 else new

        val today = store.today()
        var justCompleted = false
        if (remaining == 0 && !store.isDone(today)) {
            store.setDone(today, true)
            justCompleted = true
        }
        val state = if (store.isDone(today)) State.DONE else State.PENDING
        return Result(state, learn, review, new, remaining, justCompleted, relevant.map { it.name })
    }

    /** Chosen decks, or all top-level decks. Subdecks of a chosen deck are dropped so nothing is counted twice. */
    fun relevantDecks(all: List<AnkiBridge.Deck>, tracked: Set<Long>): List<AnkiBridge.Deck> {
        val chosen = all.filter { it.id in tracked }
        val base = chosen.ifEmpty { all.filter { !it.name.contains("::") } }
        val names = base.map { it.name }
        return base.filter { d -> names.none { parent -> d.name.startsWith("$parent::") } }
    }

    private fun maybeNotify(ctx: Context, store: StreakStore, r: Result) {
        val today = store.today()
        if (r.justCompleted) {
            Notifications.celebrate(ctx, store.currentStreak(today))
            return
        }
        if (r.state != State.PENDING) return
        val reminderAt = store.reminderMinutes
        if (reminderAt < 0) return
        val now = LocalTime.now()
        if (now.hour * 60 + now.minute < reminderAt) return
        if (store.lastRemindedDay == today.toString()) return
        Notifications.remind(ctx, store.currentStreak(today), r.remaining)
        store.lastRemindedDay = today.toString()
    }
}
