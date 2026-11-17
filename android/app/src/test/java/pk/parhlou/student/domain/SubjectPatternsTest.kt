package pk.parhlou.student.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** Each subject's paper follows its PECTAA pairing scheme: the attempted marks add up to the paper's total. */
class SubjectPatternsTest {
    private fun attempted(id: String) = PaperAssembler.patternFor(id).sections.sumOf { it.attempt * it.marksEach }

    @Test fun everySubjectsPaperAddsUp() {
        assertEquals(75.0, attempted("pb-9-math"), 0.0)
        assertEquals(60.0, attempted("pb-9-phy"), 0.0)
        assertEquals(60.0, attempted("pb-9-chem"), 0.0)
        assertEquals(50.0, attempted("pb-9-cs"), 0.0)
        assertEquals(50.0, PaperAssembler.patternFor("pb-9-cs").totalMarks, 0.0)
    }
}
