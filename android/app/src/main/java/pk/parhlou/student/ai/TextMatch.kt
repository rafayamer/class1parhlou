package pk.parhlou.student.ai

/**
 * Tolerant text matching used by the demo brain to recognise maths in typed answers.
 *
 * Pattern syntax (used in rubric `accept` groups and tutor keywords):
 * - letters and spaces only, e.g. `irrational`  → whole-word match ("rational" does not match "irrational")
 * - `p:prefix`                                   → start-of-word match ("p:terminat" matches "terminating")
 * - anything with digits or symbols, e.g. `9/20` → match ignoring spaces, not inside a longer number
 * - trailing `~`                                 → allow more digits after the match ("10x=2.7~" matches 10x = 2.777)
 * - leading `!`                                  → the rest must NOT match
 */
object TextMatch {
    private val lettersOnly = Regex("^[a-z ]+$")

    /** Lower-cases and unifies the many ways students type the same maths. */
    fun normalise(raw: String): String {
        // Line breaks become separators so numbers on different lines never run together.
        var s = raw.lowercase().replace("\n", " ; ")
        s = s.replace('−', '-').replace('–', '-').replace('—', '-')
        s = s.replace('×', '*').replace('·', '*').replace('⋅', '*').replace('∙', '*').replace('÷', '/')
        s = s.replace("…", " ").replace(Regex("\\.{2,}"), " ")
        s = s.replace("square root of", "√").replace("square root", "√").replace("sqrt", "√")
        s = s.replace(Regex("\\broot\\b"), "√")
        s = s.replace("^2", "²").replace("^3", "³").replace(Regex("\\s*squared"), "²")
        s = s.replace(Regex("√\\s*\\(\\s*(\\d+(?:\\.\\d+)?)\\s*\\)"), "√$1").replace(Regex("√\\s+"), "√")
        s = s.replace(Regex("(\\d)\\s*\\*\\s*√"), "$1√")
        return s.replace(Regex("\\s+"), " ").trim()
    }

    fun compact(normalised: String): String = normalised.replace(" ", "")

    fun matches(answer: String, pattern: String): Boolean {
        if (pattern.startsWith("!")) return !matches(answer, pattern.drop(1))
        val text = normalise(answer)
        if (pattern.startsWith("p:")) {
            return Regex("\\b" + Regex.escape(normalise(pattern.drop(2)))).containsMatchIn(text)
        }
        val openEnd = pattern.endsWith("~")
        val body = normalise(if (openEnd) pattern.dropLast(1) else pattern)
        if (body.isEmpty()) return false
        if (lettersOnly.matches(body)) {
            return Regex("\\b" + Regex.escape(body) + "\\b").containsMatchIn(text)
        }
        return compactContains(compact(text), compact(body), openEnd)
    }

    private fun compactContains(haystack: String, needle: String, openEnd: Boolean): Boolean {
        var from = 0
        while (true) {
            val i = haystack.indexOf(needle, from)
            if (i < 0) return false
            val before = haystack.getOrNull(i - 1)
            val after = haystack.getOrNull(i + needle.length)
            val startOk = !needle.first().isDigit() || before == null || !(before.isDigit() || before == '.')
            val endOk = openEnd || !needle.last().isDigit() || after == null || !after.isDigit()
            if (startOk && endOk) return true
            from = i + 1
        }
    }

    /** Every pattern in the group must hold. */
    fun matchesAll(answer: String, group: List<String>): Boolean = group.isNotEmpty() && group.all { matches(answer, it) }

    /** At least one alternative group must hold. */
    fun matchesAny(answer: String, groups: List<List<String>>): Boolean = groups.any { matchesAll(answer, it) }
}
