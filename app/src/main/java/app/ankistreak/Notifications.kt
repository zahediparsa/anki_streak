package app.ankistreak

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object Notifications {
    private const val CH_DONE = "streak_done"
    private const val CH_REMIND = "streak_reminder"
    private const val ID_DONE = 1
    private const val ID_REMIND = 2
    private const val ORANGE = 0xFFFF9600.toInt()

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, "Streak extended", NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REMIND, "Daily reminder", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun celebrate(ctx: Context, streak: Int) {
        if (!canNotify(ctx)) return
        createChannels(ctx)
        val title = if (streak <= 1) "🔥 Streak started!" else "🔥 $streak day streak!"
        val n = Notification.Builder(ctx, CH_DONE)
            .setSmallIcon(R.drawable.ic_stat_flame)
            .setColor(ORANGE)
            .setContentTitle(title)
            .setContentText("All of today's Anki cards are done. Bis morgen!")
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java)?.notify(ID_DONE, n)
    }

    fun remind(ctx: Context, streak: Int, remaining: Int) {
        if (!canNotify(ctx)) return
        createChannels(ctx)
        val title = if (streak > 0) "Keep your 🔥 $streak day streak!" else "Start a streak today 🔥"
        val cards = if (remaining == 1) "1 card" else "$remaining cards"
        val n = Notification.Builder(ctx, CH_REMIND)
            .setSmallIcon(R.drawable.ic_stat_flame)
            .setColor(ORANGE)
            .setContentTitle(title)
            .setContentText("$cards left in AnkiDroid today.")
            .setContentIntent(openAnki(ctx) ?: openApp(ctx))
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java)?.notify(ID_REMIND, n)
    }

    fun cancelReminder(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java)?.cancel(ID_REMIND)
    }

    private fun openApp(ctx: Context): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun openAnki(ctx: Context): PendingIntent? {
        val intent = ctx.packageManager.getLaunchIntentForPackage(AnkiBridge.PACKAGE) ?: return null
        return PendingIntent.getActivity(
            ctx, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
