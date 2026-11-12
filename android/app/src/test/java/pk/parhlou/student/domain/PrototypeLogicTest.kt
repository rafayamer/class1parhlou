package pk.parhlou.student.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pk.parhlou.student.ai.CriterionStatus
import pk.parhlou.student.ai.DemoBrain
import pk.parhlou.student.ai.MarkStatus
import pk.parhlou.student.ai.MarkingValidator
import pk.parhlou.student.ai.ReplyKind
import pk.parhlou.student.ai.TextMatch
import pk.parhlou.student.ai.TutorStyle
import pk.parhlou.student.ai.TutorTurn
import pk.parhlou.student.data.FakeRepository
import pk.parhlou.student.data.SampleContent

class TextMatchTest {
    @Test fun wholeWordsDoNotMatchInsideLongerWords() {
        assertTrue(TextMatch.matches("so it is rational", "rational"))
        assertFalse(TextMatch.matches("so it is irrational", "rational"))
        assertTrue(TextMatch.matches("the decimal terminates", "p:terminat"))
    }

    @Test fun differentTypingsOfTheSameMathsMatch() {
        assertTrue(TextMatch.matches("5 root 2", "5√2"))
        assertTrue(TextMatch.matches("5 * sqrt(2)", "5√2"))
        assertTrue(TextMatch.matches("p^2 = 2q^2", "p²=2q²"))
        assertTrue(TextMatch.matches("p squared = 2 q squared", "p²=2q²"))
        assertTrue(TextMatch.matches("2 × 9 = 18", "2*9"))
    }

    @Test fun numbersAreNotMatchedInsideLongerNumbers() {
        assertFalse(TextMatch.matches("13/45", "3/4"))
        assertFalse(TextMatch.matches("16√2", "6√2"))
        assertFalse(TextMatch.matches("√25", "√2"))
        assertTrue(TextMatch.matches("10x = 2.777", "10x=2.7~"))
        assertFalse(TextMatch.matches("answer 18\n40 next", "1840"))
    }

    @Test fun negationAndGroups() {
        val group = listOf("rational", "!not rational")
        assertTrue(TextMatch.matchesAll("it is rational", group))
        assertFalse(TextMatch.matchesAll("it is not rational", group))
        assertTrue(TextMatch.matchesAny("x = 4/11", listOf(listOf("5/11"), listOf("4/11"))))
    }
}

class DemoTutorTest {
    private val brain = DemoBrain(FakeRepository())
    private val maths = setOf(FakeRepository.MATHS_9)
    private fun ask(q: String, style: TutorStyle = TutorStyle.NORMAL, last: String? = null, awaiting: Boolean = false, subjects: Set<String> = maths, exam: Boolean = false) =
        runBlocking { brain.tutor(TutorTurn(q, subjects, style = style, lastNoteId = last, awaitingCheck = awaiting, examActive = exam)) }

    @Test fun findsTheRightNote() {
        assertEquals("n-rational", ask("What is a rational number?").noteId)
        assertEquals("n-irrational", ask("is root 2 irrational").noteId)
        assertEquals("n-proof-root2", ask("Prove that √2 is irrational").noteId)
        assertEquals("n-surds", ask("how do I simplify √50").noteId)
        assertEquals("n-identity-inverse", ask("what is the multiplicative inverse").noteId)
        assertEquals("n-recurring-fraction", ask("convert 0.777 to a fraction").noteId)
    }

    @Test fun answersInTheLunaStructure() {
        val r = ask("What is the distributive property?")
        assertEquals(ReplyKind.ANSWER, r.kind)
        assertTrue(r.answer.isNotBlank())
        assertNotNull(r.explanation)
        assertNotNull(r.checkQuestion)
        assertTrue(r.source!!.contains("not a textbook citation"))
    }

    @Test fun refusesWhatItDoesNotKnowWithoutInventing() {
        val r = ask("Explain photosynthesis")
        assertEquals(ReplyKind.NOT_FOUND, r.kind)
        assertNull(r.source)
        assertNull(r.noteId)
        assertTrue(r.steps.isEmpty())
    }

    @Test fun staysInsideTheLearnersSubjects() {
        assertEquals(ReplyKind.NOT_FOUND, ask("What is a rational number?", subjects = setOf("pb-9-phy")).kind)
    }

    @Test fun lockedDuringAPaper() {
        val r = ask("What is a rational number?", exam = true)
        assertEquals(ReplyKind.LOCKED, r.kind)
        assertNull(r.noteId)
    }

    @Test fun followUpStylesUseTheLastTopic() {
        val steps = ask("", TutorStyle.STEPS, last = "n-surds")
        assertEquals(3, steps.steps.size)
        val simpler = ask("", TutorStyle.SIMPLER, last = "n-surds")
        assertTrue(simpler.answer.startsWith("Find a square number"))
    }

    @Test fun checksTheAnswerToItsOwnQuestion() {
        val right = ask("3√2", last = "n-surds", awaiting = true)
        assertEquals(ReplyKind.CHECK_RESULT, right.kind)
        assertTrue(right.answer.startsWith("Correct"))
        val wrong = ask("9", last = "n-surds", awaiting = true)
        assertTrue(wrong.answer.startsWith("Not quite"))
        // An off-topic question while a check is pending is refused, not marked as a wrong answer.
        assertEquals(ReplyKind.NOT_FOUND, ask("Explain photosynthesis", last = "n-surds", awaiting = true).kind)
        // A new question while a check is pending is treated as a question, not a wrong answer.
        assertEquals("n-closure", ask("what is closure", last = "n-surds", awaiting = true).noteId)
    }

    @Test fun mcqFeedbackUsesTheApprovedRationales() = runBlocking {
        val q = FakeRepository.sampleMcqs.first()
        val r = brain.mcqFeedback(q, 0, maths)
        assertTrue(r.answer.contains("√9 = 3"))
        assertTrue(r.explanation!!.contains("√2"))
    }
}

class CheckMasterTest {
    private val repo = FakeRepository()
    private val brain = DemoBrain(repo)
    private val paper = SampleContent.chapterTest
    private val blueprint = repo.blueprints(FakeRepository.MATHS_9).first { it.id == SampleContent.CHAPTER_TEST }
    private val allWritten = paper.written + SampleContent.moduleWritten.values.flatten()

    @Test fun paperMatchesItsBlueprint() {
        assertTrue(BlueprintValidator.problems(blueprint).isEmpty())
        assertEquals(blueprint.sections[0].count, paper.mcqs.size)
        assertEquals(blueprint.sections[1].count, paper.written.count { it.section == 1 })
        assertEquals(blueprint.sections[2].count, paper.written.count { it.section == 2 })
        paper.written.forEach { assertEquals(it.id, blueprint.sections[it.section].marksEach, it.marks, 0.0) }
        paper.mcqs.forEach { q ->
            assertEquals(q.id, 4, q.options.size)
            assertEquals(q.id, 4, q.rationales.size)
            assertTrue(q.id, q.rationales[q.sampleCorrectIndex].startsWith("Correct"))
            assertNotNull(q.id, SampleContent.notes.firstOrNull { it.id == q.noteId })
        }
    }

    @Test fun everyRubricAddsUpToItsQuestion() {
        allWritten.forEach { q -> assertEquals(q.id, q.marks, q.rubric.sumOf { it.marks }, 0.0) }
    }

    @Test fun everyModelAnswerEarnsFullMarks() = runBlocking {
        allWritten.forEach { q ->
            val mark = brain.markWritten(q, q.modelAnswer.joinToString("\n"), photos = 0)
            val missed = mark.criteria.filter { it.status != CriterionStatus.MET }.map { it.criterionId }
            assertTrue("${q.id} missed $missed", missed.isEmpty())
            assertEquals(q.id, MarkStatus.CHECKED, mark.status)
            assertEquals(q.id, q.marks, mark.awarded, 0.0)
            assertTrue(q.id, MarkingValidator.problems(q, mark).isEmpty())
        }
    }

    @Test fun everyNoteAcceptsItsOwnCheckAnswer() {
        SampleContent.notes.forEach { n ->
            assertTrue(n.id, TextMatch.matchesAny(n.checkAnswer, n.checkAccept))
            assertTrue(n.id, n.examples.size >= 2 && n.steps.isNotEmpty() && n.keywords.isNotEmpty())
        }
        assertEquals(SampleContent.notes.size, SampleContent.notes.map { it.id }.toSet().size)
    }

    @Test fun partialAnswerEarnsPartialMarksWithFeedback() = runBlocking {
        val q = paper.written.first { it.id == "s7" }
        val mark = brain.markWritten(q, "√98 = 7√2 and the answer is 6√2", 0)
        assertEquals(2.0, mark.awarded, 0.0)
        assertEquals(MarkStatus.CHECKED, mark.status)
        val missing = mark.criteria.single { it.status == CriterionStatus.NOT_FOUND }
        assertEquals("b", missing.criterionId)
        assertTrue(missing.how.isNotBlank() && missing.why.isNotBlank())
    }

    @Test fun acceptedAlternativeMethodEarnsCredit() = runBlocking {
        val q = paper.written.first { it.id == "l1" }
        val alt = "Suppose root 2 = a/b in lowest terms. Then a^2 = 2b^2, so a is even, a = 2m. " +
            "So b^2 = 2m^2 and b is even. Contradiction, so root 2 is irrational."
        val mark = brain.markWritten(q, alt, 0)
        assertEquals(6.0, mark.awarded, 0.0)
    }

    @Test fun unrecognisedAnswerIsNeverMarkedWrong() = runBlocking {
        val q = paper.written.first { it.id == "s3" }
        val mark = brain.markWritten(q, "I think it is about one third", 0)
        assertEquals(MarkStatus.COULD_NOT_CHECK, mark.status)
        assertEquals(0.0, mark.awarded, 0.0)
        assertEquals(q.marks, mark.unsure, 0.0)
        assertTrue(mark.criteria.all { it.status == CriterionStatus.UNSURE })
    }

    @Test fun photoOnlyAnswerWaitsForTheAiExaminer() = runBlocking {
        val q = paper.written.first { it.id == "s1" }
        val mark = brain.markWritten(q, "", photos = 2)
        assertEquals(MarkStatus.WAITING_FOR_AI, mark.status)
        assertEquals(q.marks, mark.unsure, 0.0)
        assertEquals(MarkStatus.NOT_ATTEMPTED, brain.markWritten(q, " ", 0).status)
    }

    @Test fun wholePaperTotalsAndAttemptLimit() = runBlocking {
        val model = paper.written.associate { it.id to it.modelAnswer.joinToString("\n") }
        val allCorrect = PaperSubmission(
            mcq = paper.mcqs.associate { it.id to it.sampleCorrectIndex },
            written = model,
        )
        val full = CheckMaster.check(blueprint, paper, allCorrect, brain)
        // All 8 short and 3 long answered: only the first 6 and first 2 count.
        assertEquals(6, full.written.count { it.counted && it.question.section == 1 })
        assertEquals(2, full.written.count { it.counted && it.question.section == 2 })
        assertFalse(full.written.first { it.question.id == "s7" }.counted)
        assertEquals(40.0, full.confirmed, 0.0)
        assertEquals(0.0, full.pending, 0.0)
        assertEquals(100, full.percent)

        val partial = CheckMaster.check(
            blueprint, paper,
            PaperSubmission(mcq = mapOf("p1" to 3, "p2" to 0), written = mapOf("s2" to "√40 is irrational", "s5" to "hmm"), photos = mapOf("l1" to 3)),
            brain,
        )
        assertEquals(1.0, partial.mcqMarks, 0.0)
        assertEquals(2.0, partial.confirmed, 0.0)          // 1 MCQ + 1 criterion of s2
        assertEquals(3.0 + 6.0, partial.pending, 0.0)      // s5 unrecognised + l1 photos
        assertTrue(partial.confirmed + partial.pending <= partial.total)
    }
}

class InvigilatorTest {
    private val min = 60_000L

    @Test fun logOnlyAcceptsSensibleSequences() {
        var log = ExamLog.append("", ExamEventType.LEFT, 5)
        assertEquals("", log)
        log = ExamLog.append(log, ExamEventType.STARTED, 10)
        log = ExamLog.append(log, ExamEventType.STARTED, 20)
        log = ExamLog.append(log, ExamEventType.RETURNED, 30)
        log = ExamLog.append(log, ExamEventType.LEFT, 40)
        log = ExamLog.append(log, ExamEventType.LEFT, 50)
        log = ExamLog.append(log, ExamEventType.RETURNED, 60)
        log = ExamLog.append(log, ExamEventType.SUBMITTED, 70)
        log = ExamLog.append(log, ExamEventType.LEFT, 80)
        assertEquals(listOf(10L, 40L, 60L, 70L), ExamLog.parse(log).map { it.at })
        assertEquals(10L, ExamLog.startedAt(log))
        assertTrue(ExamLog.isSubmitted(log))
    }

    @Test fun reportCountsRealAbsencesOnly() {
        val events = listOf(
            ExamEvent(ExamEventType.STARTED, 0),
            ExamEvent(ExamEventType.LEFT, 10 * min), ExamEvent(ExamEventType.RETURNED, 12 * min),
            ExamEvent(ExamEventType.LEFT, 20 * min), ExamEvent(ExamEventType.RETURNED, 20 * min + 500), // rotation
            ExamEvent(ExamEventType.LEFT, 30 * min), ExamEvent(ExamEventType.RETURNED, 35 * min),       // app killed and reopened
            ExamEvent(ExamEventType.SUBMITTED, 80 * min),
        )
        val r = Invigilator.report(events, durationMinutes = 90)!!
        assertEquals(2, r.timesAway)
        assertEquals(7 * min, r.totalAwayMillis)
        assertEquals(5 * min, r.longestAwayMillis)
        assertEquals(80 * min, r.usedMillis)
        assertTrue(r.finishedOnTime)
        assertEquals(0, r.overrunMillis)
    }

    @Test fun lateSubmissionAndUnfinishedPaper() {
        val late = Invigilator.report(
            listOf(ExamEvent(ExamEventType.STARTED, 0), ExamEvent(ExamEventType.LEFT, 85 * min), ExamEvent(ExamEventType.SUBMITTED, 95 * min)), 90,
        )!!
        assertFalse(late.finishedOnTime)
        assertEquals(5 * min, late.overrunMillis)
        assertEquals(1, late.timesAway)
        assertNull(Invigilator.report(listOf(ExamEvent(ExamEventType.STARTED, 0)), 90))
        assertEquals("1h 20m", Invigilator.duration(80 * min))
    }
}

class EvidenceCodecTest {
    @Test fun roundTripsEveryKindOfEvidence() {
        val all = listOf(
            Evidence.ModuleOpened, Evidence.VideoStudyConfirmed, Evidence.ReadingChecked, Evidence.QuizCompleted(80),
            Evidence.WrittenSubmitted, Evidence.ExamPendingReview, Evidence.ExamMarked(55), Evidence.DemoCheckedExam(70),
        )
        val log = all.mapIndexed { i, e -> EvidenceCodec.line(1000L + i, "m-1-1", e) }.joinToString("\n")
        assertEquals(all, EvidenceCodec.parse(log).map { it.evidence })
    }

    @Test fun damagedLinesAreSkipped() {
        val log = "1|m|O\ngarbage\n2|m|Z:9\nx|m|O\n3|m|Q:abc\n4|m|W"
        assertEquals(listOf(Evidence.ModuleOpened, Evidence.WrittenSubmitted), EvidenceCodec.parse(log).map { it.evidence })
    }

    @Test fun demoCheckedNeverCountsAsVerified() {
        val state = ProgressCalculator.stateOf(listOf(Evidence.QuizCompleted(90), Evidence.DemoCheckedExam(100)))
        assertEquals(LearningState.DEMO_CHECKED, state)
        assertFalse(ProgressCalculator.countsTowardMilestone(state))
        // A verified exam result still wins over a demo check.
        assertEquals(LearningState.MASTERED, ProgressCalculator.stateOf(listOf(Evidence.DemoCheckedExam(10), Evidence.ExamMarked(90))))
    }
}

class KeywordSanityTest {
    @Test fun noKeywordMatchesEverything() {
        // A keyword that normalises to a bare symbol would hijack every question.
        SampleContent.notes.flatMap { it.keywords }.forEach { k ->
            assertFalse(k, TextMatch.matches("xyz", k))
            assertFalse(k, TextMatch.normalise(k.removePrefix("p:")) in setOf("", "√", "*", "-", "/"))
        }
    }
}
