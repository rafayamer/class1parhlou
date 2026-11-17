package pk.parhlou.student.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pk.parhlou.student.ui.components.MathKeyboard
import pk.parhlou.student.ui.theme.ParhlouTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MathKeyboardTypingTest {
    @get:Rule val rule = createComposeRule()

    @Test fun tappingKeysTypesIntoTheAnswer() {
        var out = ""
        rule.setContent {
            ParhlouTheme {
                var v by remember { mutableStateOf(TextFieldValue("")) }
                MathKeyboard(v, { v = it; out = it.text }, physics = false)
            }
        }
        rule.onNodeWithTag("key-√").performClick()
        rule.onNodeWithTag("key-π").performClick()
        assertEquals("√(π)", out)
    }
}
