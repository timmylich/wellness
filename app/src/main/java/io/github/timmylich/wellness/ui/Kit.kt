/** Shared UI primitives. Small and theme-aware; screens compose them. */
package io.github.timmylich.wellness.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.timmylich.wellness.data.Store

/**
 * Springy scale-down on press (opacity change when motion is off); no ripple.
 * Used by every tappable element for consistent feedback.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tap(
  enabled: Boolean = true,
  scaleTo: Float = 0.96f,
  label: String? = null,
  onLongClick: (() -> Unit)? = null,
  onClick: () -> Unit,
): Modifier {
  val source = remember { MutableInteractionSource() }
  val pressed by source.collectIsPressedAsState()
  val motionOn = LocalMotion.current
  val scale by animateFloatAsState(
    if (pressed && motionOn) scaleTo else 1f,
    spring(dampingRatio = if (pressed) 1f else 0.55f, stiffness = Spring.StiffnessHigh),
    label = "tap",
  )
  return this
    .graphicsLayer {
      scaleX = scale
      scaleY = scale
      alpha = if (!enabled) 0.4f else if (pressed && !motionOn) 0.7f else 1f
    }
    .combinedClickable(source, indication = null, enabled = enabled, role = Role.Button, onLongClick = onLongClick, onClick = onClick)
    .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
}

@Composable
fun T(
  text: String,
  modifier: Modifier = Modifier,
  size: Int = 15,
  dim: Boolean = false,
  bold: Boolean = false,
  color: Color? = null,
  center: Boolean = false,
  maxLines: Int = Int.MAX_VALUE,
  spacing: Float = 0f,
) {
  androidx.compose.foundation.text.BasicText(
    text,
    modifier,
    style = TextStyle(
      color = color ?: if (dim) c.textDim else c.text,
      fontSize = size.sp,
      fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
      textAlign = if (center) TextAlign.Center else TextAlign.Start,
      lineHeight = (size * 1.35f).sp,
      letterSpacing = spacing.sp,
    ),
    maxLines = maxLines,
    overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
  )
}

/** A piece of [TSpans]: unset fields inherit the paragraph style. */
data class Span(val text: String, val color: Color? = null, val size: Int? = null, val bold: Boolean = false, val dim: Boolean = false)

/** Text with differently styled pieces. */
@Composable
fun TSpans(vararg spans: Span, modifier: Modifier = Modifier, size: Int = 15, center: Boolean = false) {
  val pal = c
  val text = androidx.compose.ui.text.buildAnnotatedString {
    for (sp in spans) {
      pushStyle(
        androidx.compose.ui.text.SpanStyle(
          color = sp.color ?: if (sp.dim) pal.textDim else pal.text,
          fontSize = (sp.size ?: size).sp,
          fontWeight = if (sp.bold) FontWeight.Bold else FontWeight.Normal,
        ),
      )
      append(sp.text)
      pop()
    }
  }
  androidx.compose.foundation.text.BasicText(
    text, modifier,
    style = TextStyle(color = pal.text, fontSize = size.sp, lineHeight = (size * 1.35f).sp, textAlign = if (center) TextAlign.Center else TextAlign.Start),
  )
}

@Composable
fun Card(modifier: Modifier = Modifier, padding: Dp = 16.dp, gap: Dp = 10.dp, vertical: Dp = padding, content: @Composable ColumnScope.() -> Unit) {
  Column(
    modifier
      .fillMaxWidth()
      .background(c.card, RoundedCornerShape(18.dp))
      .border(0.5.dp, c.border, RoundedCornerShape(18.dp))
      .padding(horizontal = padding, vertical = vertical),
    verticalArrangement = Arrangement.spacedBy(gap),
    content = content,
  )
}

@Composable
fun Icon(name: String, size: Dp = 22.dp, color: Color, glow: Boolean = false, modifier: Modifier = Modifier) {
  val res = ICONS[name] ?: ICONS.getValue("info")
  val k = if (c.dark) 1f else 0.45f
  Image(
    painterResource(res),
    contentDescription = null,
    colorFilter = ColorFilter.tint(color),
    modifier = modifier
      .size(size)
      .then(
        if (!glow) Modifier
        else Modifier.drawBehind {
          // Soft radial halo; a dark halo on a light background reads as a smudge, so keep it subtle there.
          val r = this.size.minDimension
          drawCircle(
            Brush.radialGradient(
              0f to color.a(0.35f * k), 0.55f to color.a(0.12f * k), 1f to Color.Transparent,
              center = center, radius = r,
            ),
            radius = r,
          )
        },
      ),
  )
}

/** Card header. [right] drops below the title when both do not fit on one line. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun H(title: String, icon: String? = null, right: (@Composable () -> Unit)? = null) {
  FlowRow(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalArrangement = Arrangement.spacedBy(8.dp),
    itemVerticalAlignment = Alignment.CenterVertically,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      if (icon != null) Icon(icon, 18.dp, c.accent, glow = true)
      T(title, size = 18, bold = true)
    }
    right?.invoke()
  }
}

/** Wrapping row (RN rows wrap by default). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Wrap(gap: Dp = 8.dp, modifier: Modifier = Modifier, center: Boolean = false, fill: Boolean = true, content: @Composable FlowRowScope.() -> Unit) {
  FlowRow(
    if (fill) modifier.fillMaxWidth() else modifier,
    horizontalArrangement = if (center) Arrangement.spacedBy(gap, Alignment.CenterHorizontally) else Arrangement.spacedBy(gap),
    verticalArrangement = Arrangement.spacedBy(gap),
    itemVerticalAlignment = Alignment.CenterVertically,
    content = content,
  )
}

@Composable
fun HRow(gap: Dp = 8.dp, modifier: Modifier = Modifier, align: Alignment.Vertical = Alignment.CenterVertically, content: @Composable RowScope.() -> Unit) {
  Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = align, content = content)
}

/** primary | secondary | danger | ghost */
@Composable
fun Button(
  title: String,
  modifier: Modifier = Modifier,
  kind: String = "primary",
  icon: String? = null,
  big: Boolean = false,
  subtitle: String? = null,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  val bg = when (kind) {
    "secondary" -> c.accentSoft
    "danger" -> c.danger
    "ghost" -> Color.Transparent
    else -> c.accent
  }
  val fg = if (kind == "primary" || kind == "danger") Color.White else c.accent
  Row(
    modifier
      .tap(enabled, onClick = onClick)
      .background(bg, RoundedCornerShape(14.dp))
      .padding(horizontal = 16.dp, vertical = if (big) 16.dp else 12.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) Icon(icon, if (big) 20.dp else 18.dp, fg)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      androidx.compose.foundation.text.BasicText(title, style = TextStyle(color = fg, fontWeight = FontWeight.SemiBold, fontSize = if (big) 17.sp else 15.sp, textAlign = TextAlign.Center))
      if (subtitle != null) androidx.compose.foundation.text.BasicText(subtitle, style = TextStyle(color = fg.a(0.8f), fontSize = 12.sp, textAlign = TextAlign.Center))
    }
  }
}

@Composable
fun Chip(label: String, active: Boolean = false, icon: String? = null, onClick: () -> Unit) {
  Row(
    Modifier
      .tap(onClick = onClick)
      .background(if (active) c.accentSoft else c.card, RoundedCornerShape(20.dp))
      .border(1.dp, if (active) c.accent else c.border, RoundedCornerShape(20.dp))
      .padding(horizontal = 14.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    if (icon != null) Icon(icon, 16.dp, c.accent)
    androidx.compose.foundation.text.BasicText(
      label,
      style = TextStyle(color = if (active) c.accent else c.text, fontSize = 15.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal),
      maxLines = 1,
    )
  }
}

/** Single-choice selector rendered as a row of chips. */
@Composable
fun <V> Segmented(value: V, options: List<Pair<V, String>>, onChange: (V) -> Unit) {
  // Only as wide as the chips, so it fits next to a header title.
  Wrap(fill = false) {
    for ((v, label) in options) Chip(label, active = v == value) { onChange(v) }
  }
}

/** Keeps digits and one decimal point; a comma counts as the point. */
fun numericInput(v: String): String = v.replace(',', '.').filter { it.isDigit() || it == '.' }

fun parseNum(s: String): Double? = s.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it.isFinite() }

@Composable
fun Field(
  value: String,
  onChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  label: String? = null,
  suffix: String? = null,
  numeric: Boolean = true,
  placeholder: String? = null,
  hint: String? = null,
  search: Boolean = false,
  multiline: Boolean = false,
) {
  var focused by remember { mutableStateOf(false) }
  Column(modifier.widthIn(min = 120.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    if (label != null) T(label, dim = true, size = 13)
    Row(
      Modifier
        .fillMaxWidth()
        .background(c.cardAlt, RoundedCornerShape(12.dp))
        .border(1.dp, if (focused) c.accent else c.border, RoundedCornerShape(12.dp))
        .padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Box(Modifier.weight(1f).padding(vertical = 10.dp).heightIn(min = if (multiline) 96.dp else 0.dp)) {
        if (value.isEmpty() && placeholder != null) T(placeholder, dim = true, size = 16, maxLines = 1)
        BasicTextField(
          value,
          { onChange(if (numeric) numericInput(it) else it) },
          Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
          singleLine = !multiline,
          textStyle = TextStyle(color = c.text, fontSize = 16.sp),
          cursorBrush = SolidColor(c.accent),
          keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
            // Search fields: no autocorrect, so keyboards deliver every character immediately.
            autoCorrectEnabled = !search,
            imeAction = if (search) ImeAction.Search else ImeAction.Default,
          ),
        )
      }
      if (suffix != null) T(suffix, dim = true)
    }
    if (hint != null) T(hint, dim = true, size = 12)
  }
}

/** Row with a label and an app-styled switch; the whole row is tappable. */
@Composable
fun Toggle(label: String, value: Boolean, hint: String? = null, onChange: (Boolean) -> Unit) {
  Row(
    Modifier.fillMaxWidth().tap(scaleTo = 0.99f) { onChange(!value) }.semantics { contentDescription = label },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f).padding(end = 12.dp)) {
      T(label)
      if (hint != null) T(hint, dim = true, size = 12)
    }
    Switcher(value)
  }
}

@Composable
private fun Switcher(value: Boolean) {
  val p by animateFloatAsState(if (value) 1f else 0f, motion(spring(dampingRatio = 0.6f, stiffness = 500f)), label = "switch")
  Box(
    Modifier
      .size(46.dp, 28.dp)
      .background(c.cardAlt, CircleShape)
      .border(1.dp, c.border, CircleShape),
    contentAlignment = Alignment.CenterStart,
  ) {
    Box(Modifier.matchParentSizeCompat().graphicsLayer { alpha = p.coerceIn(0f, 1f) }.background(c.accent, CircleShape))
    Box(
      Modifier
        .padding(start = 2.dp)
        .offset(x = (18 * p).dp)
        .scale(if (p < 0.5f) 0.86f - 0.12f * p else 0.8f + 0.4f * (p - 0.5f))
        .size(22.dp)
        .background(Color.White, CircleShape),
    )
  }
}

private fun Modifier.matchParentSizeCompat() = this.then(Modifier.size(46.dp, 28.dp))

/** Down-chevron that rotates to "up" when open. */
@Composable
fun Chevron(open: Boolean, color: Color, size: Dp = 18.dp) {
  val r by animateFloatAsState(if (open) 180f else 0f, motion(tween(200)), label = "chevron")
  Icon("down", size, color, modifier = Modifier.rotate(r))
}

/** Expand/collapse with height and fade. */
@Composable
fun Collapse(open: Boolean, content: @Composable ColumnScope.() -> Unit) {
  val motionOn = LocalMotion.current
  AnimatedVisibility(
    open,
    enter = if (motionOn) expandVertically(tween(240)) + fadeIn(tween(240)) else fadeIn(tween(0)),
    exit = if (motionOn) shrinkVertically(tween(180)) + fadeOut(tween(180)) else fadeOut(tween(0)),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
  }
}

/** Collapsible explanation card; hidden when the user turns hints off unless [force]. */
@Composable
fun Hint(title: String, force: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
  if (!Store.data.settings.showHints && !force) return
  var open by rememberSaveable { mutableStateOf(false) }
  Column(
    Modifier.fillMaxWidth().tap(scaleTo = 1f) { open = !open }.background(c.accentSoft, RoundedCornerShape(14.dp)).padding(12.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Icon("info", 18.dp, c.accent)
      T(title, Modifier.weight(1f), color = c.accent, bold = true)
      Chevron(open, c.accent)
    }
    Collapse(open) {
      Spacer(Modifier.height(2.dp))
      content()
    }
  }
}

@Composable
fun Hint(title: String, text: String, force: Boolean = false) = Hint(title, force) { T(text, size = 14) }

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, unit: String? = null, color: Color? = null) {
  Column(modifier.widthIn(min = 90.dp)) {
    T(label, dim = true, size = 12)
    // The unit is smaller and dimmer than the value.
    TSpans(Span(value, color, bold = true), *(if (unit != null) arrayOf(Span(" $unit", size = 13, dim = true)) else emptyArray()), size = 20)
  }
}

/** Stats in one wrapping row, sharing the width. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Stats(content: @Composable FlowRowScope.() -> Unit) = FlowRow(
  Modifier.fillMaxWidth(),
  horizontalArrangement = Arrangement.spacedBy(8.dp),
  verticalArrangement = Arrangement.spacedBy(8.dp),
  content = content,
)

@Composable
fun Empty(text: String) = T(text, Modifier.fillMaxWidth().padding(vertical = 12.dp), dim = true, center = true)

@Composable
fun Divider() = Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(0.5.dp).background(c.border))

/** List row; tap edits, long-press offers deletion. */
@Composable
fun ListItem(
  title: String,
  subtitle: String? = null,
  left: (@Composable () -> Unit)? = null,
  right: (@Composable () -> Unit)? = null,
  onDelete: (() -> Unit)? = null,
  onClick: () -> Unit = {},
) {
  val dict = L
  val haptic = LocalHapticFeedback.current
  Column {
    Row(
      Modifier
        .fillMaxWidth()
        .tap(scaleTo = 0.98f, onLongClick = onDelete?.let {
          {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            confirmDialog(dict["common.deleteEntry"], title, dict["common.cancel"], dict["common.delete"], onOk = it)
          }
        }, onClick = onClick)
        .padding(vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      left?.invoke()
      Column(Modifier.weight(1f)) {
        T(title)
        if (subtitle != null) T(subtitle, dim = true, size = 12)
      }
      right?.invoke()
    }
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.border))
  }
}

/** Thin progress bar (steps, freshness, nutrient share). */
@Composable
fun Bar(fraction: Float, color: Color, height: Dp = 8.dp) {
  Box(Modifier.fillMaxWidth().height(height).background(c.cardAlt, RoundedCornerShape(height / 2))) {
    Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).background(color, RoundedCornerShape(height / 2)))
  }
}

/** Rounded square behind an icon (lists, dialogs). */
@Composable
fun IconBadge(name: String, size: Dp = 36.dp, icon: Dp = 19.dp, radius: Dp = 11.dp) {
  Box(Modifier.size(size).background(c.accentSoft, RoundedCornerShape(radius)), contentAlignment = Alignment.Center) {
    Icon(name, icon, c.accent)
  }
}

@Composable
fun Logo(size: Dp = 48.dp, color: Color = c.accent) {
  androidx.compose.foundation.Canvas(Modifier.size(size)) {
    val s = this.size.width / 100f
    drawCircle(color)
    val leaf = androidx.compose.ui.graphics.Path().apply {
      moveTo(50 * s, 18 * s)
      cubicTo(74 * s, 26 * s, 82 * s, 50 * s, 70 * s, 72 * s)
      cubicTo(62 * s, 84 * s, 44 * s, 86 * s, 34 * s, 78 * s)
      cubicTo(22 * s, 66 * s, 26 * s, 34 * s, 50 * s, 18 * s)
      close()
    }
    drawPath(leaf, Color.White)
    val pulse = androidx.compose.ui.graphics.Path().apply {
      moveTo(26 * s, 56 * s); lineTo(40 * s, 56 * s); lineTo(45 * s, 44 * s); lineTo(52 * s, 66 * s); lineTo(57 * s, 52 * s); lineTo(74 * s, 56 * s)
    }
    drawPath(
      pulse, color,
      style = androidx.compose.ui.graphics.drawscope.Stroke(5 * s, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
    )
  }
}

/** Space that keeps the last item clear of a floating button. */
@Composable
fun BottomGap(h: Dp = 24.dp) = Spacer(Modifier.height(h))
