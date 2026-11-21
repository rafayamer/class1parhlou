package pk.parhlou.student.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a free student can open, what Premium unlocks, and what turning the paywall off does. */
class PremiumTest {
    private val free = Premium.Access(premium = false, paywall = true)

    @Test fun chaptersOneAndTwoAreFree() {
        assertTrue(free.chapter(0))
        assertTrue(free.chapter(1))
        assertFalse("chapter 3 is Premium", free.chapter(2))
        assertTrue("a chapter that can't be found stays open", free.chapter(null))
        assertTrue(free.copy(premium = true).chapter(9))
        assertTrue("with the paywall off everything is open", free.copy(paywall = false).chapter(9))
    }

    @Test fun oneFreePastPaperOfTheStudentsChoice() {
        assertTrue("before choosing, any past paper can be the free one", free.pastPaper("lahore-2024"))
        val chose = free.copy(samplePaper = "lahore-2024")
        assertTrue(chose.pastPaper("lahore-2024"))
        assertFalse(chose.pastPaper("multan-2023"))
        assertFalse("Guess Paper and own tests are Premium", free.papers)
        assertTrue(free.copy(premium = true).papers)
    }

    @Test fun threeFreeTriesOfAskUstaadAndAiMarking() {
        assertEquals(3, free.aiTriesLeft)
        assertTrue(free.ai)
        val used = free.copy(aiTriesUsed = 3)
        assertEquals(0, used.aiTriesLeft)
        assertFalse(used.ai)
        assertNull("Premium has no limit", used.copy(premium = true).aiTriesLeft)
        assertTrue(used.copy(premium = true).ai)
    }

    @Test fun priceReadsLikeMoney() {
        assertEquals(3999, Premium.PRICE_PKR)
        assertEquals("Rs 3,999", Premium.rupees(3999))
    }
}
