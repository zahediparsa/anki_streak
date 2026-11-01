package app.ankistreak

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Home-screen widget: orange flame when today is done, grey flame when cards are waiting. */
class StreakWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Scheduler.ensureScheduled(context)
        update(context, manager, ids)
    }

    companion object {
        fun updateAll(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(ctx, StreakWidget::class.java))
            if (ids.isNotEmpty()) update(ctx, manager, ids)
        }

        private fun update(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
            val store = StreakStore(ctx)
            val today = store.today()
            val done = store.isDone(today)
            val streak = store.currentStreak(today)

            val views = RemoteViews(ctx.packageName, R.layout.widget_streak)
            views.setImageViewResource(
                R.id.widget_flame, if (done) R.drawable.ic_flame else R.drawable.ic_flame_gray
            )
            views.setTextViewText(R.id.widget_count, streak.toString())
            views.setTextColor(R.id.widget_count, if (done) 0xFFFF9600.toInt() else 0xFF777777.toInt())
            views.setTextViewText(R.id.widget_label, if (done) "done today ✓" else "cards waiting")

            val open = PendingIntent.getActivity(
                ctx, 2, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            manager.updateAppWidget(ids, views)
        }
    }
}
