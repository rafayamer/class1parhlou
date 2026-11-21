package pk.parhlou.student.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a free student can open, what Premium unlocks, and what turning the paywall off does. */
class PremiumTest {
    private val free = Premium.Access(premium = false, paywall = true)

    @Test fun theFirstQuarterOfEachBookIsFree() {
        assertEquals(3, Premium.freeChapters(13))  // Class 9 Maths
        assertEquals(2, Premium.freeChapters(9))   // Class 9 Physics
        assertEquals(3, Premium.freeChapters(12))
        assertEquals(1, Premium.freeChapters(4))
        assertEquals(1, Premium.freeChapters(1))
        assertTrue(free.chapter(2, 13))
        assertFalse("chapter 4 of 13 is Premium", free.chapter(3, 13))
        assertFalse("chapter 3 of 9 is Premium", free.chapter(2, 9))
        assertTrue("a chapter that can't be found stays open", free.chapter(null, 13))
        assertTrue(free.copy(premium = true).chapter(12, 13))
        assertTrue("with the paywall off everything is open", free.copy(paywall = false).chapter(12, 13))
    }

    @Test fun papersAndAiArePremiumWithNoFreeAllowance() {
        assertFalse(free.papers)
        assertFalse("no free AI tries", free.ai)
        assertTrue(free.copy(premium = true).papers)
        assertTrue(free.copy(premium = true).ai)
        assertTrue(Premium.Access.OPEN.ai)
    }

    @Test fun priceAndCreditsReadLikeMoney() {
        assertEquals(3999, Premium.PRICE_PKR)
        assertEquals("Rs 3,999", Premium.rupees(3999))
        assertEquals(1000, Premium.MONTHLY_CREDITS)
        assertEquals("1,000", Credits.format(1000))
    }
}
