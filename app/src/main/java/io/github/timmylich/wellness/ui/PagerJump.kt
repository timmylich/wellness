package io.github.timmylich.wellness.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sign

private const val JUMP_MS = 320

/**
 * Page switching for a tab bar over a pager. A neighbour slides in as usual. A distant page is
 * shown at once (the pages in between are never composed or flashed) and slides in from the travel
 * side, while [position] glides straight from the old tab to the new one for the bar highlight.
 */
class PagerJump(val pager: PagerState, private val scope: CoroutineScope, private val motion: Boolean) {
  private var from by mutableIntStateOf(0)
  private var to by mutableIntStateOf(0)
  private var active by mutableStateOf(false)
  private val p = Animatable(1f)
  private var job: Job? = null

  /** Fractional page shown in the bar: follows swipes, or the jump while one runs. */
  val position: Float
    get() = if (active) from + (to - from) * p.value else pager.currentPage + pager.currentPageOffsetFraction

  /** The page the bar should treat as selected. */
  val target: Int get() = if (active) to else pager.targetPage

  fun go(i: Int) {
    if (i !in 0 until pager.pageCount || (i == pager.currentPage && !active)) return
    job?.cancel()
    job = scope.launch {
      when {
        !motion -> pager.scrollToPage(i)
        abs(i - pager.currentPage) <= 1 -> {
          active = false
          pager.animateScrollToPage(i, animationSpec = tween(JUMP_MS, easing = FastOutSlowInEasing))
        }
        else -> {
          from = pager.currentPage
          to = i
          p.snapTo(0f)
          active = true
          try {
            pager.scrollToPage(i)
            p.animateTo(1f, tween(JUMP_MS, easing = FastOutSlowInEasing))
          } finally {
            active = false
          }
        }
      }
    }
  }

  fun step(dir: Int) = go(pager.currentPage + dir)

  /** Pages kept composed on each side: 1 at start, then all, so a swipe never builds a page midway. */
  var beyond by mutableIntStateOf(1)
    internal set

  /** Slide-in of the jump target page. */
  fun page(i: Int): Modifier = Modifier.graphicsLayer {
    if (active && i == to) {
      val v = p.value
      translationX = (1 - v) * sign((to - from).toFloat()) * size.width * 0.35f
      alpha = (v / 0.6f).coerceAtMost(1f)
    }
  }
}

@Composable
fun rememberPagerJump(pager: PagerState): PagerJump {
  val scope = rememberCoroutineScope()
  val motion = LocalMotion.current
  val jump = remember(pager, motion) { PagerJump(pager, scope, motion) }
  LaunchedEffect(jump) {
    // Compose the other pages once the first frame is on screen, off the start-up path.
    withFrameNanos {}
    jump.beyond = pager.pageCount
    // A new touch can stop the pager mid-settle and then go to a nested pager; snap back to
    // the nearest page instead of staying between two.
    snapshotFlow { pager.isScrollInProgress }.collect { scrolling ->
      if (!scrolling && abs(pager.currentPageOffsetFraction) > 0.001f) pager.animateScrollToPage(pager.currentPage)
    }
  }
  return jump
}

/**
 * Nested scroll for a pager inside another pager. A drag past the inner pager's edge moves the
 * [outer] pager with the finger (its own swipes are off); once the outer pager is out of rest the
 * drag moves it first, either way. On release it settles to the nearest page, or the next one in
 * the fling direction. Leftovers never reach the outer pager's own scrolling, so it cannot stick.
 */
@Composable
fun rememberEdgeSwipe(outer: PagerJump?): NestedScrollConnection {
  val flingPx = with(LocalDensity.current) { 400.dp.toPx() }
  return remember(outer, flingPx) {
    object : NestedScrollConnection {
      fun moved() = outer != null && abs(outer.pager.currentPageOffsetFraction) > 0.001f

      override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || !moved()) return Offset.Zero
        outer!!.pager.dispatchRawDelta(-available.x)
        return Offset(available.x, 0f)
      }

      override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput) outer?.pager?.dispatchRawDelta(-available.x)
        return Offset(available.x, 0f)
      }

      override suspend fun onPreFling(available: Velocity): Velocity {
        if (!moved()) return Velocity.Zero
        val p = outer!!.pager
        val pos = p.currentPage + p.currentPageOffsetFraction
        val target = when {
          available.x < -flingPx -> ceil(pos)
          available.x > flingPx -> floor(pos)
          else -> round(pos)
        }.toInt().coerceIn(0, p.pageCount - 1)
        p.animateScrollToPage(target)
        return Velocity(available.x, 0f)
      }

      override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = Velocity(available.x, 0f)
    }
  }
}
