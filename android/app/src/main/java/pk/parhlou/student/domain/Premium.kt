package pk.parhlou.student.domain

/**
 * What Premium unlocks, and the free taste every student gets.
 *
 * Free: chapters 1 and 2 of every book (lessons, practice, exam kit and solutions), one past paper of the student's choice
 * (the first one they open), and three tries of Ask Ustaad or AI marking. Premium (Rs 3,999 a month): every chapter,
 * every paper (Guess Paper, all past papers, Make my own test, model papers) and unlimited Ask Ustaad and AI marking
 * within the monthly allowance. The owner can switch the paywall off in the owner panel (App design, "Premium paywall").
 */
object Premium {
    /** Default monthly price; the server's price (owner panel, Payments) wins when it is known. */
    const val PRICE_PKR = 3999
    /** Chapters 1 and 2 of every book are free. */
    const val FREE_CHAPTERS = 2
    /** Ask Ustaad questions plus AI markings a free student can try. */
    const val FREE_AI_TRIES = 3

    data class Access(
        val premium: Boolean,
        /** False when the paywall is off (owner switch, or builds and tests without one): everything is open. */
        val paywall: Boolean,
        /** The past paper the student chose as their free one; null until they open one. */
        val samplePaper: String? = null,
        val aiTriesUsed: Int = 0,
    ) {
        val unlocked: Boolean get() = premium || !paywall
        /** [index] counts from 0, so chapters 1 and 2 are indexes 0 and 1. A chapter that can't be found stays open. */
        fun chapter(index: Int?): Boolean = unlocked || index == null || index < FREE_CHAPTERS
        fun pastPaper(id: String): Boolean = unlocked || samplePaper == null || samplePaper == id
        val papers: Boolean get() = unlocked
        /** Null means no limit. */
        val aiTriesLeft: Int? get() = if (unlocked) null else (FREE_AI_TRIES - aiTriesUsed).coerceAtLeast(0)
        val ai: Boolean get() = unlocked || (aiTriesLeft ?: 1) > 0

        companion object {
            /** Everything open: tests, previews and builds without a paywall. */
            val OPEN = Access(premium = false, paywall = false)
        }
    }

    /** What the Premium screen lists, free vs Premium. */
    val COMPARISON = listOf(
        Triple("Lessons and practice", "Chapters 1–2", "Every chapter"),
        Triple("Exam kit and book solutions", "Chapters 1–2", "Every chapter"),
        Triple("Past board papers", "1 paper", "All boards and years"),
        Triple("Guess Paper 2027", "—", "Included"),
        Triple("Make my own test", "—", "Included"),
        Triple("Ask Ustaad", "3 tries", "Included"),
        Triple("AI marking and examiner reports", "3 tries", "Included"),
        Triple("Ads", "Short ads after quizzes", "No ads"),
    )

    /** "Rs 3,999" with a thousands comma. */
    fun rupees(amount: Int): String = "Rs " + java.text.NumberFormat.getIntegerInstance(java.util.Locale.ENGLISH).format(amount)
}
