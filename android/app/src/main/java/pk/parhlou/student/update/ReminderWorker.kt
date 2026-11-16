package pk.parhlou.student.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import pk.parhlou.student.MainActivity
import pk.parhlou.student.data.LocalStore
import pk.parhlou.student.data.ProgressStore
import pk.parhlou.student.domain.Rewards
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Daily study reminder from Ustad at the student's chosen hour, sent only when nothing has been studied today.
 * Mentions the streak when one is at risk.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = LocalStore(applicationContext)
        if (store.reminderHour.first() < 0) return Result.success()
        val progress = ProgressStore(store).also { it.load() }
        val minutes = store.onboarding.first().minutesPerDay
        val r = Rewards.summary(progress.history(), minutes, System.currentTimeMillis())
        if (r.studiedToday) return Result.success()
        val text = if (r.streakDays >= 1) "Your ${r.streakDays}-day streak is waiting. 5 minutes with Ustad keeps it alive!"
        else "Ustad is ready for today's session. Just 5 minutes builds your exam marks."
        notify(applicationContext, text)
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "reminders"
        private const val WORK = "parhlou-daily-reminder"

        /** Schedules (or cancels, for hour < 0) the daily reminder at [hour]:00. */
        fun schedule(context: Context, hour: Int, now: LocalDateTime = LocalDateTime.now()) {
            val wm = WorkManager.getInstance(context)
            if (hour < 0) { wm.cancelUniqueWork(WORK); return }
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntil(hour, now).toMinutes(), TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** Time from [now] to the next [hour]:00. */
        fun delayUntil(hour: Int, now: LocalDateTime): Duration {
            var at = now.toLocalDate().atTime(hour, 0)
            if (!at.isAfter(now)) at = at.plusDays(1)
            return Duration.between(now, at)
        }

        private fun notify(context: Context, text: String) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Study reminders", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            manager.notify(1, NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Time to study with Ustad")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open).setAutoCancel(true).build())
        }
    }
}
