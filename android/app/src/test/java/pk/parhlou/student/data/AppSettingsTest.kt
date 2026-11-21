package pk.parhlou.student.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppSettingsTest {
    @Test fun brokenOrMissingSettingsFallBackToDefaults() {
        assertEquals(AppSettings(), AppSettings.parse(null))
        assertEquals(AppSettings(), AppSettings.parse("not json"))
        val s = AppSettings.parse("""{"accent":"red","tuning":{"lessonXp":-5,"chestMinXp":50,"chestMaxXp":10},"banner":{"title":"Hi","link":"javascript:alert(1)"}}""")
        assertNull("only #RRGGBB colours", s.accent)
        assertEquals(0, s.tuning.lessonXp)
        assertTrue("the chest range stays valid", s.tuning.chestMaxXp >= s.tuning.chestMinXp)
        assertNull("only https links", s.banner!!.link)
    }

    @Test fun featureSwitchesAndGradualRollout() {
        val s = AppSettings.parse("""{"features":{"league":{"on":false},"chest":{"on":true,"rollout":50},"videos":{"on":true,"rollout":0}}}""")
        assertFalse(s.enabled("league", "abc"))
        assertFalse(s.enabled("videos", "abc"))
        assertTrue("unlisted features are on", s.enabled("plus", "abc"))
        val share = (0 until 1000).count { s.enabled("chest", "install-$it") }
        assertTrue("about half get a 50% rollout ($share)", share in 400..600)
        assertEquals("the same student always gets the same answer", s.enabled("chest", "install-7"), s.enabled("chest", "install-7"))
    }

    @Test fun bannerDatesAndAudience() {
        val s = AppSettings.parse("""{"banner":{"title":"Exam week","startsAt":"2026-12-01","endsAt":"2026-12-07","audience":"free"}}""")
        val b = s.banner!!
        assertFalse(b.showsOn("2026-11-30", false))
        assertTrue(b.showsOn("2026-12-03", false))
        assertFalse("free-plan banner isn't shown to Plus", b.showsOn("2026-12-03", true))
        assertFalse(b.showsOn("2026-12-08", false))
        assertNull("a banner switched off isn't shown", AppSettings.parse("""{"banner":{"title":"x","on":false}}""").banner)
    }
}
