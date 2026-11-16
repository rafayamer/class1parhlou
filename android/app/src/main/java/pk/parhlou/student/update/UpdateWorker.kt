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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import pk.parhlou.student.BuildConfig
import pk.parhlou.student.MainActivity
import java.util.concurrent.TimeUnit

/**
 * Keeps the app up to date on its own: every 6 hours (when online) it checks for a newer build, downloads it in the
 * background and posts a notification. Android still asks the student to confirm the install with one tap.
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val updater = AppUpdater(applicationContext, BuildConfig.UPDATE_REPO, BuildConfig.VERSION_CODE)
        val info = updater.check() ?: return Result.success()
        updater.download(info) ?: return Result.retry()
        notifyReady(applicationContext, info.build)
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "updates"
        private const val WORK = "parhlou-update-check"

        fun schedule(context: Context) {
            if (BuildConfig.UPDATE_REPO.isBlank()) return
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun notifyReady(context: Context, build: Int) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT))
            }
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Parhlou update ready")
                .setContentText("Build $build is downloaded. Tap to see what's new and install.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            manager.notify(build, n)
        }
    }
}
