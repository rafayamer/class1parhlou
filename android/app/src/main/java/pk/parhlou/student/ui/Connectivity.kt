package pk.parhlou.student.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pk.parhlou.student.ui.components.BigButton
import pk.parhlou.student.ui.components.Sheet

/** Parhlou works online: Ustaad, marking and the course all come from the internet. */
object Connectivity {
    fun isOnline(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)
}

/** True while the phone has an internet connection; follows changes as they happen. */
@Composable
fun rememberOnline(): Boolean {
    val context = LocalContext.current
    var online by remember { mutableStateOf(Connectivity.isOnline(context)) }
    DisposableEffect(context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online = true }
            override fun onLost(network: Network) { online = Connectivity.isOnline(context) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { online = Connectivity.isOnline(context) }
        }
        runCatching { cm?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { cm?.unregisterNetworkCallback(callback) } }
    }
    return online
}

/** Covers the app (blurred behind) until the internet is back; it goes away by itself when the phone reconnects. */
@Composable
fun OfflineCover(onRetry: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .testTag("offline-cover"),
        contentAlignment = Alignment.Center,
    ) {
        Sheet(Modifier.padding(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("📶", style = MaterialTheme.typography.displaySmall)
                Text("Connect to the internet", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Text("Parhlou needs the internet for Ustaad, your papers and your progress. Turn on Wi-Fi or mobile data.",
                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                BigButton("Try again", onClick = onRetry)
            }
        }
    }
}
