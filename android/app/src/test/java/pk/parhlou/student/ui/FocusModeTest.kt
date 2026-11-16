package pk.parhlou.student.ui

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class FocusModeTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val nm = context.getSystemService(NotificationManager::class.java)

    @Test fun aPaperSilencesThePhoneAndGivesBackTheStudentsOwnSetting() {
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
        FocusMode.on(context)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, nm.currentInterruptionFilter)
        FocusMode.on(context) // a second start keeps the original setting, not "priority"
        FocusMode.off(context)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALARMS, nm.currentInterruptionFilter)
        FocusMode.off(context) // nothing to restore: no change
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALARMS, nm.currentInterruptionFilter)
    }

    @Test fun withoutPermissionNothingChanges() {
        shadowOf(nm).setNotificationPolicyAccessGranted(false)
        assertFalse(FocusMode.on(context))
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, nm.currentInterruptionFilter)
    }
}
