package pk.parhlou.student.ads

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Ads on the free plan. They appear only between activities (after a finished quiz), never in a lesson, a paper, Ask Ustaad
 * or while answering. Tests and Plus students get [NoAds].
 */
interface Ads {
    /** Starts loading the first ad (called once the student is known to be on the free plan). */
    fun prepare() {}
    /** A quiz has just finished; may show an ad. */
    fun afterQuiz() {}
}

object NoAds : Ads

/** When an ad may show: every second finished quiz, at most once every 10 minutes, never in the first 3 minutes of use. */
class AdPacing(private val startedAt: Long, private val every: Int = 2, private val gapMs: Long = 10 * 60_000L, private val warmUpMs: Long = 3 * 60_000L) {
    private var quizzes = 0
    private var lastShown = Long.MIN_VALUE / 2
    fun shouldShow(now: Long): Boolean {
        quizzes++
        if (now - startedAt < warmUpMs || quizzes % every != 0 || now - lastShown < gapMs) return false
        lastShown = now
        return true
    }
}

/**
 * AdMob, set up for a children's app: child-directed, under the age of consent, G-rated ads only. That makes every ad
 * non-personalised; the advertising ID permission is removed in the manifest.
 */
class AdMobAds(private val activity: Activity, private val unitId: String) : Ads {
    private val pacing = AdPacing(System.currentTimeMillis())
    private var started = false
    private var ad: InterstitialAd? = null
    private var loading = false

    override fun prepare() {
        if (started) return
        started = true
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE)
                .setTagForUnderAgeOfConsent(RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE)
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
                .build()
        )
        runCatching { MobileAds.initialize(activity) { load() } }
    }

    private fun load() {
        if (loading || ad != null) return
        loading = true
        InterstitialAd.load(activity, unitId, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(loaded: InterstitialAd) { ad = loaded; loading = false }
            override fun onAdFailedToLoad(error: LoadAdError) { ad = null; loading = false }
        })
    }

    override fun afterQuiz() {
        if (!started) { prepare(); return }
        if (!pacing.shouldShow(System.currentTimeMillis())) { load(); return }
        val ready = ad ?: run { load(); return }
        ad = null
        ready.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = load()
            override fun onAdFailedToShowFullScreenContent(error: AdError) = load()
        }
        runCatching { ready.show(activity) }
    }
}
