package pk.parhlou.student.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Do Not Disturb while a paper is open: priority-only from Start until hand-in (or leaving the paper), then the phone goes
 * back to exactly the setting the student had. Needs the "Do Not Disturb access" permission; without it papers work as before.
 * The earlier setting is saved, so it is restored even if the app was closed during the paper.
 */
object FocusMode {
    private const val PREFS = "focus_mode"
    private const val KEY = "previous_filter"

    private fun nm(c: Context) = c.getSystemService(NotificationManager::class.java)

    fun allowed(c: Context): Boolean = runCatching { nm(c)?.isNotificationPolicyAccessGranted == true }.getOrDefault(false)

    fun askPermission(c: Context) {
        runCatching { c.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Silences the phone; returns true if it did. Does nothing if the student already has Do Not Disturb on. */
    fun on(c: Context): Boolean {
        if (!allowed(c)) return false
        val m = nm(c) ?: return false
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY)) prefs.edit().putInt(KEY, m.currentInterruptionFilter).apply()
        return runCatching { m.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY); true }.getOrDefault(false)
    }

    /** Puts the phone back the way it was before the paper. Safe to call any time. */
    fun off(c: Context) {
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY)) return
        val previous = prefs.getInt(KEY, NotificationManager.INTERRUPTION_FILTER_ALL)
        if (allowed(c)) runCatching { nm(c)?.setInterruptionFilter(previous) }
        prefs.edit().remove(KEY).apply()
    }
}
