/**
 * Overview: sections in a horizontal pager, a floating section bar whose highlight follows the
 * swipe, and a "+" button that fans quick actions out above itself.
 */
package io.github.timmylich.wellness.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.timmylich.wellness.data.Store
import io.github.timmylich.wellness.data.WorkoutTemplate
import io.github.timmylich.wellness.ui.AppSheet
import io.github.timmylich.wellness.ui.BodyForm
import io.github.timmylich.wellness.ui.Button
import io.github.timmylich.wellness.ui.HeartForm
import io.github.timmylich.wellness.ui.Icon
import io.github.timmylich.wellness.ui.L
import io.github.timmylich.wellness.ui.PagerJump
import io.github.timmylich.wellness.ui.rememberEdgeSwipe
import io.github.timmylich.wellness.ui.rememberPagerJump
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import io.github.timmylich.wellness.ui.LocalMotion
import io.github.timmylich.wellness.ui.Nav
import io.github.timmylich.wellness.ui.Route
import io.github.timmylich.wellness.ui.SheetTitle
import io.github.timmylich.wellness.ui.StepsForm
import io.github.timmylich.wellness.ui.T
import io.github.timmylich.wellness.ui.TemplateGrid
import io.github.timmylich.wellness.ui.WaterForm
import io.github.timmylich.wellness.ui.WorkoutForm
import io.github.timmylich.wellness.ui.a
import io.github.timmylich.wellness.ui.c
import io.github.timmylich.wellness.ui.motion
import io.github.timmylich.wellness.ui.tap
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val TABS = listOf("summary" to "summary", "nutrition" to "nutrition", "water" to "water", "activity" to "activity", "heart" to "heart", "body" to "body")

private val ADD_ITEMS = listOf(
  Triple("food", "nutrition", "nutrition"), Triple("water", "water", "water"), Triple("workout", "workout", "activity"),
  Triple("steps", "activity", "activity"), Triple("heart", "heart", "heart"), Triple("body", "body", "body"),
)

/** The Overview section pager, for pagers nested inside sections. */
val LocalSections = compositionLocalOf<PagerJump?> { null }

/** Height of the floating section bar below the status bar. */
private val BAR_TOTAL = 54.dp

@Composable
fun Overview() {
  val settings = Store.data.settings
  val tabs = TABS.filter { it.first == "summary" || settings.section(it.first) }
  val pager = rememberPagerState { tabs.size }
  val jump = rememberPagerJump(pager)
  val outer = LocalTabs.current
  var adding by rememberSaveable { mutableStateOf<String?>(null) }
  val select = { id: String -> jump.go(tabs.indexOfFirst { it.first == id }) }
  // Swiping past the first or last section moves to the neighbouring bottom tab.
  val edge = rememberEdgeSwipe(outer)
  Box(Modifier.fillMaxSize().background(c.bg)) {
    CompositionLocalProvider(LocalSections provides jump) {
    HorizontalPager(pager, Modifier.fillMaxSize().nestedScroll(edge), beyondViewportPageCount = jump.beyond, key = { tabs[it].first }) { i ->
      Column(
        jump.page(i).fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = BAR_TOTAL + 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        when (tabs[i].first) {
          "nutrition" -> Nutrition()
          "water" -> Water()
          "activity" -> Activity()
          "heart" -> Heart()
          "body" -> Body()
          else -> Summary(select)
        }
      }
    }
    }
    // Pages scroll under the bar; only the tabs themselves have a background.
    Column(Modifier.fillMaxWidth()) {
      Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
      SectionBar(tabs, jump, select)
    }
    val items = ADD_ITEMS.filter { settings.section(it.third) }
    if (items.isNotEmpty()) FabMenu(items) { kind ->
      if (kind == "food") Nav.push(Route.FoodSearch()) else adding = kind
    }
  }
  adding?.let { kind -> QuickAdd(kind) { adding = null } }
}

/**
 * Floating section bar. Every tab is an icon; the active one also shows its label, and the
 * highlight pill slides and stretches between tabs following the swipe (or a tap jump).
 */
@Composable
private fun SectionBar(tabs: List<Pair<String, String>>, jump: PagerJump, onSelect: (String) -> Unit) {
  val pal = c
  val dict = L
  val d = LocalDensity.current
  val measurer = rememberTextMeasurer()
  val style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)
  // Natural width of each label.
  val labelW = remember(tabs, dict) { tabs.map { measurer.measure(dict["sections.${it.first}"], style).size.width.toFloat() } }
  val base = with(d) { 44.dp.toPx() } // padding 12 + icon 20 + padding 12
  val gap = with(d) { 2.dp.toPx() }
  val labelGap = with(d) { 6.dp.toPx() }
  val pad = with(d) { 4.dp.toPx() }
  val pos = jump.position
  // How "open" each tab is: 1 for the shown section, blending between neighbours mid-swipe.
  val open = tabs.indices.map { (1f - abs(pos - it)).coerceIn(0f, 1f) }
  val widths = tabs.indices.map { base + open[it] * (labelGap + labelW[it]) }
  val lefts = widths.runningFold(pad) { x, w -> x + w + gap }
  val total = lefts[tabs.size] - gap + pad
  val k = pos.toInt().coerceIn(0, tabs.size - 1)
  val f = (pos - k).coerceIn(0f, 1f)
  val k2 = (k + 1).coerceAtMost(tabs.size - 1)
  val pillX = lefts[k] + (lefts[k2] - lefts[k]) * f
  val pillW = widths[k] + (widths[k2] - widths[k]) * f
  // Solid colour (accent blended over the card), so overlaps never look darker.
  val pill = pal.accent.a(0.16f).compositeOver(pal.card)
  Box(Modifier.fillMaxWidth().padding(top = 8.dp, start = 12.dp, end = 12.dp), contentAlignment = Alignment.TopCenter) {
    Box(Modifier.size(with(d) { total.toDp() }, 46.dp).background(pal.card, CircleShape).border(1.dp, pal.border, CircleShape)) {
      Box(Modifier.offset { IntOffset(pillX.roundToInt(), pad.roundToInt()) }.size(with(d) { pillW.toDp() }, 38.dp).background(pill, CircleShape))
      tabs.forEachIndexed { i, (id, icon) ->
        Row(
          Modifier.offset { IntOffset(lefts[i].roundToInt(), pad.roundToInt()) }.size(with(d) { widths[i].toDp() }, 38.dp)
            .tap(label = dict["sections.$id"]) { onSelect(id) }.padding(start = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(icon, 20.dp, pal.accent, glow = true)
          // Fade quickly so a fading label never overlaps the next icon.
          val a = ((open[i] - 0.4f) / 0.6f).coerceIn(0f, 1f)
          if (a > 0f) T(dict["sections.$id"], Modifier.padding(start = 6.dp).graphicsLayer { alpha = a }, size = 14, bold = true, color = pal.accent, maxLines = 1)
        }
      }
    }
  }
}

private fun Color.compositeOver(under: Color): Color {
  val a = alpha
  return Color(red * a + under.red * (1 - a), green * a + under.green * (1 - a), blue * a + under.blue * (1 - a), 1f)
}

/** "+" button that fans quick actions out on an arc (or a grid for more than 4). */
@Composable
private fun FabMenu(items: List<Triple<String, String, String>>, onPick: (String) -> Unit) {
  val pal = c
  val dict = L
  var open by remember { mutableStateOf(false) }
  val p = remember { Animatable(0f) }
  val motionOn = LocalMotion.current
  LaunchedEffect(open) {
    if (!motionOn) p.snapTo(if (open) 1f else 0f)
    else if (open) p.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 300f))
    else p.animateTo(0f, tween(140))
  }
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val width = maxWidth.value
    val n = items.size
    val grid = n > 4
    val cols = 3
    val cellW = min(112f, (width - 32) / cols)
    val radius = min(135f, width / 2 - 40)
    val stepRad = if (n > 1) 140.0 / (n - 1) * PI / 180 else PI
    val labelW = if (grid) cellW - 8 else (2 * radius * sin(stepRad / 2) - 6).toFloat().coerceIn(56f, 96f)
    val rows = ceil(n / cols.toDouble()).toInt()
    val pos = items.indices.map { i ->
      if (grid) {
        val row = i / cols
        val inRow = min(cols, n - row * cols)
        Offset((i % cols - (inRow - 1) / 2f) * cellW, -(rows - row) * 104f)
      } else {
        val deg = if (n == 1) 90.0 else 160.0 - 140.0 * i / (n - 1)
        val rad = deg * PI / 180
        Offset((cos(rad) * radius).toFloat(), (-sin(rad) * radius + 6).toFloat())
      }
    }
    // Dim backdrop; tap to close.
    if (p.value > 0.001f) Box(
      Modifier.fillMaxSize().graphicsLayer { alpha = p.value * 0.45f }.background(Color.Black)
        .pointerInput(Unit) { detectTapGestures { open = false } },
    )
    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp), contentAlignment = Alignment.BottomCenter) {
      if (p.value > 0.001f) items.forEachIndexed { i, (kind, icon) ->
        Column(
          Modifier.width(labelW.dp).padding(bottom = 4.dp).graphicsLayer {
            val v = p.value
            translationX = pos[i].x.dp.toPx() * v
            translationY = pos[i].y.dp.toPx() * v
            alpha = v.coerceIn(0f, 1f)
            val s = 0.3f + 0.7f * v
            scaleX = s
            scaleY = s
          },
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Box(
            Modifier.size(54.dp).tap(label = dict["fab.$kind"]) {
              open = false
              onPick(kind)
            }.background(pal.card, CircleShape).border(1.5.dp, pal.accent.a(0.5f), CircleShape),
            contentAlignment = Alignment.Center,
          ) { Icon(icon, 24.dp, pal.accent) }
          T(dict["fab.$kind"], Modifier.padding(top = 4.dp), size = 11, bold = true, center = true, color = Color.White, maxLines = 3)
        }
      }
      Box(
        Modifier.size(62.dp).tap(label = dict["fab.add"]) { open = !open }.background(pal.accent, CircleShape),
        contentAlignment = Alignment.Center,
      ) { Icon("plus", 30.dp, Color.White, modifier = Modifier.graphicsLayer { rotationZ = 135f * p.value }) }
    }
  }
}

@Composable
private fun QuickAdd(kind: String, onDismiss: () -> Unit) {
  var template by remember { mutableStateOf<WorkoutTemplate?>(null) }
  var custom by remember { mutableStateOf(false) }
  val dict = L
  AppSheet(onDismiss) {
    when (kind) {
      "water" -> { SheetTitle(dict["fab.water"]); WaterForm(onDone = close) }
      "steps" -> { SheetTitle(dict["fab.steps"]); StepsForm(onDone = close) }
      "heart" -> { SheetTitle(dict["fab.heart"]); HeartForm(onDone = close) }
      "body" -> { SheetTitle(dict["fab.body"]); BodyForm(onDone = close) }
      "workout" -> if (template == null && !custom) {
        SheetTitle(dict["fab.workout"])
        TemplateGrid({ template = it })
        Button(dict["activity.custom"], Modifier.fillMaxWidth(), kind = "secondary", icon = "plus") { custom = true }
      } else WorkoutForm(template, onDone = close)
    }
  }
}
