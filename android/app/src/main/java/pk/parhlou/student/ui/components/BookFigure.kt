package pk.parhlou.student.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/** Figures cut from the textbook, shipped in the app (assets/figures/<id>.webp) so they work offline. */
object BookFigures {
    @Volatile private var alt: Map<String, String>? = null

    fun description(context: Context, id: String): String {
        val map = alt ?: runCatching {
            val o = JSONObject(context.assets.open("figures/index.json").bufferedReader().use { it.readText() })
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap()).also { alt = it }
        return map[id] ?: "Figure from your textbook"
    }

    fun load(context: Context, id: String): ImageBitmap? = runCatching {
        context.assets.open("figures/$id.webp").use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
    }.getOrNull()
}

/** A book figure on a white card (the drawings are black on white, so they stay readable in dark mode). */
@Composable
fun BookFigure(id: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val image = remember(id) { BookFigures.load(context, id) } ?: return
    val description = remember(id) { BookFigures.description(context, id) }
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), modifier = modifier.fillMaxWidth().testTag("figure-$id")) {
        Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
            Image(image, contentDescription = description, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp))
        }
    }
}
