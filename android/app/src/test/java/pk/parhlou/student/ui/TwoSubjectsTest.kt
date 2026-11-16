package pk.parhlou.student.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pk.parhlou.student.data.BundleRepository
import pk.parhlou.student.data.ContentBundle
import pk.parhlou.student.data.FakeRepository
import pk.parhlou.student.data.LocalStore
import pk.parhlou.student.data.OnboardingState
import pk.parhlou.student.data.Pathway
import pk.parhlou.student.data.word
import androidx.compose.ui.semantics.getOrNull
import pk.parhlou.student.ui.theme.ParhlouTheme

/** Mathematics and Physics side by side: one repository, each subject's own course, papers and words. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp")
class TwoSubjectsTest {
    @get:Rule val rule = createComposeRule()
    private val packed = FakeRepository()

    /** A small Physics course: the sample course with Physics ids, chapter wording and one practical. */
    private fun physicsBundle(): JSONObject {
        val text = ContentBundle.export(packed, FakeRepository.MATHS_9, "Physics 9 test edition").toString()
            .replace(FakeRepository.MATHS_9, FakeRepository.PHYSICS_9)
            .replace(Regex(""""(u|m|n|q|w|ch|b|pp)-(\d)"""), "\"phy-$1-$2")
            .replace(Regex(""""id":"(chapter-test|sample|readiness)"""), "\"id\":\"phy-$1")
        val b = JSONObject(text)
        val s = b.getJSONObject("subject").put("name", "Physics")
        b.getJSONArray("blueprints").let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { it.put("id", "phy-" + it.getString("id")) } }
        b.getJSONArray("papers").let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { it.put("blueprintId", "phy-" + it.getString("blueprintId")) } }
        s.put("practicals", JSONArray().put(JSONObject().put("id", "phy-prac-1-2").put("unit", 1).put("label", "Activity 1.2")
            .put("title", "Thickness of a coin").put("aim", "To find the thickness of one coin using a metre rule.")
            .put("apparatus", JSONArray().put("10 identical coins").put("metre rule"))
            .put("diagram", "a stack of coins beside a metre rule").put("procedure", JSONArray().put("Stack ten coins.").put("Measure the height of the stack.").put("Divide by 10."))
            .put("table", JSONObject().put("columns", JSONArray().put("Height of 10 coins (mm)").put("Thickness of one coin (mm)"))
                .put("rows", JSONArray().put(JSONArray().put("15").put("1.5"))))
            .put("calculation", JSONArray().put("Thickness = 15 mm ÷ 10 = 1.5 mm")).put("result", "One coin is about 1.5 mm thick.")
            .put("precautions", JSONArray().put("Keep the stack straight.")).put("viva", JSONArray().put(JSONObject().put("question", "Why measure 10 coins?").put("answer", "To reduce the error of one small reading.")))
            .put("bookText", "Stack ten coins and measure their height.").put("checked", true).put("inExam", true)))
        return b
    }

    private fun both(): Pair<BundleRepository, BundleRepository> {
        val maths = ContentBundle.repository(ContentBundle.export(packed, FakeRepository.MATHS_9, "Maths 9 test edition"), 1, packed)
        val physics = ContentBundle.repository(physicsBundle(), 1, packed)
        return maths to physics
    }

    @Test fun twoCoursesStackIntoOneRepository() {
        val (maths, physics) = both()
        val before = packed.subjectOptions("PB", 9).first { it.id == FakeRepository.PHYSICS_9 }
        assertFalse("Physics can't be chosen before its course is on the phone", before.available)
        val repo = BundleRepository.stack(listOf(maths, physics), packed)
        assertNotNull(repo.subject(FakeRepository.MATHS_9)); assertNotNull(repo.subject(FakeRepository.PHYSICS_9))
        assertTrue(repo.subjectOptions("PB", 9).first { it.id == FakeRepository.PHYSICS_9 }.available)
        assertNotNull(repo.module("m-1-1")); assertNotNull(repo.module("phy-m-1-1"))
        // Ustaad answers each subject from its own notes only.
        assertTrue(repo.notes(FakeRepository.PHYSICS_9).isNotEmpty())
        assertTrue(repo.notes(FakeRepository.PHYSICS_9).all { it.subjectId == FakeRepository.PHYSICS_9 })
        assertTrue(repo.notes(FakeRepository.MATHS_9).all { it.subjectId == FakeRepository.MATHS_9 })
        val phyChapter = repo.subject(FakeRepository.PHYSICS_9)!!.chapters.first()
        assertEquals("Chapter", phyChapter.word)
        assertEquals("Unit", repo.subject(FakeRepository.MATHS_9)!!.chapters.first().word)
        assertEquals("Thickness of a coin", repo.subject(FakeRepository.PHYSICS_9)!!.practicals.single().title)
    }

    @Test fun thePhysicsBookHasPracticals() {
        val (maths, physics) = both()
        val repo = BundleRepository.stack(listOf(maths, physics), packed)
        val store = LocalStore(ApplicationProvider.getApplicationContext())
        runBlocking {
            store.clearAll()
            store.saveOnboarding(OnboardingState(true, "PB", 9, "English", setOf(FakeRepository.MATHS_9, FakeRepository.PHYSICS_9), Pathway.COMPLETE_ACADEMY, 5, 60, completed = true))
        }
        rule.setContent { ParhlouTheme { ParhlouApp(repo, store) } }
        rule.waitUntil(5_000) { rule.onAllNodes(androidx.compose.ui.test.hasText("Do this now")).fetchSemanticsNodes().isNotEmpty() }
        // Home shows a journey for each subject.
        rule.onNodeWithTag("journey-${FakeRepository.PHYSICS_9}").performScrollTo()
        rule.onNodeWithTag("tab-subjects").performClick()
        rule.waitUntil(3_000) { rule.onAllNodes(androidx.compose.ui.test.hasText("Physics · Class 9")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Physics · Class 9").performClick()
        rule.onNodeWithText("Practicals").performClick()
        rule.onNodeWithTag("practical-phy-prac-1-2").performScrollTo().performClick()
        rule.waitUntil(3_000) { rule.onAllNodes(androidx.compose.ui.test.hasText("To find the thickness of one coin", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Thickness = 15 mm ÷ 10 = 1.5 mm", substring = true).performScrollTo()
    }

    @Test fun papersSwitchBetweenSubjects() {
        val (maths, physics) = both()
        val repo = BundleRepository.stack(listOf(maths, physics), packed)
        val store = LocalStore(ApplicationProvider.getApplicationContext())
        runBlocking {
            store.clearAll()
            store.saveOnboarding(OnboardingState(true, "PB", 9, "English", setOf(FakeRepository.MATHS_9, FakeRepository.PHYSICS_9), Pathway.COMPLETE_ACADEMY, 5, 60, completed = true))
            store.setReadinessPassed(true)
        }
        rule.setContent { ParhlouTheme { ParhlouApp(repo, store) } }
        rule.waitUntil(5_000) { rule.onAllNodes(androidx.compose.ui.test.hasText("Do this now")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("tab-tests").performClick()
        rule.waitUntil(3_000) { rule.onAllNodes(androidx.compose.ui.test.hasText("Board papers")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("papers-subject-${FakeRepository.PHYSICS_9}").performClick()
        rule.onNodeWithTag("scope-chapter").performScrollTo().performClick()
        // Only Physics's chapter test is listed.
        val ids = rule.onAllNodes(tagPrefix("paper-")).fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag) }
        assertTrue(ids.toString(), ids.isNotEmpty() && ids.all { it.startsWith("paper-phy-") })
    }
}

private fun tagPrefix(prefix: String) = androidx.compose.ui.test.SemanticsMatcher("tag starts with $prefix") {
    it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith(prefix) == true
}
