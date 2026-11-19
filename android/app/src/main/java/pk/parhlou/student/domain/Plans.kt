package pk.parhlou.student.domain

import org.json.JSONObject

/**
 * Free and Plus. Plus is paid by hand for now: the student sends money to the founder's JazzCash or Easypaisa wallet, types the
 * transaction ID, and the founder approves it in the owner panel. (Google Play Billing replaces this later.)
 */
object Plans {
    data class Wallet(val method: String, val number: String, val name: String)
    data class Request(val status: String, val months: Int, val amount: Int, val txn: String, val note: String)
    data class Info(
        val plus: Boolean,
        val paidUntil: String?,
        val pricePkr: Int,
        val wallets: List<Wallet>,
        val adsEnabled: Boolean,
        val freeUsd: Double,
        val plusUsd: Double,
        val lastRequest: Request?,
    ) {
        /** Ads only on the free plan, and only when the owner has them switched on. */
        val showAds: Boolean get() = !plus && adsEnabled
        val waiting: Boolean get() = lastRequest?.status == "pending"
    }

    val METHODS = listOf("jazzcash" to "JazzCash", "easypaisa" to "Easypaisa", "bank" to "Bank transfer")
    fun methodName(id: String) = METHODS.firstOrNull { it.first == id }?.second ?: id
    /** How many months a student can pay for at once (the price is per month). */
    val MONTHS = listOf(1, 3, 12)

    fun parse(text: String): Info? = runCatching {
        val o = JSONObject(text)
        val acc = o.optJSONObject("accounts") ?: JSONObject()
        val wallets = METHODS.mapNotNull { (id, _) ->
            acc.optJSONObject(id)?.let { w -> Wallet(id, w.optString("number"), w.optString("name")) }?.takeIf { it.number.isNotBlank() }
        }
        val r = o.optJSONObject("lastRequest")
        Info(
            plus = o.optString("plan") == "plus",
            paidUntil = o.optString("paidUntil").takeIf { it.isNotBlank() && it != "null" },
            pricePkr = o.optInt("pricePkr", 499),
            wallets = wallets,
            adsEnabled = o.optBoolean("adsEnabled", true),
            freeUsd = o.optDouble("freeUsd", 0.5),
            plusUsd = o.optDouble("plusUsd", 3.0),
            lastRequest = r?.let { Request(it.optString("status"), it.optInt("months"), it.optInt("amount"), it.optString("txn"), it.optString("note")) },
        )
    }.getOrNull()

    /** A transaction ID as the server stores it: letters and digits only, upper case. */
    fun cleanTxn(raw: String) = raw.filter { it.isLetterOrDigit() }.uppercase()

    fun explain(code: String?): String = when (code) {
        "BAD_TRANSACTION_ID" -> "Type the transaction ID from your JazzCash or Easypaisa SMS."
        "ALREADY_WAITING" -> "Your last payment is still being checked. We'll confirm it soon."
        "TRANSACTION_USED" -> "That transaction ID has already been used."
        "OFFLINE" -> "Connect to the internet and try again."
        else -> "That didn't work. Please try again."
    }
}
