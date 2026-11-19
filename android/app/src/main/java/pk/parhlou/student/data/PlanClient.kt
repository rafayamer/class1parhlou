package pk.parhlou.student.data

import org.json.JSONObject
import pk.parhlou.student.domain.Plans

/** The student's plan and hand-checked payments, through their own session. Null when offline or signed out. */
open class PlanClient(private val sync: SyncClient?) {
    open suspend fun info(): Plans.Info? = sync?.rpc("my_plan")?.let(Plans::parse)
    /** "I've paid": the updated plan, or an error code. */
    open suspend fun requestPayment(method: String, months: Int, txn: String, payer: String): Pair<Plans.Info?, String?> {
        val s = sync ?: return null to "OFFLINE"
        val (text, err) = s.rpcOrError("payment_request", JSONObject().put("p_method", method).put("p_months", months)
            .put("p_txn", Plans.cleanTxn(txn)).put("p_payer", payer.take(60)))
        return text?.let(Plans::parse) to err
    }
}
