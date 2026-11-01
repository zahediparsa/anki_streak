package app.ankistreak

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import app.ankistreak.StreakChecker.State
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var store: StreakStore
    private lateinit var content: LinearLayout
    private var checking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = StreakStore(this)
        Notifications.createChannels(this)
        Scheduler.ensureScheduled(this)
        setupSystemBars()

        val scroll = ScrollView(this).apply {
            setBackgroundColor(BG)
            isFillViewport = true
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
        }
        scroll.addView(content, ViewGroup.LayoutParams(MATCH, WRAP))
        if (Build.VERSION.SDK_INT >= 35) {
            // Android 15 draws apps edge-to-edge: keep content clear of the status/navigation bars.
            scroll.setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        if (checking) return
        checking = true
        Thread {
            val result = StreakChecker.check(applicationContext, fromUi = true)
            runOnUiThread {
                checking = false
                if (!isFinishing && !isDestroyed) render(result)
            }
        }.start()
    }

    // ---------------------------------------------------------------- screens

    private fun render(r: StreakChecker.Result) {
        content.removeAllViews()
        content.addView(text("ANKI STREAK", 13f, MUTED, bold = true).apply { letterSpacing = 0.15f })
        when (r.state) {
            State.NO_ANKI -> {
                flame(done = false)
                message("AnkiDroid isn't installed", "Install AnkiDroid first, then come back here.")
                content.addView(button("Get AnkiDroid") { openStore() }, spaced(16))
            }
            State.NO_PERMISSION -> {
                flame(done = false)
                message(
                    "Connect to AnkiDroid",
                    "Anki Streak needs permission to read how many cards you have left today. " +
                        "It only reads deck counts and never changes your cards."
                )
                content.addView(button("Allow access") { askPermissions() }, spaced(16))
            }
            State.ERROR -> {
                flame(done = false)
                message(
                    "Can't read AnkiDroid right now",
                    "Open AnkiDroid once, then come back. Also make sure \"Enable AnkiDroid API\" " +
                        "is turned on in AnkiDroid's settings (under Advanced)."
                )
                content.addView(button("Try again") { refresh() }, spaced(16))
                content.addView(button("Open AnkiDroid", primary = false) { openAnki() }, spaced(10))
            }
            State.PENDING, State.DONE -> renderStreak(r)
        }
    }

    private fun renderStreak(r: StreakChecker.Result) {
        val today = store.today()
        val streak = store.currentStreak(today)
        val done = r.state == State.DONE

        val flameView = flame(done)
        content.addView(text("$streak", 64f, if (done) ORANGE else DARK, bold = true))
        content.addView(text("day streak", 18f, MUTED, bold = true))

        if (r.justCompleted) {
            flameView.scaleX = 0.4f
            flameView.scaleY = 0.4f
            flameView.animate().scaleX(1f).scaleY(1f)
                .setInterpolator(OvershootInterpolator(3f)).setDuration(700).start()
            Toast.makeText(
                this, if (streak <= 1) "🔥 Streak started!" else "🔥 $streak days in a row!", Toast.LENGTH_LONG
            ).show()
        }

        // Today's status
        val card = card()
        if (done) {
            card.addView(text("✅  Done for today", 18f, GREEN, bold = true))
            card.addView(text("Your streak is safe. Bis morgen!", 15f, DARK), spaced(4))
        } else {
            val cards = if (r.remaining == 1) "1 card" else "${r.remaining} cards"
            card.addView(text("$cards left today", 18f, DARK, bold = true))
            val newPart = if (store.ignoreNew) "new ignored" else "${r.new} new"
            card.addView(text("$newPart · ${r.learn} learning · ${r.review} review", 14f, MUTED), spaced(4))
            val goal = if (streak > 0) "Finish them in AnkiDroid to keep your $streak-day streak."
            else "Finish them in AnkiDroid to start a streak."
            card.addView(text(goal, 15f, DARK), spaced(8))
        }
        content.addView(card, spaced(20))
        content.addView(button("Open AnkiDroid") { openAnki() }, spaced(14))

        // Last 7 days
        content.addView(sectionTitle("Last 7 days"))
        content.addView(weekRow(today), spaced(8))
        content.addView(
            text("Longest streak: ${store.longestStreak()}  ·  Study days: ${store.doneDays().size}", 14f, MUTED),
            spaced(12)
        )
        content.addView(text("Missed one by mistake? Long-press a day to fix it.", 12f, MUTED), spaced(2))

        // Settings
        content.addView(sectionTitle("Settings"))
        content.addView(settingRow("Decks that count", deckSummary(r)) { pickDecks() })
        content.addView(
            settingRow("New day starts at", "%02d:00 — keep this the same as Anki (default 04:00)".format(store.rolloverHour)) {
                pickRollover()
            }
        )
        content.addView(settingRow("Daily reminder", reminderSummary()) { pickReminder() })
        content.addView(switchRow("Ignore new cards", "Only reviews and learning cards must be finished", store.ignoreNew) {
            store.ignoreNew = it
            refresh()
        })
        if (!Notifications.canNotify(this)) {
            content.addView(settingRow("Notifications are off", "Tap to allow streak and reminder notifications") {
                askNotifications()
            })
        }
        content.addView(
            text(
                "Checks AnkiDroid in the background about every 15 minutes, and right away whenever you open this app.",
                12f, MUTED
            ).apply { gravity = Gravity.CENTER },
            spaced(24)
        )
    }

    private fun flame(done: Boolean): ImageView {
        val v = ImageView(this).apply {
            setImageResource(if (done) R.drawable.ic_flame else R.drawable.ic_flame_gray)
        }
        content.addView(v, LinearLayout.LayoutParams(dp(130), dp(130)).apply { topMargin = dp(20) })
        return v
    }

    private fun message(title: String, body: String) {
        content.addView(text(title, 22f, DARK, bold = true).apply { gravity = Gravity.CENTER }, spaced(16))
        content.addView(text(body, 15f, MUTED).apply { gravity = Gravity.CENTER }, spaced(8))
    }

    private fun weekRow(today: LocalDate): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val done = store.doneDays()
        for (i in 6 downTo 0) {
            val day = today.minusDays(i.toLong())
            val isDone = day in done
            val isToday = i == 0
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            col.addView(
                text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), 12f,
                    if (isToday) ORANGE else MUTED, bold = true)
            )
            val circle = TextView(this).apply {
                text = if (isDone) "🔥" else day.dayOfMonth.toString()
                textSize = if (isDone) 16f else 13f
                gravity = Gravity.CENTER
                setTextColor(if (isToday) ORANGE else MUTED)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (isDone) ORANGE_LIGHT else LIGHT)
                    if (isToday) setStroke(dp(2), ORANGE)
                }
                setOnLongClickListener { confirmToggle(day); true }
            }
            col.addView(circle, LinearLayout.LayoutParams(dp(38), dp(38)).apply { topMargin = dp(4) })
            row.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        return row
    }

    // ---------------------------------------------------------------- actions

    private fun confirmToggle(day: LocalDate) {
        val label = day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))
        val done = store.isDone(day)
        AlertDialog.Builder(this)
            .setTitle(label)
            .setMessage(if (done) "Remove this day from your streak?" else "Count this day as studied?")
            .setPositiveButton(if (done) "Remove" else "Mark as studied") { _, _ ->
                store.setDone(day, !done)
                StreakWidget.updateAll(this)
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deckSummary(r: StreakChecker.Result): String =
        if (store.trackedDecks.isEmpty()) "All decks"
        else r.trackedNames.joinToString(", ").ifEmpty { "All decks" }

    private fun pickDecks() {
        Thread {
            val decks = AnkiBridge.readDecks(applicationContext).orEmpty().sortedBy { it.name.lowercase() }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (decks.isEmpty()) {
                    Toast.makeText(this, "Couldn't load your decks from AnkiDroid", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                val tracked = store.trackedDecks
                val names = decks.map { it.name.replace("::", " › ") }.toTypedArray()
                val checked = BooleanArray(decks.size) { decks[it].id in tracked }
                AlertDialog.Builder(this)
                    .setTitle("Which decks must be finished?")
                    .setMultiChoiceItems(names, checked) { _, which, isChecked -> checked[which] = isChecked }
                    .setPositiveButton("Save") { _, _ ->
                        store.trackedDecks = decks.filterIndexed { i, _ -> checked[i] }.map { it.id }.toSet()
                        refresh()
                    }
                    .setNeutralButton("All decks") { _, _ ->
                        store.trackedDecks = emptySet()
                        refresh()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }.start()
    }

    private fun pickRollover() {
        val hours = Array(24) { "%02d:00".format(it) }
        AlertDialog.Builder(this)
            .setTitle("New day starts at")
            .setSingleChoiceItems(hours, store.rolloverHour) { dialog, which ->
                store.rolloverHour = which
                dialog.dismiss()
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun reminderSummary(): String {
        val m = store.reminderMinutes
        if (m < 0) return "Off"
        val time = "%02d:%02d".format(m / 60, m % 60)
        return "$time, only if today isn't done yet"
    }

    private fun pickReminder() {
        val current = store.reminderMinutes.let { if (it < 0) 20 * 60 else it }
        val dialog = TimePickerDialog(
            this,
            { _, hour, minute ->
                store.reminderMinutes = hour * 60 + minute
                store.lastRemindedDay = null
                refresh()
            },
            current / 60, current % 60, DateFormat.is24HourFormat(this)
        )
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Turn off") { _, _ ->
            store.reminderMinutes = -1
            refresh()
        }
        dialog.show()
    }

    private fun askPermissions() {
        val permanentlyDenied = store.askedPermission && !shouldShowRequestPermissionRationale(AnkiBridge.PERMISSION)
        if (permanentlyDenied) {
            Toast.makeText(this, "Allow the AnkiDroid permission under Permissions", Toast.LENGTH_LONG).show()
            openAppSettings()
            return
        }
        store.askedPermission = true
        val perms = mutableListOf(AnkiBridge.PERMISSION)
        if (Build.VERSION.SDK_INT >= 33) {
            perms += Manifest.permission.POST_NOTIFICATIONS
            store.askedNotifications = true
        }
        requestPermissions(perms.toTypedArray(), REQ_PERMS)
    }

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT < 33) return
        if (store.askedNotifications && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            openAppSettings()
            return
        }
        store.askedNotifications = true
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_PERMS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun openAnki() {
        val intent = packageManager.getLaunchIntentForPackage(AnkiBridge.PACKAGE)
        if (intent != null) startActivity(intent)
        else Toast.makeText(this, "AnkiDroid not found", Toast.LENGTH_SHORT).show()
    }

    private fun openStore() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${AnkiBridge.PACKAGE}")))
        } catch (e: ActivityNotFoundException) {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${AnkiBridge.PACKAGE}"))
            )
        }
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    // ---------------------------------------------------------------- tiny view helpers

    @Suppress("DEPRECATION")
    private fun setupSystemBars() {
        if (Build.VERSION.SDK_INT < 35) {
            window.statusBarColor = BG
            window.navigationBarColor = BG
        }
        if (Build.VERSION.SDK_INT >= 30) {
            val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(light, light)
        } else {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun spaced(top: Int) = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(top) }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER_HORIZONTAL
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun sectionTitle(s: String) = text(s.uppercase(), 13f, MUTED, bold = true).apply {
        letterSpacing = 0.1f
        gravity = Gravity.START
        layoutParams = spaced(28)
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(Color.WHITE)
            setStroke(dp(2), LIGHT)
        }
    }

    private fun button(label: String, primary: Boolean = true, onClick: () -> Unit) = TextView(this).apply {
        text = label.uppercase()
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.05f
        gravity = Gravity.CENTER
        setTextColor(if (primary) Color.WHITE else ORANGE)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        val shape = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(if (primary) ORANGE else Color.WHITE)
            setStroke(dp(2), if (primary) ORANGE_DARK else LIGHT)
        }
        background = RippleDrawable(ColorStateList.valueOf(0x33000000), shape, null)
        setOnClickListener { onClick() }
    }

    private fun settingRow(title: String, summary: String, onClick: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4), dp(12), dp(4), dp(12))
        addView(text(title, 16f, DARK, bold = true).apply { gravity = Gravity.START })
        addView(text(summary, 14f, MUTED).apply { gravity = Gravity.START })
        isClickable = true
        background = RippleDrawable(ColorStateList.valueOf(0x22000000), null, GradientDrawable().apply { setColor(Color.WHITE) })
        setOnClickListener { onClick() }
        layoutParams = spaced(4)
    }

    private fun switchRow(title: String, summary: String, value: Boolean, onChange: (Boolean) -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(12), dp(4), dp(12))
            val texts = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(title, 16f, DARK, bold = true).apply { gravity = Gravity.START })
                addView(text(summary, 14f, MUTED).apply { gravity = Gravity.START })
            }
            addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
            val sw = Switch(context).apply {
                isChecked = value
                setOnCheckedChangeListener { _, checked -> onChange(checked) }
            }
            addView(sw)
            setOnClickListener { sw.toggle() }
            layoutParams = spaced(4)
        }

    private companion object {
        const val REQ_PERMS = 1
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val BG = 0xFFFFFFFF.toInt()
        const val DARK = 0xFF3C3C3C.toInt()
        const val MUTED = 0xFF8A8A8A.toInt()
        const val LIGHT = 0xFFE9E9E9.toInt()
        const val ORANGE = 0xFFFF9600.toInt()
        const val ORANGE_DARK = 0xFFE07F00.toInt()
        const val ORANGE_LIGHT = 0xFFFFE3B8.toInt()
        const val GREEN = 0xFF46A302.toInt()
    }
}
