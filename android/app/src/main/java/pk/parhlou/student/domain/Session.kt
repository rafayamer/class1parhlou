package pk.parhlou.student.domain

import pk.parhlou.student.ai.TextMatch
import pk.parhlou.student.data.BookSolution
import pk.parhlou.student.data.ExamKit
import pk.parhlou.student.data.McqQuestion
import pk.parhlou.student.data.Module
import pk.parhlou.student.data.SolutionKind
import kotlin.random.Random

/** One screen of a guided session. Every kind is built from content the course already has (no AI needed). */
sealed interface SessionItem {
    val id: String
    val noteId: String?
    /** What the student should do, shown at the top of the screen. */
    val instruction: String

    data class Mcq(override val id: String, override val noteId: String?, val stem: String, val options: List<String>,
                   val correct: Int, val rationales: List<String>, val pastPapers: List<String> = emptyList()) : SessionItem {
        override val instruction = "Choose the correct answer"
    }
    data class TrueFalse(override val id: String, override val noteId: String?, val statement: String, val isTrue: Boolean,
                         val why: String) : SessionItem {
        override val instruction = "True or false?"
    }
    data class TypeAnswer(override val id: String, override val noteId: String?, val question: String, val answer: String,
                          val accept: List<List<String>>, val hint: String?, val solution: List<String>,
                          val figures: List<String> = emptyList()) : SessionItem {
        override val instruction = "Work it out and type your final answer"
    }
    data class OrderSteps(override val id: String, override val noteId: String?, val question: String, val steps: List<String>,
                          val shuffled: List<Int>, val figures: List<String> = emptyList()) : SessionItem {
        override val instruction = "Put the steps of the solution in order"
    }
    data class NextStep(override val id: String, override val noteId: String?, val question: String, val shown: List<String>,
                        val options: List<String>, val correct: Int, val figures: List<String> = emptyList()) : SessionItem {
        override val instruction = "What is the next step?"
    }
    data class Flashcard(override val id: String, override val noteId: String?, val term: String, val text: String) : SessionItem {
        override val instruction = "Say it word for word, then flip the card"
    }
}

/** The student's answer to one item. */
sealed interface SessionAnswer {
    data class Choice(val index: Int) : SessionAnswer
    data class Typed(val text: String) : SessionAnswer
    data class Order(val order: List<Int>) : SessionAnswer
    data class SelfRated(val knewIt: Boolean) : SessionAnswer
}

data class Feedback(val correct: Boolean, val title: String, val explanation: String, val correctAnswer: String?)

/** Builds a session for one section: a mix of question types, sized to the student's minutes. Deterministic per seed. */
object SessionBuilder {
    fun size(minutes: Int): Int = when {
        minutes <= 5 -> 6
        minutes <= 15 -> 10
        else -> 12
    }

    fun build(module: Module, quiz: List<McqQuestion>, kit: ExamKit?, minutes: Int, seed: Long, noteId: String? = null): List<SessionItem> {
        val rnd = Random(seed)
        val target = size(minutes)
        val mcqs = quiz.filter { it.options.size >= 2 && it.sampleCorrectIndex in it.options.indices }.shuffled(rnd)
        val sols = module.solutions.filter { it.checked && it.steps.isNotEmpty() }.shuffled(rnd)
        val pool = mutableListOf<SessionItem>()

        // Multiple choice and true/false from the section's MCQs.
        mcqs.forEachIndexed { i, q ->
            if (i % 3 == 2 && q.rationales.size == q.options.size) {
                val pickTrue = rnd.nextBoolean()
                val opt = if (pickTrue) q.sampleCorrectIndex else q.options.indices.filter { it != q.sampleCorrectIndex }.random(rnd)
                pool += SessionItem.TrueFalse("tf-${q.id}", q.noteId ?: noteId, "${q.stem.trimEnd(':', ' ')}: ${q.options[opt]}", pickTrue,
                    q.rationales.getOrElse(opt) { "" })
            } else pool += SessionItem.Mcq("mcq-${q.id}", q.noteId ?: noteId, q.stem, q.options, q.sampleCorrectIndex, q.rationales, q.pastPapers)
        }
        // Type the answer: the lesson's practice questions and short book questions with a typed answer.
        module.lesson?.yourTurn?.forEachIndexed { i, p ->
            if (p.accept.isNotEmpty()) pool += SessionItem.TypeAnswer("yt-${module.id}-$i", noteId, p.question, p.answer, p.accept,
                p.hints.firstOrNull(), p.solution)
        }
        sols.filter { it.kind == SolutionKind.EXERCISE && it.accept.isNotEmpty() && it.steps.size <= 5 && it.inExam }.take(4).forEach { s ->
            pool += SessionItem.TypeAnswer("ty-${module.id}-${s.source}", noteId, "${s.source}: ${s.question}", s.answer, s.accept,
                s.steps.firstOrNull()?.let { "First step: $it" }, s.steps, s.figures)
        }
        // Order the steps / pick the next step, from worked solutions of 3–6 steps.
        val stepSols = sols.filter { it.steps.size in 3..6 && it.inExam }
        stepSols.take(3).forEachIndexed { i, s ->
            if (i % 2 == 0) pool += orderItem(module.id, s, noteId, rnd)
            else nextStepItem(module.id, s, stepSols, noteId, rnd)?.let { pool += it }
        }
        // Flashcards: definitions to learn word for word.
        kit?.definitions?.shuffled(rnd)?.take(2)?.forEach { d ->
            pool += SessionItem.Flashcard("fc-${d.term}", noteId, d.term, d.text)
        }

        // Interleave kinds so the session never feels repetitive.
        val byKind = pool.groupBy { it::class }.values.map { it.toMutableList() }.toMutableList()
        val out = mutableListOf<SessionItem>()
        while (out.size < target && byKind.any { it.isNotEmpty() }) {
            for (list in byKind) if (list.isNotEmpty() && out.size < target) out += list.removeAt(0)
        }
        return out
    }

    private fun orderItem(moduleId: String, s: BookSolution, noteId: String?, rnd: Random): SessionItem.OrderSteps {
        var order = s.steps.indices.shuffled(rnd)
        if (order == s.steps.indices.toList()) order = order.reversed()
        return SessionItem.OrderSteps("ord-$moduleId-${s.source}", noteId, "${s.source}: ${s.question}", s.steps, order, s.figures)
    }

    private fun nextStepItem(moduleId: String, s: BookSolution, others: List<BookSolution>, noteId: String?, rnd: Random): SessionItem.NextStep? {
        val k = 1 + rnd.nextInt(s.steps.size - 1)
        val right = s.steps[k]
        val wrong = others.filter { it !== s }.flatMap { it.steps }.filter { it != right && it !in s.steps }.distinct().shuffled(rnd).take(2)
        if (wrong.size < 2) return null
        val options = (wrong + right).shuffled(rnd)
        return SessionItem.NextStep("nx-$moduleId-${s.source}", noteId, "${s.source}: ${s.question}", s.steps.take(k), options, options.indexOf(right), s.figures)
    }

    /** Marks one answer and explains it. */
    fun check(item: SessionItem, answer: SessionAnswer): Feedback = when (item) {
        is SessionItem.Mcq -> {
            val chosen = (answer as? SessionAnswer.Choice)?.index ?: -1
            val ok = chosen == item.correct
            Feedback(ok, if (ok) praise(item.id) else "Not quite",
                item.rationales.getOrElse(if (ok) item.correct else chosen) { "" }.ifBlank { item.rationales.getOrElse(item.correct) { "" } },
                if (ok) null else item.options[item.correct])
        }
        is SessionItem.TrueFalse -> {
            val said = (answer as? SessionAnswer.Choice)?.index == 0 // 0 = True, 1 = False
            val ok = said == item.isTrue
            Feedback(ok, if (ok) praise(item.id) else "Not quite", item.why, if (ok) null else if (item.isTrue) "True" else "False")
        }
        is SessionItem.TypeAnswer -> {
            val ok = TextMatch.matchesAny((answer as? SessionAnswer.Typed)?.text.orEmpty(), item.accept)
            Feedback(ok, if (ok) praise(item.id) else "Not quite", if (ok) "That's the right answer." else item.solution.joinToString("\n"),
                if (ok) null else item.answer)
        }
        is SessionItem.OrderSteps -> {
            val ok = (answer as? SessionAnswer.Order)?.order == item.steps.indices.toList()
            Feedback(ok, if (ok) praise(item.id) else "Not quite", if (ok) "Every step is in the right place." else "The correct order is shown below.",
                if (ok) null else item.steps.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n"))
        }
        is SessionItem.NextStep -> {
            val ok = (answer as? SessionAnswer.Choice)?.index == item.correct
            Feedback(ok, if (ok) praise(item.id) else "Not quite", if (ok) "That's exactly what comes next." else "This step continues the working:",
                if (ok) null else item.options[item.correct])
        }
        is SessionItem.Flashcard -> {
            val ok = (answer as? SessionAnswer.SelfRated)?.knewIt == true
            Feedback(ok, if (ok) "Well remembered!" else "You'll see it again", if (ok) "Keep it word for word in the exam." else "Read it once more, then say it aloud.", null)
        }
    }

    private val PRAISE = listOf("Correct!", "Great job!", "Shabash!", "Excellent!", "Spot on!", "Well done!")
    private fun praise(id: String) = PRAISE[(id.hashCode() and 0x7fffffff) % PRAISE.size]
}

/** Result of one finished session. */
data class SessionResult(val xp: Int, val firstTryCorrect: Int, val total: Int, val bestCombo: Int, val missed: List<SessionItem>) {
    val accuracy: Int get() = if (total == 0) 0 else firstTryCorrect * 100 / total
}

/**
 * Plays a session the Duolingo way: one item at a time; a missed item comes back at the end (up to twice) until it is
 * right. XP: 10 for a first-try answer, 5 for a retry, +2 for every answer in a streak of 3 or more, +10 for finishing
 * with 80% or better.
 */
class SessionPlayer(items: List<SessionItem>) {
    private val queue = items.toMutableList()
    private val total = items.size
    private val attempts = mutableMapOf<String, Int>()
    private val missedFirst = linkedSetOf<String>()
    var index = 0; private set
    var xp = 0; private set
    var combo = 0; private set
    var bestCombo = 0; private set
    private var firstTry = 0
    private var finishBonus = 0

    val current: SessionItem? get() = queue.getOrNull(index)
    val done: Boolean get() = index >= queue.size
    private val solved = mutableSetOf<String>()
    /** Fraction of the session's items finished (right, or shown three times), for the progress bar. */
    val progress: Float get() = if (total == 0) 1f else solved.size.toFloat() / total

    fun answer(answer: SessionAnswer): Feedback {
        val item = current ?: error("session finished")
        val fb = SessionBuilder.check(item, answer)
        val tries = (attempts[item.id] ?: 0) + 1
        attempts[item.id] = tries
        if (fb.correct) {
            solved += item.id
            combo++
            bestCombo = maxOf(bestCombo, combo)
            xp += if (tries == 1) 10 else 5
            if (combo >= 3) xp += 2
            if (tries == 1) firstTry++
        } else {
            combo = 0
            if (tries == 1) missedFirst += item.id
            if (tries < 3) queue += item else solved += item.id
        }
        return fb
    }

    /** Moves to the next item; returns the result once the session is over. */
    fun next(): SessionResult? {
        index++
        if (!done) return null
        if (finishBonus == 0 && total > 0 && firstTry * 100 / total >= 80) { finishBonus = 10; xp += 10 }
        val missed = missedFirst.mapNotNull { id -> queue.firstOrNull { it.id == id } }
        return SessionResult(xp, firstTry, total, bestCombo, missed)
    }
}
