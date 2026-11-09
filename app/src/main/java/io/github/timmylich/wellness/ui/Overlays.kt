/** Dialogs, bottom sheets and pickers (ports of Dialog, Sheet, Wheel, Slider, DateNav, DateStep). */
package io.github.timmylich.wellness.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.timmylich.wellness.lib.addDays
import io.github.timmylich.wellness.lib.humanDate
import io.github.timmylich.wellness.lib.todayKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// ---- dialogs ---------------------------------------------------------------------------

data class DialogButton(val text: String, val kind: String = "secondary", val onClick: (() -> Unit)? = null)

data class DialogOptions(val title: String, val message: String? = null, val icon: String? = null, val buttons: List<DialogButton> = emptyList())

/** App-styled replacement for the system alert; the single [DialogHost] shows the queue one at a time. */
object Dialogs {
  val queue = mutableStateListOf<DialogOptions>()
}

fun showDialog(title: String, message: String? = null, icon: String? = "info", buttons: List<DialogButton> = emptyList()) {
  Dialogs.queue += DialogOptions(title, message, icon, buttons)
}

/** Destructive confirmation: Cancel + a red action button. */
fun confirmDialog(title: String, message: String?, cancel: String, ok: String, icon: String = "trash", onOk: () -> Unit) =
  showDialog(title, message, icon, listOf(DialogButton(cancel), DialogButton(ok, "danger", onOk)))

@Composable
fun DialogHost() {
  val current = Dialogs.queue.firstOrNull() ?: return
  val close = { after: (() -> Unit)? ->
    Dialogs.queue.remove(current)
    after?.invoke()
  }
  Dialog(onDismissRequest = { close(null) }) {
    Column(
      Modifier.fillMaxWidth().background(c.card, RoundedCornerShape(24.dp)).border(1.dp, c.border, RoundedCornerShape(24.dp)).padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      if (current.icon != null) IconBadge(current.icon, 48.dp, 24.dp, 16.dp)
      T(current.title, size = 18, bold = true, center = true)
      if (current.message != null) T(current.message, dim = true, center = true)
      val buttons = current.buttons.ifEmpty { listOf(DialogButton(L["common.done"], "primary")) }
      Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (b in buttons) Button(b.text, Modifier.weight(1f), kind = b.kind) { close(b.onClick) }
      }
    }
  }
}

// ---- bottom sheet ----------------------------------------------------------------------

class SheetScope(val close: () -> Unit)

/**
 * Bottom sheet with a dimmed backdrop; drag down or tap outside to close. Compose it only while
 * open; call [SheetScope.close] to slide it out (then [onDismiss] runs).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(onDismiss: () -> Unit, content: @Composable SheetScope.() -> Unit) {
  val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()
  val dismiss by rememberUpdatedState(onDismiss)
  val sheet = remember { SheetScope { scope.launch { state.hide() }.invokeOnCompletion { dismiss() } } }
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = state,
    containerColor = c.card,
    shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 12.dp).size(44.dp, 5.dp).background(c.border, RoundedCornerShape(3.dp))) },
  ) {
    Column(
      Modifier.verticalScroll(rememberScrollState()).imePadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { sheet.content() }
  }
}

/** Sheet title. */
@Composable
fun SheetTitle(text: String) = T(text, size = 18, bold = true)

// ---- wheel picker ----------------------------------------------------------------------

private val ITEM_H = 44.dp
private const val VISIBLE = 5

fun range(from: Int, to: Int): List<Int> = (from..to).toList()

/** Vertical wheel (like the Android time picker): snaps to an item, the centred one is bright. */
@Composable
fun Wheel(values: List<Int>, value: Int, width: Dp = 84.dp, format: (Int) -> String = { it.toString() }, onChange: (Int) -> Unit) {
  val start = values.indexOf(value).coerceAtLeast(0)
  val list = rememberLazyListState(start)
  val haptic = LocalHapticFeedback.current
  val change by rememberUpdatedState(onChange)
  val current by rememberUpdatedState(value)
  val itemPx = with(LocalDensity.current) { ITEM_H.toPx() }
  LaunchedEffect(list) {
    snapshotFlow { list.isScrollInProgress }.distinctUntilChanged().collect { moving ->
      if (moving) return@collect
      val i = (list.firstVisibleItemIndex + (list.firstVisibleItemScrollOffset / itemPx).roundToInt()).coerceIn(0, values.size - 1)
      if (values[i] != current) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        change(values[i])
      }
    }
  }
  val pad = ITEM_H * ((VISIBLE - 1) / 2)
  Box(Modifier.width(width).height(ITEM_H * VISIBLE)) {
    // Selection lines above and below the centre row.
    val line = c.textDim
    Box(
      Modifier.padding(horizontal = 6.dp).offset(y = pad).fillMaxWidth().height(ITEM_H).drawBehind {
        val w = 2.dp.toPx()
        drawRect(line, size = androidx.compose.ui.geometry.Size(size.width, w))
        drawRect(line, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - w), size = androidx.compose.ui.geometry.Size(size.width, w))
      },
    )
    LazyColumn(
      state = list,
      flingBehavior = rememberSnapFlingBehavior(list),
      contentPadding = PaddingValues(vertical = pad),
      modifier = Modifier.fillMaxWidth().height(ITEM_H * VISIBLE),
    ) {
      items(values.size) { i ->
        val color = c.text
        Box(
          Modifier.fillMaxWidth().height(ITEM_H).graphicsLayer {
            // Distance of this row from the centre, in rows.
            val info = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == i }
            val center = (list.layoutInfo.viewportEndOffset + list.layoutInfo.viewportStartOffset) / 2f
            val d = if (info == null) 2f else abs((info.offset + info.size / 2f - center) / itemPx).coerceAtMost(2f)
            alpha = 1f - 0.375f * d
            val s = 1.1f - 0.15f * d
            scaleX = s
            scaleY = s
          },
          contentAlignment = Alignment.Center,
        ) {
          androidx.compose.foundation.text.BasicText(format(values[i]), style = TextStyle(color = color, fontSize = 22.sp, textAlign = TextAlign.Center))
        }
      }
    }
  }
}

// ---- slider ----------------------------------------------------------------------------

data class Zone(val from: Double, val to: Double, val color: Color)

/** Stepped slider with coloured zones and tick marks; [onComplete] fires once on release. */
@Composable
fun Slider(
  value: Double,
  min: Double,
  max: Double,
  step: Double,
  onChange: (Double) -> Unit,
  onComplete: ((Double) -> Unit)? = null,
  zones: List<Zone> = emptyList(),
  marks: List<Double> = emptyList(),
  haptics: Boolean = true,
) {
  val pal = c
  val haptic = LocalHapticFeedback.current
  var dragging by remember { mutableStateOf(false) }
  val cur by rememberUpdatedState(value)
  val change by rememberUpdatedState(onChange)
  val complete by rememberUpdatedState(onComplete)
  val thumb = 28.dp
  BoxWithConstraints(Modifier.fillMaxWidth().height(thumb + 12.dp)) {
    val d = LocalDensity.current
    val w = constraints.maxWidth.toFloat()
    val tPx = with(d) { thumb.toPx() }
    val track = (w - tPx).coerceAtLeast(1f)
    fun toX(v: Double) = ((v - min) / (max - min) * track).toFloat()
    Box(
      Modifier.fillMaxWidth().height(thumb + 12.dp).pointerInput(min, max, step, track) {
        // The slider keeps the whole gesture (pagers and scroll views do not take it over).
        awaitEachGesture {
          val down = awaitFirstDown()
          var last = cur
          fun apply(x: Float) {
            val raw = min + (x.coerceIn(0f, track) / track) * (max - min)
            val r = (Math.round(raw / step) * step * 10000).roundToInt() / 10000.0
            if (r != last) {
              last = r
              if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              change(r)
            }
          }
          dragging = true
          var x = down.position.x - tPx / 2
          down.consume()
          apply(x)
          while (true) {
            val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (!ch.pressed) break
            x += ch.positionChange().x
            ch.consume()
            apply(x)
          }
          dragging = false
          complete?.invoke(last)
        }
      },
    ) {
      androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(thumb + 12.dp)) {
        val cy = size.height / 2
        val left = tPx / 2
        val h = 8.dp.toPx()
        drawRoundRect(pal.cardAlt, androidx.compose.ui.geometry.Offset(left, cy - h / 2), androidx.compose.ui.geometry.Size(track, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
        for (z in zones) drawRect(z.color, androidx.compose.ui.geometry.Offset(left + toX(z.from), cy - h / 2), androidx.compose.ui.geometry.Size(toX(z.to) - toX(z.from), h))
        for (m in marks) drawRoundRect(pal.textDim, androidx.compose.ui.geometry.Offset(left + toX(m) - 1.dp.toPx(), cy - 9.dp.toPx()), androidx.compose.ui.geometry.Size(2.dp.toPx(), 18.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
        val x = left + toX(value)
        if (dragging) drawCircle(pal.accent.a(0.18f), tPx / 2 + 6.dp.toPx(), androidx.compose.ui.geometry.Offset(x, cy))
        val r = tPx / 2 * if (dragging) 1.15f else 1f
        drawCircle(pal.card, r, androidx.compose.ui.geometry.Offset(x, cy))
        drawCircle(pal.accent, r - 4.dp.toPx(), androidx.compose.ui.geometry.Offset(x, cy))
      }
    }
  }
}

// ---- dates -----------------------------------------------------------------------------

/** "‹ Today ›" day switcher used by per-day sections; the label slides in the travel direction. */
@Composable
fun DateNav(date: String, onChange: (String) -> Unit) {
  val isToday = date >= todayKey()
  val dict = L
  val on = LocalMotion.current
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.tap { onChange(addDays(date, -1)) }.padding(6.dp)) { Icon("left", color = c.accent, glow = true) }
    AnimatedContent(
      date,
      Modifier.weight(1f),
      transitionSpec = {
        val dir = if (targetState > initialState) 1 else -1
        (slideInHorizontally(anim(on, tween(260))) { it / 6 * dir } + fadeIn(anim(on, tween(260))))
          .togetherWith(slideOutHorizontally(anim(on, tween(260))) { -it / 6 * dir } + fadeOut(anim(on, tween(200))))
      },
      contentAlignment = Alignment.Center,
      label = "date",
    ) { d ->
      Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        T(humanDate(d, dict), Modifier.tap(scaleTo = 1f) { onChange(todayKey()) }, size = 16, bold = true)
      }
    }
    Box(Modifier.tap(enabled = !isToday) { onChange(addDays(date, 1)) }.padding(6.dp)) { Icon("right", color = c.accent, glow = !isToday) }
  }
}

/** Day picker with − / + steps around a readable date. */
@Composable
fun DateStep(label: String, value: String, onChange: (String) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    T(label, dim = true, size = 13)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Box(Modifier.tap { onChange(addDays(value, -1)) }.background(c.accentSoft, RoundedCornerShape(12.dp)).padding(10.dp)) { Icon("minus", 18.dp, c.accent) }
      T(humanDate(value, L), Modifier.weight(1f), bold = true, center = true)
      Box(Modifier.tap { onChange(addDays(value, 1)) }.background(c.accentSoft, RoundedCornerShape(12.dp)).padding(10.dp)) { Icon("plus", 18.dp, c.accent) }
    }
  }
}

/** Two-column grid helper used by settings and templates. */
@Composable
fun Grid2(items: Int, gap: Dp = 8.dp, cell: @Composable (Int, Modifier) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(gap)) {
    for (row in 0 until (items + 1) / 2) {
      Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
        cell(row * 2, Modifier.weight(1f).fillMaxWidth())
        if (row * 2 + 1 < items) cell(row * 2 + 1, Modifier.weight(1f).fillMaxWidth()) else Box(Modifier.weight(1f))
      }
    }
  }
}
