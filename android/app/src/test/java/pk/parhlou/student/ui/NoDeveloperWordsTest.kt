package pk.parhlou.student.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Students only ever see "Ustaad" and plain words: no model names, no "demo", "sample" or "not official" labels, no
 * "unverified" notes. This scans every string the app shows (Kotlin string literals and string resources).
 */
class NoDeveloperWordsTest {
    private val banned = Regex("""gpt|luna|\bdemo|sample —|not official|unverified|ai examiner|in this build|not connected|lorem|\btodo\b|fixme""", RegexOption.IGNORE_CASE)
    private val literal = Regex(""""((?:[^"\\]|\\.)*)"""")
    private val template = Regex("""\$\{[^}]*}|\$[A-Za-z_][A-Za-z0-9_]*""")

    @Test fun noScreenShowsDeveloperWording() {
        val root = File("src/main").takeIf { it.isDirectory } ?: File("app/src/main")
        val found = mutableListOf<String>()
        root.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "xml") }.forEach { f ->
            f.readLines().forEachIndexed { i, line ->
                val code = line.trim()
                if (code.startsWith("//") || code.startsWith("*") || code.startsWith("/*") || code.startsWith("<!--")) return@forEachIndexed
                val texts = if (f.extension == "kt") literal.findAll(line).map { it.groupValues[1] }.toList()
                    else Regex(""">([^<]+)<""").findAll(line).map { it.groupValues[1] }.toList()
                texts.map { template.replace(it, "") }.filter { banned.containsMatchIn(it) }.forEach { found += "${f.name}:${i + 1}: $it" }
            }
        }
        assertEquals("Developer wording students would see:\n" + found.joinToString("\n"), emptyList<String>(), found)
    }
}
