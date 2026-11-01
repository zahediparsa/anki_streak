package app.ankistreak

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Checks AnkiDroid about every 15 minutes (the shortest interval Android allows). */
object Scheduler {
    private const val JOB_ID = 4242

    fun ensureScheduled(ctx: Context) {
        val js = ctx.getSystemService(JobScheduler::class.java) ?: return
        if (js.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(ctx, CheckJobService::class.java))
            .setPeriodic(15 * 60 * 1000L)
            .setPersisted(true) // survives reboots
            .build()
        js.schedule(job)
    }
}

class CheckJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            try {
                StreakChecker.check(applicationContext, fromUi = false)
            } catch (e: Exception) {
                android.util.Log.w("AnkiStreak", "Background check failed", e)
            } finally {
                jobFinished(params, false)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}

/** Re-schedules the background check after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Scheduler.ensureScheduled(context)
    }
}
