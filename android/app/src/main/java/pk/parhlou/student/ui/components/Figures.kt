package pk.parhlou.student.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import pk.parhlou.student.domain.Expr
import pk.parhlou.student.ui.theme.LocalBrand
import pk.parhlou.student.ui.theme.Nunito
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** A figure as Ustaad sent it (already checked by the server): the app draws it exactly from these numbers. */
data class FigureSpec(
    val kind: String, val title: String, val description: String,
    val functions: List<String>, val shade: Shade?,
    val xMin: Double, val xMax: Double, val yMin: Double, val yMax: Double,
    val points: List<Pt>, val segments: List<Seg>, val polygons: List<List<String>>, val angles: List<Ang>, val circles: List<Circ>,
    val intervals: List<Interval>, val sets: List<VennSet>, val both: List<String>, val outside: List<String>,
    val categories: List<String>, val values: List<Double>, val headers: List<String>, val rows: List<List<String>>,
    /** Physics: arrows on a body (angle in degrees from +x, relative size), and circuit components. */
    val forces: List<Force> = emptyList(), val components: List<Part> = emptyList(), val parallel: Boolean = false,
) {
    data class Force(val label: String, val angle: Double, val size: Double)
    data class Part(val type: String, val label: String)
    data class Shade(val expression: String, val above: Boolean, val strict: Boolean)
    data class Pt(val x: Double, val y: Double, val label: String)
    data class Seg(val from: String, val to: String, val label: String)
    data class Ang(val at: String, val label: String, val right: Boolean)
    data class Circ(val center: String, val radius: Double, val label: String)
    data class Interval(val from: Double, val to: Double, val fromClosed: Boolean, val toClosed: Boolean, val label: String)
    data class VennSet(val name: String, val only: List<String>)

    companion object {
        private fun JSONArray?.strings() = this?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty()
        private fun JSONArray?.objects() = this?.let { a -> List(a.length()) { a.optJSONObject(it) }.filterNotNull() }.orEmpty()

        fun parse(j: JSONObject?): FigureSpec? = runCatching {
            j ?: return null
            val kind = j.getString("kind")
            if (kind !in setOf("graph", "number_line", "coordinate", "geometry", "venn", "bar", "pie", "table", "forces", "circuit")) return null
            FigureSpec(
                kind, j.optString("title"), j.optString("description"),
                j.optJSONArray("functions").strings(),
                j.optJSONObject("shade")?.let { Shade(it.getString("expression"), it.optString("side") == "above", it.optBoolean("strict")) },
                j.optDouble("xMin", -10.0), j.optDouble("xMax", 10.0), j.optDouble("yMin", -10.0), j.optDouble("yMax", 10.0),
                j.optJSONArray("points").objects().map { Pt(it.getDouble("x"), it.getDouble("y"), it.optString("label")) },
                j.optJSONArray("segments").objects().map { Seg(it.getString("from"), it.getString("to"), it.optString("label")) },
                j.optJSONArray("polygons")?.let { a -> List(a.length()) { a.optJSONArray(it).strings() } }.orEmpty(),
                j.optJSONArray("angles").objects().map { Ang(it.getString("at"), it.optString("label"), it.optBoolean("right")) },
                j.optJSONArray("circles").objects().map { Circ(it.getString("center"), it.getDouble("radius"), it.optString("label")) },
                j.optJSONArray("intervals").objects().map { Interval(it.getDouble("from"), it.getDouble("to"), it.optBoolean("fromClosed"), it.optBoolean("toClosed"), it.optString("label")) },
                j.optJSONArray("sets").objects().map { VennSet(it.getString("name"), it.optJSONArray("only").strings()) },
                j.optJSONArray("both").strings(), j.optJSONArray("outside").strings(),
                j.optJSONArray("categories").strings(), j.optJSONArray("values")?.let { a -> List(a.length()) { a.getDouble(it) } }.orEmpty(),
                j.optJSONArray("headers").strings(), j.optJSONArray("rows")?.let { a -> List(a.length()) { a.optJSONArray(it).strings() } }.orEmpty(),
                j.optJSONArray("forces").objects().map { Force(it.optString("label"), it.getDouble("angle"), it.getDouble("size")) },
                j.optJSONArray("components").objects().map { Part(it.getString("type"), it.optString("label")) },
                j.optString("arrangement") == "parallel",
            )
        }.getOrNull()
    }
}

/** Draws a figure in a white card with its caption. Screen readers hear the description. */
@Composable
fun FigureView(f: FigureSpec, modifier: Modifier = Modifier) {
    val brand = LocalBrand.current
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val palette = remember(brand) { brand.units.map { it.first } }
    val c = Colors(ink, muted, brand.line, palette, brand.sun)
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
            .border(2.dp, brand.line, RoundedCornerShape(16.dp)).padding(12.dp)
            .semantics { contentDescription = f.description.ifBlank { f.title.ifBlank { "Figure" } } }.testTag("figure-${f.kind}"),
    ) {
        if (f.title.isNotBlank()) Text(f.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
        when (f.kind) {
            "table" -> FigureTable(f)
            "number_line" -> Canvas(Modifier.fillMaxWidth().height(110.dp)) { numberLine(f, c, measurer) }
            "bar" -> Canvas(Modifier.fillMaxWidth().height(230.dp)) { bars(f, c, measurer) }
            "pie" -> Canvas(Modifier.fillMaxWidth().height(220.dp)) { pie(f, c, measurer) }
            "venn" -> Canvas(Modifier.fillMaxWidth().aspectRatio(1.35f)) { venn(f, c, measurer) }
            "geometry" -> Canvas(Modifier.fillMaxWidth().aspectRatio(1.2f)) { geometry(f, c, measurer) }
            "forces" -> Canvas(Modifier.fillMaxWidth().aspectRatio(1.2f)) { forces(f, c, measurer) }
            "circuit" -> Canvas(Modifier.fillMaxWidth().aspectRatio(1.5f)) { circuit(f, c, measurer) }
            else -> Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) { plane(f, c, measurer) }
        }
    }
}

private data class Colors(val ink: Color, val muted: Color, val grid: Color, val series: List<Color>, val sun: Color)

private fun DrawScope.label(m: TextMeasurer, text: String, at: Offset, color: Color, size: Int = 12, bold: Boolean = false, center: Boolean = false) {
    if (text.isBlank()) return
    val layout = m.measure(text, TextStyle(fontFamily = Nunito, fontSize = size.sp, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Bold, color = color))
    val tl = if (center) Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f) else at
    drawText(layout, topLeft = Offset(tl.x.coerceIn(0f, max(0f, size().width - layout.size.width)), tl.y.coerceIn(0f, max(0f, size().height - layout.size.height))))
}
private fun DrawScope.size() = this.size

/** A "nice" grid step for a range: 1, 2 or 5 × 10ⁿ, about 8–12 lines across. */
private fun step(range: Double): Double {
    val raw = range / 10
    val p = 10.0.pow(floor(log10(raw)))
    return listOf(1.0, 2.0, 5.0, 10.0).map { it * p }.first { it >= raw }
}

/** Graphs and coordinate figures: grid, axes with numbers, functions, shading, segments, polygons and labelled points. */
private fun DrawScope.plane(f: FigureSpec, c: Colors, m: TextMeasurer) {
    val pad = 8.dp.toPx()
    // Make sure every point is inside the view.
    var x0 = f.xMin; var x1 = f.xMax; var y0 = f.yMin; var y1 = f.yMax
    f.points.forEach { x0 = min(x0, it.x - 1); x1 = max(x1, it.x + 1); y0 = min(y0, it.y - 1); y1 = max(y1, it.y + 1) }
    val w = size.width - 2 * pad; val h = size.height - 2 * pad
    fun sx(x: Double) = (pad + (x - x0) / (x1 - x0) * w).toFloat()
    fun sy(y: Double) = (pad + (y1 - y) / (y1 - y0) * h).toFloat()
    val sxStep = step(x1 - x0); val syStep = step(y1 - y0)
    var gx = ceil(x0 / sxStep) * sxStep
    while (gx <= x1) { drawLine(c.grid, Offset(sx(gx), pad), Offset(sx(gx), size.height - pad), 1f); gx += sxStep }
    var gy = ceil(y0 / syStep) * syStep
    while (gy <= y1) { drawLine(c.grid, Offset(pad, sy(gy)), Offset(size.width - pad, sy(gy)), 1f); gy += syStep }
    // Axes (or the frame edge when the axis is off-screen) with numbers.
    val ax = sy(0.0.coerceIn(y0, y1)); val ay = sx(0.0.coerceIn(x0, x1))
    drawLine(c.ink, Offset(pad, ax), Offset(size.width - pad, ax), 2.5f)
    drawLine(c.ink, Offset(ay, pad), Offset(ay, size.height - pad), 2.5f)
    gx = ceil(x0 / sxStep) * sxStep
    while (gx <= x1) { if (abs(gx) > 1e-9) label(m, fmt(gx), Offset(sx(gx) - 6, ax + 3), c.muted, 10); gx += sxStep }
    gy = ceil(y0 / syStep) * syStep
    while (gy <= y1) { if (abs(gy) > 1e-9) label(m, fmt(gy), Offset(ay + 4, sy(gy) - 8), c.muted, 10); gy += syStep }
    label(m, "x", Offset(size.width - pad - 10, ax - 18), c.ink, 12, true)
    label(m, "y", Offset(ay + 6, pad), c.ink, 12, true)

    // Everything plotted stays inside the frame.
    clipRect(pad, pad, size.width - pad, size.height - pad) {
    // Shaded inequality region, with a dashed boundary for < and >.
    f.shade?.let { s ->
        Expr.parse(s.expression)?.let { fn ->
            val n = 240
            val path = Path()
            val edge = if (s.above) sy(y1) else sy(y0)
            path.moveTo(sx(x0), edge)
            for (i in 0..n) {
                val x = x0 + (x1 - x0) * i / n
                val y = fn(x).takeIf { it.isFinite() }?.coerceIn(y0 - 1, y1 + 1) ?: if (s.above) y0 else y1
                path.lineTo(sx(x), sy(y))
            }
            path.lineTo(sx(x1), edge); path.close()
            drawPath(path, c.series[0].copy(alpha = 0.18f))
            plot(fn, x0, x1, y0, y1, ::sx, ::sy, c.series[0], if (s.strict) PathEffect.dashPathEffect(floatArrayOf(16f, 12f)) else null)
        }
    }
    f.functions.forEachIndexed { i, e -> Expr.parse(e)?.let { plot(it, x0, x1, y0, y1, ::sx, ::sy, c.series[(i + 1) % c.series.size], null) } }
    }
    f.functions.forEachIndexed { i, e -> label(m, "y = ${pretty(e)}", Offset(pad + 6, pad + 4 + i * 18f), c.series[(i + 1) % c.series.size], 12, true) }

    val byLabel = f.points.associateBy { it.label }
    f.polygons.forEach { poly ->
        val pts = poly.mapNotNull { byLabel[it] }
        if (pts.size >= 3) {
            val path = Path().apply { moveTo(sx(pts[0].x), sy(pts[0].y)); pts.drop(1).forEach { lineTo(sx(it.x), sy(it.y)) }; close() }
            drawPath(path, c.series[1].copy(alpha = 0.15f)); drawPath(path, c.series[1], style = Stroke(3f))
        }
    }
    f.segments.forEach { s ->
        val a = byLabel[s.from] ?: return@forEach; val b = byLabel[s.to] ?: return@forEach
        drawLine(c.series[1], Offset(sx(a.x), sy(a.y)), Offset(sx(b.x), sy(b.y)), 3f)
        label(m, s.label, Offset((sx(a.x) + sx(b.x)) / 2 + 4, (sy(a.y) + sy(b.y)) / 2 + 4), c.ink, 11)
    }
    f.points.forEach { p ->
        drawCircle(c.series[2], 6f, Offset(sx(p.x), sy(p.y)))
        // A label that is already the coordinates ("(0, −3)") is shown once.
        val coords = "(${fmt(p.x)}, ${fmt(p.y)})"
        label(m, when { p.label.isBlank() -> coords; p.label.trimStart().startsWith("(") -> p.label; else -> "${p.label}$coords" },
            Offset(sx(p.x) + 8, sy(p.y) - 20), c.ink, 11, true)
    }
}

private fun DrawScope.plot(fn: (Double) -> Double, x0: Double, x1: Double, y0: Double, y1: Double,
                           sx: (Double) -> Float, sy: (Double) -> Float, color: Color, effect: PathEffect?) {
    val n = 400
    var path = Path(); var open = false; var lastY = Double.NaN
    for (i in 0..n) {
        val x = x0 + (x1 - x0) * i / n
        val y = fn(x)
        // Break the line where the function is undefined or jumps off the view (e.g. 1/x at 0).
        val ok = y.isFinite() && y > y0 - (y1 - y0) && y < y1 + (y1 - y0) && !(lastY.isFinite() && abs(y - lastY) > (y1 - y0))
        if (ok) { if (open) path.lineTo(sx(x), sy(y)) else { path.moveTo(sx(x), sy(y)); open = true } }
        else if (open) { drawPath(path, color, style = Stroke(4f, pathEffect = effect)); path = Path(); open = false }
        lastY = y
    }
    if (open) drawPath(path, color, style = Stroke(4f, pathEffect = effect))
}

/** Triangles and other shapes: fitted to the space (no axes), with side labels, angle marks and circles. */
private fun DrawScope.geometry(f: FigureSpec, c: Colors, m: TextMeasurer) {
    if (f.points.isEmpty()) return
    val pad = 28.dp.toPx()
    var x0 = f.points.minOf { it.x }; var x1 = f.points.maxOf { it.x }; var y0 = f.points.minOf { it.y }; var y1 = f.points.maxOf { it.y }
    val byLabel0 = f.points.associateBy { it.label }
    f.circles.forEach { ci -> byLabel0[ci.center]?.let { p -> x0 = min(x0, p.x - ci.radius); x1 = max(x1, p.x + ci.radius); y0 = min(y0, p.y - ci.radius); y1 = max(y1, p.y + ci.radius) } }
    val span = max(x1 - x0, y1 - y0).coerceAtLeast(1e-6)
    val scale = min((size.width - 2 * pad) / span, (size.height - 2 * pad) / span).toFloat()
    val ox = (size.width - (x1 - x0).toFloat() * scale) / 2; val oy = (size.height - (y1 - y0).toFloat() * scale) / 2
    fun p(pt: FigureSpec.Pt) = Offset(ox + ((pt.x - x0) * scale).toFloat(), size.height - oy - ((pt.y - y0) * scale).toFloat())
    val byLabel = byLabel0
    val centre = Offset(f.points.map { p(it).x }.average().toFloat(), f.points.map { p(it).y }.average().toFloat())
    f.circles.forEach { ci -> byLabel[ci.center]?.let { drawCircle(c.series[3], (ci.radius * scale).toFloat(), p(it), style = Stroke(3f)) } }
    f.polygons.forEach { poly ->
        val pts = poly.mapNotNull { byLabel[it] }.map(::p)
        if (pts.size >= 3) {
            val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }
            drawPath(path, c.series[0].copy(alpha = 0.12f)); drawPath(path, c.series[0], style = Stroke(4f))
        }
    }
    f.segments.forEach { s ->
        val a = byLabel[s.from]?.let(::p) ?: return@forEach; val b = byLabel[s.to]?.let(::p) ?: return@forEach
        drawLine(c.series[0], a, b, 4f)
        val mid = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
        val out = (mid - centre).let { d -> val l = hypot(d.x, d.y).coerceAtLeast(1f); Offset(d.x / l, d.y / l) }
        label(m, s.label, mid + out * 18f, c.ink, 12, true, center = true)
    }
    f.angles.forEach { a ->
        val at = byLabel[a.at]?.let(::p) ?: return@forEach
        // The two sides that meet at this vertex (from segments or polygons).
        val nbrs = (f.segments.flatMap { s -> listOfNotNull(if (s.from == a.at) s.to else null, if (s.to == a.at) s.from else null) } +
            f.polygons.flatMap { poly -> poly.indices.filter { poly[it] == a.at }.flatMap { i -> listOf(poly[(i + poly.size - 1) % poly.size], poly[(i + 1) % poly.size]) } })
            .distinct().mapNotNull { byLabel[it]?.let(::p) }.take(2)
        if (nbrs.size == 2) {
            val u = (nbrs[0] - at).let { it / hypot(it.x, it.y) }; val v = (nbrs[1] - at).let { it / hypot(it.x, it.y) }
            if (a.right) {
                val r = 16f
                val path = Path().apply { moveTo(at.x + u.x * r, at.y + u.y * r); lineTo(at.x + (u.x + v.x) * r, at.y + (u.y + v.y) * r); lineTo(at.x + v.x * r, at.y + v.y * r) }
                drawPath(path, c.ink, style = Stroke(2.5f))
            } else {
                val r = 26f
                val s1 = Math.toDegrees(atan2(u.y.toDouble(), u.x.toDouble())).toFloat(); val s2 = Math.toDegrees(atan2(v.y.toDouble(), v.x.toDouble())).toFloat()
                var sweep = s2 - s1; if (sweep > 180) sweep -= 360; if (sweep < -180) sweep += 360
                drawArc(c.series[2], s1, sweep, false, topLeft = Offset(at.x - r, at.y - r), size = Size(2 * r, 2 * r), style = Stroke(3f))
                val bis = (u + v).let { val l = hypot(it.x, it.y).coerceAtLeast(0.01f); it / l }
                label(m, a.label, at + bis * 44f, c.series[2], 11, true, center = true)
            }
        }
    }
    f.points.forEach { pt ->
        val o = p(pt)
        drawCircle(c.ink, 5f, o)
        val out = (o - centre).let { d -> val l = hypot(d.x, d.y).coerceAtLeast(1f); Offset(d.x / l, d.y / l) }
        label(m, pt.label, o + out * 18f, c.ink, 14, true, center = true)
    }
}

private fun DrawScope.numberLine(f: FigureSpec, c: Colors, m: TextMeasurer) {
    val finite = (f.intervals.flatMap { listOf(it.from, it.to) } + f.points.map { it.x }).filter { abs(it) < 1e8 }
    var x0 = min(f.xMin, (finite.minOrNull() ?: 0.0) - 2); var x1 = max(f.xMax, (finite.maxOrNull() ?: 0.0) + 2)
    if (f.xMin == -10.0 && f.xMax == 10.0 && finite.isNotEmpty()) { x0 = finite.min() - 3; x1 = finite.max() + 3 }
    val pad = 16.dp.toPx(); val y = size.height * 0.62f
    fun sx(x: Double) = (pad + (x.coerceIn(x0, x1) - x0) / (x1 - x0) * (size.width - 2 * pad)).toFloat()
    drawLine(c.ink, Offset(pad - 8, y), Offset(size.width - pad + 8, y), 3f)
    val st = step(x1 - x0).coerceAtLeast(1.0)
    var t = ceil(x0 / st) * st
    while (t <= x1) { drawLine(c.ink, Offset(sx(t), y - 7), Offset(sx(t), y + 7), 2f); label(m, fmt(t), Offset(sx(t), y + 18), c.muted, 11, center = true); t += st }
    f.intervals.forEachIndexed { i, iv ->
        val col = c.series[i % c.series.size]; val yy = y - 22f - i * 16f
        val a = sx(iv.from); val b = sx(iv.to)
        drawLine(col, Offset(a, yy), Offset(b, yy), 7f)
        if (abs(iv.from) < 1e8) drawCircle(if (iv.fromClosed) col else Color.White, 8f, Offset(a, yy)).also { drawCircle(col, 8f, Offset(a, yy), style = Stroke(3f)) }
        else drawLine(col, Offset(a, yy), Offset(a - 2, yy), 7f)
        if (abs(iv.to) < 1e8) drawCircle(if (iv.toClosed) col else Color.White, 8f, Offset(b, yy)).also { drawCircle(col, 8f, Offset(b, yy), style = Stroke(3f)) }
        label(m, iv.label, Offset((a + b) / 2, yy - 18), col, 12, true, center = true)
    }
    f.points.forEach { p -> drawCircle(c.ink, 7f, Offset(sx(p.x), y)); label(m, p.label, Offset(sx(p.x), y - 24), c.ink, 12, true, center = true) }
}

private fun DrawScope.venn(f: FigureSpec, c: Colors, m: TextMeasurer) {
    val pad = 10.dp.toPx()
    drawRect(c.ink, Offset(pad, pad), Size(size.width - 2 * pad, size.height - 2 * pad), style = Stroke(2.5f))
    label(m, "U", Offset(pad + 8, pad + 6), c.ink, 13, true)
    val n = f.sets.size.coerceIn(1, 3)
    val r = min(size.width, size.height) * (if (n == 3) 0.24f else 0.28f)
    val cx = size.width / 2; val cy = size.height / 2 + (if (n == 3) r * 0.25f else 0f)
    val centres = when (n) {
        1 -> listOf(Offset(cx, cy))
        2 -> listOf(Offset(cx - r * 0.6f, cy), Offset(cx + r * 0.6f, cy))
        else -> listOf(Offset(cx - r * 0.6f, cy - r * 0.15f), Offset(cx + r * 0.6f, cy - r * 0.15f), Offset(cx, cy - r * 1.15f))
    }
    centres.forEachIndexed { i, o -> drawCircle(c.series[i].copy(alpha = 0.16f), r, o); drawCircle(c.series[i], r, o, style = Stroke(3.5f)) }
    f.sets.take(n).forEachIndexed { i, s ->
        val o = centres[i]
        val away = when { n == 1 -> Offset(0f, 0f); n == 2 -> Offset(if (i == 0) -r * 0.45f else r * 0.45f, 0f)
            else -> listOf(Offset(-r * 0.45f, r * 0.2f), Offset(r * 0.45f, r * 0.2f), Offset(0f, -r * 0.45f))[i] }
        label(m, s.name, o + Offset(0f, -r - 14f) + Offset(away.x * 0.6f, if (n == 3 && i == 2) -6f else 0f), c.series[i], 16, true, center = true)
        label(m, s.only.joinToString(", "), o + away, c.ink, 13, true, center = true)
    }
    if (n >= 2) label(m, f.both.joinToString(", "), Offset(cx, centres.take(n).map { it.y }.average().toFloat() + if (n == 3) r * 0.15f else 0f), c.ink, 13, true, center = true)
    label(m, f.outside.joinToString(", "), Offset(size.width - pad - 8, size.height - pad - 22).let { Offset(it.x - 60, it.y) }, c.muted, 12, true)
}

private fun DrawScope.bars(f: FigureSpec, c: Colors, m: TextMeasurer) {
    val n = min(f.categories.size, f.values.size); if (n == 0) return
    val top = (f.values.take(n).maxOrNull() ?: 1.0).coerceAtLeast(1e-9)
    val left = 34.dp.toPx(); val bottom = size.height - 26.dp.toPx(); val topY = 16.dp.toPx()
    drawLine(c.ink, Offset(left, topY), Offset(left, bottom), 2.5f); drawLine(c.ink, Offset(left, bottom), Offset(size.width, bottom), 2.5f)
    val st = step(top)
    var t = st
    while (t <= top * 1.0001) { val y = (bottom - (t / top) * (bottom - topY)).toFloat(); drawLine(c.grid, Offset(left, y), Offset(size.width, y), 1f); label(m, fmt(t), Offset(4f, y - 8), c.muted, 10); t += st }
    val slot = (size.width - left) / n
    for (i in 0 until n) {
        val v = f.values[i]; val h = ((v / top) * (bottom - topY)).toFloat().coerceAtLeast(0f)
        val x = left + slot * i + slot * 0.18f; val bw = slot * 0.64f
        drawRect(c.series[i % c.series.size], Offset(x, bottom - h), Size(bw, h))
        label(m, fmt(v), Offset(x + bw / 2, bottom - h - 10), c.ink, 11, true, center = true)
        label(m, f.categories[i], Offset(x + bw / 2, bottom + 12), c.ink, 11, center = true)
    }
}

private fun DrawScope.pie(f: FigureSpec, c: Colors, m: TextMeasurer) {
    val n = min(f.categories.size, f.values.size); val total = f.values.take(n).sum(); if (n == 0 || total <= 0) return
    val r = min(size.height / 2 - 8f, size.width * 0.28f); val o = Offset(r + 12f, size.height / 2)
    var start = -90f
    for (i in 0 until n) {
        val sweep = (f.values[i] / total * 360).toFloat()
        drawArc(c.series[i % c.series.size], start, sweep, true, topLeft = o - Offset(r, r), size = Size(2 * r, 2 * r))
        start += sweep
    }
    for (i in 0 until n) {
        val y = 14f + i * 26f; val x = o.x + r + 24f
        drawRect(c.series[i % c.series.size], Offset(x, y), Size(16f, 16f))
        label(m, "${f.categories[i]}  ${fmt(f.values[i])} (${fmt(f.values[i] / total * 100)}%)", Offset(x + 24f, y - 2f), c.ink, 12, true)
    }
}

@Composable
private fun FigureTable(f: FigureSpec) {
    val line = LocalBrand.current.line
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        f.headers.indices.forEach { col ->
            Column(Modifier.widthIn(min = 64.dp)) {
                Text(f.headers[col], style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer).border(1.dp, line).padding(horizontal = 10.dp, vertical = 8.dp).fillMaxWidth())
                f.rows.forEach { row ->
                    Text(row.getOrElse(col) { "" }, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.border(1.dp, line).padding(horizontal = 10.dp, vertical = 8.dp).fillMaxWidth())
                }
            }
        }
    }
}

/** "2*x-3" → "2x − 3", "x^2" → "x²", "sqrt(x)" → "√(x)": how the student writes it. */
fun pretty(e: String): String {
    val sup = mapOf('0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹')
    return e.replace(Regex("""\^(\d)""")) { sup[it.groupValues[1][0]].toString() }
        .replace(Regex("""(\d)\*([a-z(])""")) { it.groupValues[1] + it.groupValues[2] }
        .replace("*", "×").replace("sqrt", "√").replace("pi", "π")
        .replace(Regex("""(?<=[\w)²³])([+\-])""")) { " ${if (it.value == "-") "−" else "+"} " }
}

private fun fmt(v: Double): String = if (abs(v - Math.round(v)) < 1e-9) Math.round(v).toString() else "%.2f".format(v).trimEnd('0').trimEnd('.')

private operator fun Offset.div(d: Float) = Offset(x / d, y / d)
@Suppress("unused") private fun Rect.unused() = Unit

// ── Physics ─────────────────────────────────────────────────────────────────────────────────────────────────────

/** A free-body (or vector) diagram: the body in the middle and each force as a labelled arrow. */
private fun DrawScope.forces(f: FigureSpec, c: Colors, m: TextMeasurer) {
    val centre = Offset(size.width / 2, size.height / 2)
    val half = minOf(size.width, size.height) * 0.09f
    drawRect(c.muted.copy(alpha = 0.18f), Offset(centre.x - half, centre.y - half), Size(half * 2, half * 2))
    drawRect(c.ink, Offset(centre.x - half, centre.y - half), Size(half * 2, half * 2), style = Stroke(3f))
    val biggest = f.forces.maxOf { it.size }.takeIf { it > 0 } ?: 1.0
    val reach = minOf(size.width, size.height) * 0.42f
    f.forces.forEachIndexed { i, force ->
        val a = Math.toRadians(force.angle)
        val dir = Offset(kotlin.math.cos(a).toFloat(), -kotlin.math.sin(a).toFloat())
        val start = centre + dir * half
        // Lengths follow the sizes, but even a small force is long enough to read.
        val len = half + (reach - half) * (0.35f + 0.65f * (force.size / biggest).toFloat())
        val tip = centre + dir * len
        val colour = c.series[i % c.series.size]
        drawLine(colour, start, tip, strokeWidth = 5f)
        val back = dir * -16f
        val side = Offset(-dir.y, dir.x) * 9f
        drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(tip.x + back.x + side.x, tip.y + back.y + side.y); lineTo(tip.x + back.x - side.x, tip.y + back.y - side.y); close() }, colour)
        // Sideways arrows need room for the label's width.
        if (force.label.isNotBlank()) label(m, force.label, tip + dir * (18f + 34f * kotlin.math.abs(dir.x)), c.ink, 13, bold = true, center = true)
    }
}

/** A circuit diagram: a series loop, or the source with one component per parallel branch. */
private fun DrawScope.circuit(f: FigureSpec, c: Colors, m: TextMeasurer) {
    val w = size.width; val h = size.height
    val wire = { a: Offset, b: Offset -> drawLine(c.ink, a, b, strokeWidth = 3f) }
    val source = f.components.filter { it.type == "cell" || it.type == "battery" }
    val others = f.components.filter { it.type != "cell" && it.type != "battery" }
    val left = w * 0.1f; val right = w * 0.9f; val top = h * 0.18f; val bottom = h * 0.85f
    if (!f.parallel) {
        // Source on the bottom wire, everything else spread along the top and the right side.
        val ordered = source + others
        val topParts = ordered.drop(source.size).let { it.take(maxOf(1, (it.size + 1) / 2)) }
        val sideParts = ordered.drop(source.size + topParts.size)
        wire(Offset(left, top), Offset(right, top)); wire(Offset(right, top), Offset(right, bottom))
        wire(Offset(right, bottom), Offset(left, bottom)); wire(Offset(left, bottom), Offset(left, top))
        source.forEachIndexed { i, p -> part(p, Offset(left + (right - left) * (i + 1) / (source.size + 1), bottom), false, c, m) }
        topParts.forEachIndexed { i, p -> part(p, Offset(left + (right - left) * (i + 1) / (topParts.size + 1), top), false, c, m) }
        sideParts.forEachIndexed { i, p -> part(p, Offset(right, top + (bottom - top) * (i + 1) / (sideParts.size + 1)), true, c, m) }
    } else {
        // Source on the left; each other component on its own branch between two rails.
        val railL = w * 0.32f
        val n0 = maxOf(1, others.size)
        val lastX = railL + (right - railL) * (n0 - 0.5f) / n0
        wire(Offset(left, top), Offset(lastX, top)); wire(Offset(left, bottom), Offset(lastX, bottom))
        wire(Offset(left, top), Offset(left, bottom))
        source.forEachIndexed { i, p -> part(p, Offset(left, top + (bottom - top) * (i + 1) / (source.size + 1)), true, c, m) }
        val n = maxOf(1, others.size)
        others.forEachIndexed { i, p ->
            val x = railL + (right - railL) * (i + 0.5f) / n
            wire(Offset(x, top), Offset(x, bottom))
            part(p, Offset(x, (top + bottom) / 2), true, c, m)
        }
    }
}

/** One circuit symbol centred on a wire (the wire is hidden under it); vertical = the wire runs up and down. */
private fun DrawScope.part(p: FigureSpec.Part, at: Offset, vertical: Boolean, c: Colors, m: TextMeasurer) {
    val bg = Color.White
    val s = minOf(size.width, size.height) * 0.07f
    fun pt(along: Float, across: Float) = if (vertical) Offset(at.x + across, at.y + along) else Offset(at.x + along, at.y + across)
    val gap = when (p.type) { "battery" -> s * 1.3f; "resistor", "coil" -> s * 1.2f; else -> s }
    // Clear the wire under the symbol.
    drawLine(bg, pt(-gap, 0f), pt(gap, 0f), strokeWidth = 6f)
    val ink = c.ink
    when (p.type) {
        "cell", "battery" -> {
            val cells = if (p.type == "battery") 2 else 1
            for (k in 0 until cells) {
                val o = (k - (cells - 1) / 2f) * s * 0.9f
                drawLine(bg, pt(o - s * 0.3f, 0f), pt(o + s * 0.3f, 0f), strokeWidth = 8f)
                drawLine(ink, pt(o - s * 0.25f, -s * 0.8f), pt(o - s * 0.25f, s * 0.8f), strokeWidth = 4f)   // long plate (+)
                drawLine(ink, pt(o + s * 0.25f, -s * 0.4f), pt(o + s * 0.25f, s * 0.4f), strokeWidth = 7f)     // short plate (−)
            }
            drawLine(ink, pt(-gap, 0f), pt(-s * 0.25f - (cells - 1) * s * 0.45f, 0f), strokeWidth = 3f)
            drawLine(ink, pt(s * 0.25f + (cells - 1) * s * 0.45f, 0f), pt(gap, 0f), strokeWidth = 3f)
        }
        "resistor" -> {
            val r = if (vertical) Rect(at.x - s * 0.35f, at.y - s, at.x + s * 0.35f, at.y + s) else Rect(at.x - s, at.y - s * 0.35f, at.x + s, at.y + s * 0.35f)
            drawRect(bg, r.topLeft, r.size); drawRect(ink, r.topLeft, r.size, style = Stroke(3f))
            drawLine(ink, pt(-gap, 0f), pt(-s, 0f), strokeWidth = 3f); drawLine(ink, pt(s, 0f), pt(gap, 0f), strokeWidth = 3f)
        }
        "coil" -> {
            for (k in 0 until 4) drawArc(ink, 180f, 180f, false, topLeft = pt(-s + k * s * 0.5f, -s * 0.3f).let { if (vertical) Offset(at.x - s * 0.3f, it.y) else it },
                size = if (vertical) Size(s * 0.6f, s * 0.5f) else Size(s * 0.5f, s * 0.6f), style = Stroke(3f))
            drawLine(ink, pt(-gap, 0f), pt(-s, 0f), strokeWidth = 3f); drawLine(ink, pt(s, 0f), pt(gap, 0f), strokeWidth = 3f)
        }
        "switch" -> {
            drawCircle(ink, 4f, pt(-s * 0.7f, 0f)); drawCircle(ink, 4f, pt(s * 0.7f, 0f))
            drawLine(ink, pt(-s * 0.7f, 0f), pt(s * 0.6f, -s * 0.55f), strokeWidth = 3f)
            drawLine(ink, pt(-gap, 0f), pt(-s * 0.7f, 0f), strokeWidth = 3f); drawLine(ink, pt(s * 0.7f, 0f), pt(gap, 0f), strokeWidth = 3f)
        }
        else -> { // bulb, ammeter, voltmeter: a circle
            drawCircle(bg, s * 0.62f, at); drawCircle(ink, s * 0.62f, at, style = Stroke(3f))
            when (p.type) {
                "bulb" -> { val d = s * 0.42f; drawLine(ink, Offset(at.x - d, at.y - d), Offset(at.x + d, at.y + d), strokeWidth = 3f); drawLine(ink, Offset(at.x - d, at.y + d), Offset(at.x + d, at.y - d), strokeWidth = 3f) }
                "ammeter" -> label(m, "A", at, ink, 15, bold = true, center = true)
                "voltmeter" -> label(m, "V", at, ink, 15, bold = true, center = true)
            }
        }
    }
    if (p.label.isNotBlank()) label(m, p.label, if (vertical) Offset(at.x + s + 16f, at.y) else Offset(at.x, at.y - s - 14f), c.ink, 12, center = true)
}
