package pk.parhlou.student.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pk.parhlou.student.data.BookSolution
import pk.parhlou.student.data.ExamKit
import pk.parhlou.student.data.KitDefinition
import pk.parhlou.student.data.McqQuestion
import pk.parhlou.student.data.Module
import pk.parhlou.student.data.ReviewStatus
import pk.parhlou.student.data.SolutionKind

class SessionTest {
    private fun mcq(i: Int) = McqQuestion("q$i", "Question $i:", listOf("a$i", "b$i", "c$i", "d$i"), 1,
        listOf("ra$i", "rb$i", "rc$i", "rd$i"), ReviewStatus.APPROVED, "n-1", "m-1")
    private fun sol(i: Int, steps: Int) = BookSolution(SolutionKind.EXERCISE, "Ex 1.1 Q$i", "Solve $i", List(steps) { "step $i.$it" },
        "x = $i", "", listOf(listOf("x=$i")), true)
    private val module = Module("m-1", 1, "1.1 Test", null, null, emptyList(), emptyList(), ReviewStatus.APPROVED,
        solutions = (1..6).map { sol(it, 3 + it % 3) })
    private val kit = ExamKit(true, "2027 exam", emptyList(), listOf(KitDefinition("Rational number", "A number p/q, q ≠ 0.")),
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    @Test fun buildsAMixOfKindsSizedToMinutes() {
        val items = SessionBuilder.build(module, (1..9).map(::mcq), kit, minutes = 30, seed = 1)
        assertEquals(12, items.size)
        val kinds = items.map { it::class.simpleName }.toSet()
        assertTrue(kinds.containsAll(listOf("Mcq", "TrueFalse", "TypeAnswer", "OrderSteps", "Flashcard")))
        assertEquals(6, SessionBuilder.build(module, (1..9).map(::mcq), kit, minutes = 5, seed = 1).size)
        assertEquals(items, SessionBuilder.build(module, (1..9).map(::mcq), kit, minutes = 30, seed = 1)) // deterministic
    }

    @Test fun marksEveryKind() {
        val m = SessionItem.Mcq("m", null, "s", listOf("a", "b"), 1, listOf("ra", "rb"))
        assertTrue(SessionBuilder.check(m, SessionAnswer.Choice(1)).correct)
        val wrong = SessionBuilder.check(m, SessionAnswer.Choice(0))
        assertEquals("b", wrong.correctAnswer); assertEquals("ra", wrong.explanation)
        val t = SessionItem.TypeAnswer("t", null, "q", "x = 3", listOf(listOf("x=3")), null, listOf("s"))
        assertTrue(SessionBuilder.check(t, SessionAnswer.Typed("so x = 3")).correct)
        val o = SessionItem.OrderSteps("o", null, "q", listOf("a", "b", "c"), listOf(2, 0, 1))
        assertTrue(SessionBuilder.check(o, SessionAnswer.Order(listOf(0, 1, 2))).correct)
        val tf = SessionItem.TrueFalse("f", null, "s", isTrue = false, why = "w")
        assertTrue(SessionBuilder.check(tf, SessionAnswer.Choice(1)).correct)
    }

    @Test fun missedItemsComeBackAndXpAddsUp() {
        val a = SessionItem.Mcq("a", null, "s", listOf("x", "y"), 0, listOf("", ""))
        val b = SessionItem.Mcq("b", null, "s", listOf("x", "y"), 0, listOf("", ""))
        val p = SessionPlayer(listOf(a, b))
        assertTrue(p.answer(SessionAnswer.Choice(0)).correct); assertNull(p.next())
        assertTrue(!p.answer(SessionAnswer.Choice(1)).correct); assertNull(p.next())   // b missed: it comes back
        assertEquals("b", p.current?.id)
        assertTrue(p.answer(SessionAnswer.Choice(0)).correct)
        val r = assertNotNullResult(p.next())
        assertEquals(15, r.xp)          // 10 first try + 5 retry
        assertEquals(50, r.accuracy)
        assertEquals(listOf("b"), r.missed.map { it.id })
    }

    @Test fun perfectRunEarnsComboAndFinishBonus() {
        val items = (1..4).map { SessionItem.Mcq("i$it", null, "s", listOf("x", "y"), 0, listOf("", "")) }
        val p = SessionPlayer(items)
        var r: SessionResult? = null
        repeat(4) { p.answer(SessionAnswer.Choice(0)); r = p.next() }
        assertEquals(40 + 2 * 2 + 10, r!!.xp)
        assertEquals(4, r!!.bestCombo)
    }

    private fun assertNotNullResult(r: SessionResult?): SessionResult { assertNotNull(r); return r!! }
}
