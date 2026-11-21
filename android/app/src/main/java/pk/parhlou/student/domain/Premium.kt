package pk.parhlou.student.domain

/**
 * What Premium unlocks.
 *
 * Free: the first quarter of every book (lessons, practice, exam kit and solutions). No papers and no AI.
 * Premium (Rs 3,999 a month): every chapter, every paper with no limits (Guess Paper, all past papers, model papers, papers
 * made for you), and 1,000 credits a month for everything the AI does (Ask Ustaad, photo questions, AI marking, reading
 * answer pages, examiner reports, choosing a paper's questions). Premium and its credits stop when the paid months end.
 * The owner can switch the paywall off in the owner panel (App design, "Premium paywall").
 */
object Premium {
    /** Default monthly price; the server's price (owner panel, Payments) wins when it is known. */
    const val PRICE_PKR = 3999
    /** Credits Premium gives each month (1,000 credits = US$3.50 of AI time); the server's number wins when known. */
    const val MONTHLY_CREDITS = 1000

    /** The free first quarter of a book with [total] chapters, at least one: 13 chapters → 3, 12 → 3, 9 → 2, 4 → 1. */
    fun freeChapters(total: Int): Int = maxOf(1, (total + 2) / 4)

    data class Access(
        val premium: Boolean,
        /** False when the paywall is off (owner switch, or builds and tests without one): everything is open. */
        val paywall: Boolean,
    ) {
        val unlocked: Boolean get() = premium || !paywall
        /** Chapter [index] (from 0) of a book with [total] chapters. A chapter that can't be found stays open. */
        fun chapter(index: Int?, total: Int): Boolean = unlocked || index == null || index < freeChapters(total)
        /** Every paper, with no limit on tries. */
        val papers: Boolean get() = unlocked
        /** Ask Ustaad, AI marking, examiner reports: Premium's credits. */
        val ai: Boolean get() = unlocked

        companion object {
            /** Everything open: tests, previews and builds without a paywall. */
            val OPEN = Access(premium = false, paywall = false)
        }
    }

    /** What the Premium screen lists, free vs Premium. */
    val COMPARISON = listOf(
        Triple("Lessons and practice", "First quarter of each book", "Every chapter"),
        Triple("Exam kit and book solutions", "First quarter of each book", "Every chapter"),
        Triple("Past papers and Guess Paper 2027", "—", "All, no limits"),
        Triple("Papers made for you", "—", "No limits"),
        Triple("Ask Ustaad, AI marking and reports", "—", "1,000 credits a month"),
        Triple("Ads", "Short ads after quizzes", "No ads"),
    )

    /** "Rs 3,999" with a thousands comma. */
    fun rupees(amount: Int): String = "Rs " + java.text.NumberFormat.getIntegerInstance(java.util.Locale.ENGLISH).format(amount)
}
