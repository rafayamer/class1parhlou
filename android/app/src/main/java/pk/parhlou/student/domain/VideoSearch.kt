package pk.parhlou.student.domain

import java.net.URLEncoder

/**
 * YouTube searches that find real lectures for a section. Pakistani teachers title their videos like
 * "9th Class Math Chapter 1 Exercise 1.1" or "9th class physics chapter 2 kinematics lecture", so the query uses that
 * shape: class, short subject name, chapter number, then the topic without its section number.
 */
object VideoSearch {
    fun shortSubject(name: String): String = when (name.trim().lowercase()) {
        "mathematics", "maths", "math" -> "math"
        "computer science", "computer" -> "computer"
        else -> name.trim().lowercase()
    }

    /** The topic words of a section title: "1.1 Introduction to Real Numbers" -> "introduction to real numbers". */
    fun topic(title: String): String = title.replace(Regex("^\\s*\\d+(\\.\\d+)*\\s*[:.\\-]?\\s*"), "").replace(Regex("\\s+"), " ").trim().lowercase()

    fun query(subjectName: String, grade: Int?, chapter: Int?, sectionTitle: String): String = listOfNotNull(
        "${grade ?: 9}th class",
        shortSubject(subjectName),
        chapter?.let { "chapter $it" },
        topic(sectionTitle).takeIf { it.isNotBlank() },
        "lecture",
    ).joinToString(" ")

    fun searchUrl(query: String): String = "https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8")

    fun watchUrl(id: String): String? = id.takeIf { Regex("^[A-Za-z0-9_-]{6,20}$").matches(it) }?.let { "https://www.youtube.com/watch?v=$it" }
}
