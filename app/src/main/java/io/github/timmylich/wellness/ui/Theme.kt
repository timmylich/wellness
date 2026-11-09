/** Colour palette from user settings (light/dark/system + accent), and app-wide composition locals. */
package io.github.timmylich.wellness.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.timmylich.wellness.i18n.Dict

@Immutable
data class Palette(
  val dark: Boolean,
  val bg: Color,
  val card: Color,
  val cardAlt: Color,
  val text: Color,
  val textDim: Color,
  val border: Color,
  val accent: Color,
  val accentSoft: Color,
  val ok: Color,
  val low: Color,
  val high: Color,
  val danger: Color,
  val water: Color,
) {
  /** ok | good | low | high | danger */
  fun tone(t: String): Color = when (t) {
    "low" -> low
    "high" -> high
    "danger" -> danger
    else -> ok
  }
}

val ACCENTS = listOf("#2E9E6A", "#2F7DE1", "#7A5AF8", "#E0457B", "#F08C2E", "#0FA3A3", "#5C6B7A")

fun hex(s: String): Color = runCatching { Color(android.graphics.Color.parseColor(s)) }.getOrDefault(Color(0xFF2E9E6A))

fun Color.a(alpha: Float) = copy(alpha = alpha)

fun palette(dark: Boolean, amoled: Boolean, accentHex: String): Palette {
  val accent = hex(accentHex)
  return when {
    dark && amoled -> Palette(
      true, Color(0xFF000000), Color(0xFF0C0E0F), Color(0xFF16191B), Color(0xFFECEFF1), Color(0xFF8D989F), Color(0xFF1F2427),
      accent, accent.a(0.16f), Color(0xFF3CC47C), Color(0xFFE8B341), Color(0xFFF08C2E), Color(0xFFEF5350), Color(0xFF4FA8F7),
    )
    dark -> Palette(
      true, Color(0xFF0F1214), Color(0xFF1A1F23), Color(0xFF242A2F), Color(0xFFECEFF1), Color(0xFF8D989F), Color(0xFF2C343A),
      accent, accent.a(0.18f), Color(0xFF3CC47C), Color(0xFFE8B341), Color(0xFFF08C2E), Color(0xFFEF5350), Color(0xFF4FA8F7),
    )
    else -> Palette(
      false, Color(0xFFF4F6F5), Color(0xFFFFFFFF), Color(0xFFF0F3F2), Color(0xFF1B2124), Color(0xFF69757B), Color(0xFFE4E9E7),
      accent, accent.a(0.10f), Color(0xFF23A15D), Color(0xFFD19A12), Color(0xFFE0761B), Color(0xFFD93636), Color(0xFF2F8FE6),
    )
  }
}

val LocalPalette = staticCompositionLocalOf { palette(false, false, "#2E9E6A") }
val LocalDict = staticCompositionLocalOf<Dict> { error("no dictionary") }
/** UI motion on (settings); the system "animator duration scale" applies on its own. */
val LocalMotion = staticCompositionLocalOf { true }

val c: Palette @Composable @ReadOnlyComposable get() = LocalPalette.current
val L: Dict @Composable @ReadOnlyComposable get() = LocalDict.current

/** [spec] when motion is on, otherwise an instant change. */
@Composable
@ReadOnlyComposable
fun <T> motion(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> = anim(LocalMotion.current, spec)

/** Non-composable variant, for transition lambdas. */
fun <T> anim(on: Boolean, spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> = if (on) spec else snap()

/** Dictionary for formatting outside composition (number formatting follows the UI language). */
lateinit var currentDict: Dict

/** Locale-aware number for the current UI language. */
fun fmt(v: Double, digits: Int = 0): String = currentDict.num(v, digits)

fun fmt(v: Int): String = currentDict.num(v.toDouble())

fun fmt(v: Long): String = currentDict.num(v.toDouble())
