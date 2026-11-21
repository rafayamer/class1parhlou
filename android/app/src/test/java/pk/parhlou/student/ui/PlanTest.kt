package pk.parhlou.student.ui

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pk.parhlou.student.data.PlanClient
import pk.parhlou.student.domain.Plans
import pk.parhlou.student.ui.screens.PlanScreen
import pk.parhlou.student.ui.theme.ParhlouTheme

/** Paying for Plus by hand: months, wallet, transaction ID, then "checking your payment". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlanTest {
    @get:Rule val rule = createComposeRule()

    private val free = Plans.Info(false, null, 499, listOf(Plans.Wallet("jazzcash", "0300 1234567", "Founder"), Plans.Wallet("easypaisa", "0345 7654321", "Founder")),
        true, 0.5, 3.0, null)

    private class Fake(var info: Plans.Info) : PlanClient(null) {
        var sent: Triple<String, Int, String>? = null
        override suspend fun info() = info
        override suspend fun requestPayment(method: String, months: Int, txn: String, payer: String): Pair<Plans.Info?, String?> {
            if (Plans.cleanTxn(txn) == "USED1234") return null to "TRANSACTION_USED"
            sent = Triple(method, months, Plans.cleanTxn(txn))
            info = info.copy(lastRequest = Plans.Request("pending", months, 499 * months, Plans.cleanTxn(txn), ""))
            return info to null
        }
    }

    @Test fun payingForThreeMonths() {
        val fake = Fake(free)
        // Wallet transfers exist only in the direct-download version (the Play version is tested below).
        rule.setContent { ParhlouTheme { PlanScreen(fake, signedIn = true, onSignIn = {}, onPlanChanged = {}, onBack = {}, manualPayments = true) } }
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("plan-pay")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("months-3").performScrollTo().performClick()
        rule.onNodeWithText("Send Rs 1497 to", substring = true).assertExists()
        rule.onNodeWithText("Easypaisa").performScrollTo().performClick()
        rule.onNodeWithText("0345 7654321", substring = true).assertExists()
        rule.onNodeWithTag("parent-ok").performScrollTo().performClick()
        rule.onNodeWithTag("txn-input").performScrollTo().performTextInput("used-1234")
        rule.onNodeWithTag("paid").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("already been used", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("txn-input").performScrollTo().performTextClearance()
        rule.onNodeWithTag("txn-input").performTextInput("9876-5432")
        rule.onNodeWithTag("paid").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Checking your payment")).fetchSemanticsNodes().isNotEmpty() }
        assert(fake.sent == Triple("easypaisa", 3, "98765432"))
    }

    @Test fun thePlayVersionOffersNoWalletTransfers() {
        rule.setContent { ParhlouTheme { PlanScreen(Fake(free), signedIn = true, onSignIn = {}, onPlanChanged = {}, onBack = {}, manualPayments = false) } }
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("plan-play-soon")).fetchSemanticsNodes().isNotEmpty() }
        assert(rule.onAllNodes(hasTestTag("plan-pay")).fetchSemanticsNodes().isEmpty())
    }

    @Test fun guestsAreAskedToSignIn() {
        rule.setContent { ParhlouTheme { PlanScreen(Fake(free), signedIn = false, onSignIn = {}, onPlanChanged = {}, onBack = {}) } }
        rule.onNodeWithTag("plan-price").assertExists()
        rule.onNodeWithText("Sign in").assertExists()
    }
}
