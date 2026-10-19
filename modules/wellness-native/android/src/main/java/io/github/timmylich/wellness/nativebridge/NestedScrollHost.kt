package io.github.timmylich.wellness.nativebridge

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.views.ExpoView
import kotlin.math.abs

/**
 * Wrapper that stops ancestors (the section ViewPager2, vertical ScrollViews) from stealing a
 * gesture that belongs to its content — Google's "NestedScrollableHost" pattern, done in native
 * code so it never races the JS thread.
 *
 * Modes:
 *  - "horizontal": a mostly-horizontal drag stays inside (for carousels); vertical drags are
 *    released to the parent so the page still scrolls, and so is a drag past the carousel's
 *    first or last card, so the section pager can turn the page.
 *  - "hold": the gesture is claimed only after the finger rests for [HOLD_MS] (for chart
 *    scrubbing); moving earlier releases it to the parents as usual.
 */
class NestedScrollHost(context: Context, appContext: AppContext) : ExpoView(context, appContext) {
  var mode: String = "horizontal"

  private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
  private val handler = Handler(Looper.getMainLooper())
  private var startX = 0f
  private var startY = 0f
  private var decided = false
  private var held = false
  private val holdRunnable = Runnable { if (!decided) { held = true; decided = true; claim(true) } }

  private fun claim(disallow: Boolean) {
    parent?.requestDisallowInterceptTouchEvent(disallow)
  }

  override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
    track(e)
    return super.onInterceptTouchEvent(e)
  }

  private fun track(e: MotionEvent) {
    when (e.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        startX = e.x
        startY = e.y
        decided = false
        held = false
        // Hold everything until the direction (or the hold) is known.
        claim(true)
        if (mode == "hold") handler.postDelayed(holdRunnable, HOLD_MS)
      }
      MotionEvent.ACTION_MOVE -> {
        if (decided) return
        val sdx = e.x - startX
        val dx = abs(sdx)
        val dy = abs(e.y - startY)
        if (dx < touchSlop && dy < touchSlop) return
        decided = true
        handler.removeCallbacks(holdRunnable)
        // Hold mode moved too early: not a scrub. Horizontal mode: keep only sideways drags
        // the content can still follow (finger right → content scrolls towards its start).
        claim(mode == "horizontal" && dx > dy && canScroll(this, if (sdx > 0) -1 else 1))
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        handler.removeCallbacks(holdRunnable)
        decided = false
        held = false
        claim(false)
      }
    }
  }

  /** Whether any horizontally scrollable descendant can still scroll in [direction]. */
  private fun canScroll(v: View, direction: Int): Boolean {
    if (v !== this && v.canScrollHorizontally(direction)) return true
    if (v is ViewGroup) for (i in 0 until v.childCount) if (canScroll(v.getChildAt(i), direction)) return true
    return false
  }

  companion object {
    const val HOLD_MS = 180L
  }
}
