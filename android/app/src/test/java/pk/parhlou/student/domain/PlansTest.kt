package pk.parhlou.student.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pk.parhlou.student.ads.AdPacing

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlansTest {
    @Test fun parsesThePlan() {
        val i = Plans.parse("""{"plan":"free","paidUntil":null,"pricePkr":499,"adsEnabled":true,"freeUsd":0.5,"plusUsd":3,
            "accounts":{"jazzcash":{"number":"0300 0000000","name":"A. Founder"},"easypaisa":{"number":""}},
            "lastRequest":{"status":"pending","months":3,"amount":1497,"txn":"ABC123","note":""}}""")!!
        assertFalse(i.plus)
        assertTrue(i.showAds)
        assertTrue(i.waiting)
        assertEquals(listOf("jazzcash"), i.wallets.map { it.method })
        assertEquals(null, i.paidUntil)
        val plus = Plans.parse("""{"plan":"plus","paidUntil":"2027-02-19","adsEnabled":true}""")!!
        assertFalse("Plus has no ads", plus.showAds)
        assertEquals("2027-02-19", plus.paidUntil)
    }

    @Test fun cleansTransactionIds() = assertEquals("0123456789AB", Plans.cleanTxn(" 0123-4567 89ab "))

    @Test fun adsArePaced() {
        val start = 0L
        val p = AdPacing(start)
        val min = 60_000L
        assertFalse("not in the first minutes", p.shouldShow(start + 1 * min))
        assertTrue("second quiz after warm-up", p.shouldShow(start + 4 * min))
        assertFalse("odd quiz", p.shouldShow(start + 5 * min))
        assertFalse("within 10 minutes of the last ad", p.shouldShow(start + 6 * min))
        assertFalse("odd quiz", p.shouldShow(start + 15 * min))
        assertTrue(p.shouldShow(start + 16 * min))
    }
}
