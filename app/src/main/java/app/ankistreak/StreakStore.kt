package app.ankistreak

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime

/** All of the app's own data. Stored only on the phone, in this app. */
class StreakStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("streak", Context.MODE_PRIVATE)

    private fun rawDone(): Set<String> = prefs.getStringSet(KEY_DONE, emptySet()).orEmpty()

    fun doneDays(): Set<LocalDate> =
        rawDone().mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()

    fun isDone(day: LocalDate): Boolean = rawDone().contains(day.toString())

    fun setDone(day: LocalDate, done: Boolean) {
        val set = HashSet(rawDone()) // copy: never mutate the set SharedPreferences returns
        if (done) set.add(day.toString()) else set.remove(day.toString())
        prefs.edit().putStringSet(KEY_DONE, set).commit()
    }

    /** Deck ids that count for the streak. Empty = all decks. */
    var trackedDecks: Set<Long>
        get() = prefs.getStringSet(KEY_DECKS, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
        set(value) {
            prefs.edit().putStringSet(KEY_DECKS, value.map { it.toString() }.toSet()).commit()
        }

    /** Hour the new day starts. Anki's default is 4 AM, so 1 AM still counts for "yesterday". */
    var rolloverHour: Int
        get() = prefs.getInt(KEY_ROLLOVER, 4)
        set(value) { prefs.edit().putInt(KEY_ROLLOVER, value).commit() }

    /** Reminder time in minutes after midnight, or -1 when off. */
    var reminderMinutes: Int
        get() = prefs.getInt(KEY_REMINDER, 20 * 60)
        set(value) { prefs.edit().putInt(KEY_REMINDER, value).commit() }

    var ignoreNew: Boolean
        get() = prefs.getBoolean(KEY_IGNORE_NEW, false)
        set(value) { prefs.edit().putBoolean(KEY_IGNORE_NEW, value).commit() }

    var lastRemindedDay: String?
        get() = prefs.getString(KEY_REMINDED, null)
        set(value) { prefs.edit().putString(KEY_REMINDED, value).commit() }

    var askedPermission: Boolean
        get() = prefs.getBoolean(KEY_ASKED, false)
        set(value) { prefs.edit().putBoolean(KEY_ASKED, value).commit() }

    var askedNotifications: Boolean
        get() = prefs.getBoolean(KEY_ASKED_NOTIF, false)
        set(value) { prefs.edit().putBoolean(KEY_ASKED_NOTIF, value).commit() }

    /** The current "Anki day", which starts at [rolloverHour] instead of midnight. */
    fun today(now: LocalDateTime = LocalDateTime.now()): LocalDate =
        now.minusHours(rolloverHour.toLong()).toLocalDate()

    /**
     * Consecutive done days ending today. If today isn't done yet the streak
     * is still alive (counted up to yesterday) — like Duolingo.
     */
    fun currentStreak(today: LocalDate = today()): Int {
        val days = doneDays()
        var d = if (today in days) today else today.minusDays(1)
        var n = 0
        while (d in days) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    fun longestStreak(): Int {
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in doneDays().sorted()) {
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            if (run > best) best = run
            prev = d
        }
        return best
    }

    private companion object {
        const val KEY_DONE = "done_days"
        const val KEY_DECKS = "tracked_decks"
        const val KEY_ROLLOVER = "rollover_hour"
        const val KEY_REMINDER = "reminder_minutes"
        const val KEY_IGNORE_NEW = "ignore_new"
        const val KEY_REMINDED = "last_reminded_day"
        const val KEY_ASKED = "asked_permission"
        const val KEY_ASKED_NOTIF = "asked_notifications"
    }
}
