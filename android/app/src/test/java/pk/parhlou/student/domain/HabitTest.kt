package pk.parhlou.student.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pk.parhlou.student.update.ReminderWorker
import java.time.LocalDate
import java.time.LocalDateTime

class HabitTest {
    @Test fun reminderFiresAtTheNextChosenHour() {
        assertEquals(120, ReminderWorker.delayUntil(18, LocalDateTime.of(2026, 11, 16, 16, 0)).toMinutes())
        assertEquals(22 * 60, ReminderWorker.delayUntil(18, LocalDateTime.of(2026, 11, 16, 20, 0)).toMinutes()) // tomorrow
    }

    @Test fun examCountdown() {
        assertEquals(105, ExamCountdown.daysLeft("2027-03-01", LocalDate.of(2026, 11, 16)))
        assertNull(ExamCountdown.daysLeft("2027-03-01", LocalDate.of(2027, 3, 2)))
        assertNull(ExamCountdown.daysLeft("", LocalDate.of(2026, 11, 16)))
    }
}
