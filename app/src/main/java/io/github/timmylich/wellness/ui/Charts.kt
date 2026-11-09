/**
 * Canvas charts: bar chart, line chart, range gauge and
 * progress ring. Tap a chart to show the value of a point, or press and hold to scrub: a value
 * bubble follows the finger. Charts glide from the previous data instead of replaying from zero.
 */
package io.github.timmylich.wellness.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.timmylich.wellness.lib.Range
import io.github.timmylich.wellness.lib.rangeStatus
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

data class Band(val from: Double, val to: Double, val color: Color)

data class BarDatum(val label: String, val value: Double, val color: Color? = null)

data class Series(val values: List<Double?>, val color: Color)

private const val HOLD_MS = 180L
private val PAD_L = 36.dp
private val PAD_B = 18.dp
private val PAD_T = 8.dp

/** Round up to a "nice" axis maximum. */
private fun niceMax(v: Double): Double {
  if (v <= 0) return 1.0
  val p = 10.0.pow(floor(log10(v)))
  val n = v / p
  return (if (n <= 1) 1.0 else if (n <= 2) 2.0 else if (n <= 5) 5.0 else 10.0) * p
}

/**
 * Glides a list of numbers from what is shown to [target] (no animation on mount). NaN marks a
 * gap and is never interpolated.
 */
@Composable
private fun morph(target: List<Double>, ms: Int = 450): List<Double> {
  val motionOn = LocalMotion.current
  val st = remember { object { var from = target; var to = target } }
  val t = remember { Animatable(1f) }
  if (st.to != target) {
    // Start from what is on screen now.
    val shown = blend(st.from, st.to, t.value)
    st.from = shown
    st.to = target
  }
  LaunchedEffect(target) {
    if (!motionOn) return@LaunchedEffect t.snapTo(1f)
    t.snapTo(0f)
    t.animateTo(1f, tween(ms, easing = FastOutSlowInEasing))
  }
  return blend(st.from, st.to, t.value)
}

private fun blend(from: List<Double>, to: List<Double>, t: Float): List<Double> = to.mapIndexed { i, v ->
  val f = from.getOrNull(i)
  if (v.isNaN() || f == null || f.isNaN()) v else f + (v - f) * t
}

/**
 * Chart touch: a tap selects the point under the finger and keeps it shown (tap it again to hide);
 * press-and-hold scrubs, following the finger until release. [onX] gets the finger x in px,
 * or null when the selection ends; [onTap] gets the x of a tap.
 */
private fun Modifier.scrub(key: Any?, onX: (Float?) -> Unit, onTap: (Float) -> Unit): Modifier = pointerInput(key) {
  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    // "moved" (a scroll or swipe: left to the parents), "tap", or null when the hold fired.
    val outcome = withTimeoutOrNull(HOLD_MS) {
      var result = "moved"
      while (true) {
        val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
        if (!ch.pressed) {
          result = "tap"
          ch.consume()
          break
        }
        if (ch.positionChange().getDistance() > viewConfiguration.touchSlop / 2 || ch.isConsumed) break
      }
      result
    }
    if (outcome == "tap") return@awaitEachGesture onTap(down.position.x)
    if (outcome != null) return@awaitEachGesture
    onX(down.position.x)
    while (true) {
      val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
      if (!ch.pressed) {
        ch.consume()
        break
      }
      // Keep the gesture: pagers and scroll views must not take it while scrubbing.
      ch.consume()
      onX(ch.position.x)
    }
    onX(null)
  }
}

/** Value bubble above the selected point. */
@Composable
private fun Bubble(x: Float, y: Float, width: Float, label: String, value: String, color: Color) {
  val d = LocalDensity.current
  val bg = if (c.dark) c.cardAlt else Color(0xFF1F2427)
  val wPx = with(d) { 150.dp.toPx() }
  val hPx = with(d) { 32.dp.toPx() }
  val left = (x - wPx / 2).coerceIn(0f, max(0f, width - wPx))
  val top = max(0f, y - hPx - with(d) { 14.dp.toPx() })
  Box(Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }.width(150.dp), contentAlignment = Alignment.Center) {
    Row(
      Modifier.height(32.dp).background(bg, RoundedCornerShape(16.dp)).border(1.dp, if (c.dark) c.border else bg, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      T(value, size = 13, bold = true, color = color, maxLines = 1)
      T(label, size = 12, color = if (c.dark) c.textDim else Color(0xFFAEB8BC), maxLines = 1)
    }
  }
}

private fun DrawScope.dashed(from: Offset, to: Offset, color: Color, width: Float, on: Float, off: Float) =
  drawLine(color, from, to, width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(on, off)))

@Composable
fun BarChart(
  data: List<BarDatum>,
  height: Dp = 160.dp,
  target: Double? = null,
  bands: List<Band> = emptyList(),
  unit: String = "",
  maxY: Double? = null,
  format: ((Double) -> String)? = null,
) {
  val pal = c
  val measurer = rememberTextMeasurer()
  val raw = maxY ?: niceMax(
    maxOf(data.maxOfOrNull { it.value } ?: 0.0, target ?: 0.0, bands.maxOfOrNull { b -> min(b.to, if ((target ?: 0.0) > 0) target!! * 1.5 else b.to) } ?: 0.0) * 1.1,
  )
  val m = morph(data.map { it.value } + raw, 500)
  val max = m.last().takeIf { it > 0 } ?: raw
  var finger by remember { mutableStateOf<Float?>(null) }
  var pinned by remember { mutableStateOf<Int?>(null) }
  val label = TextStyle(color = pal.textDim, fontSize = 10.sp)
  BoxWithConstraints(Modifier.fillMaxWidth().height(height)) {
    val d = LocalDensity.current
    val w = constraints.maxWidth.toFloat()
    val padL = with(d) { PAD_L.toPx() }
    val padB = with(d) { PAD_B.toPx() }
    val padT = with(d) { PAD_T.toPx() }
    val h = constraints.maxHeight.toFloat()
    val ch = h - padB - padT
    fun y(v: Double) = (padT + ch - (min(v, max) / (if (max == 0.0) 1.0 else max)) * ch).toFloat()
    val slot = if (data.isNotEmpty()) (w - padL) / data.size else 0f
    // With a year of bars a slot is under 2 dp: bars then touch instead of overlapping.
    val barW = (slot * 0.6f).coerceIn(min(slot, with(d) { 2.dp.toPx() }), with(d) { 28.dp.toPx() })
    val every = ceil(data.size / 8.0).toInt().coerceAtLeast(1)
    fun idx(x: Float) = ((x - padL) / (if (slot == 0f) 1f else slot)).toInt().coerceIn(0, max(0, data.size - 1))
    val sel = (finger?.let(::idx) ?: pinned?.takeIf { it < data.size })?.takeIf { data.isNotEmpty() }
    Canvas(Modifier.fillMaxWidth().height(height).scrub(w to data.size, { finger = it }) { x -> idx(x).let { pinned = if (pinned == it) null else it } }) {
      for (b in bands) drawRect(b.color, Offset(padL, y(b.to)), Size(w - padL, max(0f, y(b.from) - y(b.to))))
      for (f in listOf(0.0, 0.5, 1.0)) {
        val yy = y(max * f)
        if (f > 0) dashed(Offset(padL, yy), Offset(w, yy), pal.border, 1.dp.toPx(), 2.dp.toPx(), 4.dp.toPx())
        val t = measurer.measure(fmt(max * f), label)
        drawText(t, topLeft = Offset(padL - 4.dp.toPx() - t.size.width, yy - t.size.height / 2f))
      }
      data.forEachIndexed { i, b ->
        val x = padL + slot * i + (slot - barW) / 2
        val v = max(0.0, m[i])
        drawRoundRect(
          b.color ?: pal.accent, Offset(x, y(v)), Size(barW, max(0f, y(0.0) - y(v))),
          CornerRadius(min(4.dp.toPx(), barW / 2)), alpha = if (sel == null || sel == i) 1f else 0.45f,
        )
        if (i % every == 0) {
          val t = measurer.measure(b.label, label)
          drawText(t, topLeft = Offset(x + barW / 2 - t.size.width / 2f, h - t.size.height))
        }
      }
      if (target != null) dashed(Offset(padL, y(target)), Offset(w, y(target)), pal.accent.a(0.8f), 1.5.dp.toPx(), 6.dp.toPx(), 5.dp.toPx())
      if (sel != null) {
        val cx = padL + slot * sel + slot / 2
        drawCircle(pal.card, 5.dp.toPx(), Offset(cx, y(data[sel].value)))
        drawCircle(data[sel].color ?: pal.accent, 5.dp.toPx(), Offset(cx, y(data[sel].value)), style = Stroke(2.5.dp.toPx()))
      }
    }
    if (sel != null) {
      val v = data[sel].value
      Bubble(padL + slot * sel + slot / 2, y(v), w, data[sel].label, format?.invoke(v) ?: (fmt(v) + if (unit.isNotEmpty()) " $unit" else ""), data[sel].color ?: pal.accent)
    }
    if (unit.isNotEmpty()) T(unit, Modifier.offset(y = (-2).dp), dim = true, size = 10)
  }
}

/** Smooth path through points (Catmull-Rom as cubic Béziers). */
private fun smoothPath(pts: List<Offset>): Path = Path().apply {
  moveTo(pts[0].x, pts[0].y)
  for (i in 0 until pts.size - 1) {
    val p0 = pts.getOrElse(i - 1) { pts[i] }
    val p1 = pts[i]
    val p2 = pts[i + 1]
    val p3 = pts.getOrElse(i + 2) { p2 }
    cubicTo(p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6, p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6, p2.x, p2.y)
  }
}

@Composable
fun LineChart(
  labels: List<String>,
  series: List<Series>,
  bands: List<Band> = emptyList(),
  height: Dp = 180.dp,
  minY: Double? = null,
  maxY: Double? = null,
  format: (Double) -> String = { fmt(it, 1) },
) {
  val pal = c
  val measurer = rememberTextMeasurer()
  val all = series.flatMap { s -> s.values.filterNotNull() }
  var lo0 = minY ?: all.minOrNull() ?: 0.0
  var hi0 = maxY ?: all.maxOrNull() ?: 1.0
  if (hi0 - lo0 < 1) { lo0 -= 1; hi0 += 1 }
  val pad = (hi0 - lo0) * 0.1
  if (minY == null) lo0 -= pad
  if (maxY == null) hi0 += pad
  val flat = morph(series.flatMap { s -> s.values.map { it ?: Double.NaN } } + listOf(lo0, hi0))
  var finger by remember { mutableStateOf<Float?>(null) }
  var pinned by remember { mutableStateOf<Int?>(null) }
  if (all.isEmpty()) {
    T(L["common.noData"], Modifier.fillMaxWidth(), dim = true, center = true)
    return
  }
  // Axis bounds glide too, but never collapse (zero range gives NaN coordinates).
  val lo = flat[flat.size - 2].takeIf { it.isFinite() } ?: lo0
  val hi = flat.last().takeIf { it.isFinite() && it - lo > 0.5 } ?: hi0
  var off = 0
  val shown = series.map { s -> Series(s.values.indices.map { i -> flat[off + i].takeUnless { it.isNaN() } }, s.color).also { off += s.values.size } }
  val label = TextStyle(color = pal.textDim, fontSize = 10.sp)
  BoxWithConstraints(Modifier.fillMaxWidth().height(height)) {
    val d = LocalDensity.current
    val w = constraints.maxWidth.toFloat()
    val h = constraints.maxHeight.toFloat()
    val padL = with(d) { PAD_L.toPx() }
    val padR = with(d) { 6.dp.toPx() }
    val padB = with(d) { PAD_B.toPx() }
    val padT = with(d) { PAD_T.toPx() }
    val cw = w - padL - padR
    val ch = h - padB - padT
    fun x(i: Int) = padL + if (labels.size <= 1) cw / 2 else i.toFloat() / (labels.size - 1) * cw
    fun y(v: Double) = (padT + ch - ((v.coerceIn(lo, hi) - lo) / (if (hi == lo) 1.0 else hi - lo)) * ch).toFloat()
    val every = ceil(labels.size / 7.0).toInt().coerceAtLeast(1)
    fun idx(x: Float) = if (labels.size <= 1) 0 else (((x - padL) / (if (cw == 0f) 1f else cw)) * (labels.size - 1)).roundToInt().coerceIn(0, labels.size - 1)
    val sel = finger?.let(::idx) ?: pinned?.takeIf { it < labels.size }
    Canvas(Modifier.fillMaxWidth().height(height).scrub(w to labels.size, { finger = it }) { x -> idx(x).let { pinned = if (pinned == it) null else it } }) {
      for (b in bands) drawRect(b.color, Offset(padL, y(b.to)), Size(cw, max(0f, y(b.from) - y(b.to))))
      for (f in listOf(0.0, 0.5, 1.0)) {
        val v = lo + (hi - lo) * f
        if (f > 0) dashed(Offset(padL, y(v)), Offset(w - padR, y(v)), pal.border, 1.dp.toPx(), 2.dp.toPx(), 4.dp.toPx())
        val t = measurer.measure(fmt(v, 1), label)
        drawText(t, topLeft = Offset(padL - 4.dp.toPx() - t.size.width, y(v) - t.size.height / 2f))
      }
      labels.forEachIndexed { i, l ->
        if (i % every != 0) return@forEachIndexed
        val t = measurer.measure(l, label)
        drawText(t, topLeft = Offset(x(i) - t.size.width / 2f, h - t.size.height))
      }
      for (s in shown) {
        // Smooth segments broken at gaps; single points get a dot.
        val segs = mutableListOf(mutableListOf<Offset>())
        s.values.forEachIndexed { i, v ->
          if (v == null) {
            if (segs.last().isNotEmpty()) segs += mutableListOf<Offset>()
          } else segs.last() += Offset(x(i), y(v))
        }
        for (seg in segs) when {
          seg.size > 1 -> drawPath(smoothPath(seg), s.color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
          seg.size == 1 -> drawCircle(s.color, 3.dp.toPx(), seg[0])
        }
      }
      if (sel != null) {
        val first = series.firstNotNullOfOrNull { it.values.getOrNull(sel) } ?: lo
        dashed(Offset(x(sel), y(first)), Offset(x(sel), padT + ch), pal.textDim, 2.dp.toPx(), 4.dp.toPx(), 4.dp.toPx())
        for (s in series) {
          val v = s.values.getOrNull(sel) ?: continue
          drawCircle(s.color.a(0.3f), 11.dp.toPx(), Offset(x(sel), y(v)))
          drawCircle(Color.White, 6.dp.toPx(), Offset(x(sel), y(v)))
          drawCircle(s.color, 6.dp.toPx(), Offset(x(sel), y(v)), style = Stroke(3.dp.toPx()))
        }
      }
    }
    if (sel != null) {
      val vals = series.mapNotNull { it.values.getOrNull(sel) }
      Bubble(
        x(sel), y(vals.firstOrNull() ?: lo), w, labels[sel], vals.joinToString(" / ") { format(it) }.ifEmpty { "—" },
        series.firstOrNull { it.values.getOrNull(sel) != null }?.color ?: pal.accent,
      )
    }
  }
}

/**
 * Horizontal gauge for a nutrient range: low zone, recommended zone, danger zone above max,
 * a target tick and the current value.
 */
@Composable
fun RangeBar(value: Double, range: Range, height: Dp = 10.dp) {
  val pal = c
  val scale = maxOf(range.max * 1.25, value * 1.05, 1.0)
  val status = rangeStatus(value, range)
  val marker = pal.tone(if (status == "low") "low" else if (status == "high") "danger" else "ok")
  val lowEnd = if (range.lowerIsBetter) 0.0 else range.min
  val frac by animateFloatAsState((min(value, scale) / scale).toFloat(), motion(tween(600, easing = FastOutSlowInEasing)), label = "range")
  Canvas(Modifier.fillMaxWidth().height(height + 8.dp)) {
    val w = size.width
    val hh = height.toPx()
    fun px(v: Double) = (min(v, scale) / scale * w).toFloat()
    val track = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 4.dp.toPx(), w, 4.dp.toPx() + hh, CornerRadius(hh / 2))) }
    clipPath(track) {
      val top = 4.dp.toPx()
      drawRect(pal.cardAlt, Offset(0f, top), Size(w, hh))
      if (lowEnd > 0) drawRect(pal.low.a(0.35f), Offset(0f, top), Size(px(lowEnd), hh))
      drawRect(pal.ok.a(0.3f), Offset(px(lowEnd), top), Size(px(range.max) - px(lowEnd), hh))
      drawRect(pal.danger.a(0.3f), Offset(px(range.max), top), Size(w - px(range.max), hh))
      drawRect(marker.a(0.85f), Offset(0f, top), Size(frac * w, hh))
    }
    if (!range.lowerIsBetter) drawLine(pal.text, Offset(px(range.target), 0f), Offset(px(range.target), size.height), 2.dp.toPx(), StrokeCap.Round)
  }
}

@Composable
fun Ring(value: Double, max: Double, size: Dp = 180.dp, stroke: Dp = 16.dp, color: Color, content: @Composable ColumnScope.() -> Unit = {}) {
  val pal = c
  val frac by animateFloatAsState(if (max > 0) min(1.0, value / max).toFloat() else 0f, motion(tween(700, easing = FastOutSlowInEasing)), label = "ring")
  Box(Modifier.size(size), contentAlignment = Alignment.Center) {
    Canvas(Modifier.size(size)) {
      val s = stroke.toPx()
      val inset = s / 2
      val arc = Size(this.size.width - s, this.size.height - s)
      drawArc(pal.cardAlt, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(s))
      if (frac > 0f) drawArc(color, -90f, 360f * frac, false, Offset(inset, inset), arc, style = Stroke(s, cap = StrokeCap.Round))
    }
    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
  }
}
