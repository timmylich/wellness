/**
 * Bottom navigation: Tips · Pantry · Overview · Health · More (optional ones hide when off), as a
 * swipeable pager with a bar whose highlight follows the swipe. Overview has its own section
 * pager; swiping past its first or last section moves to the neighbouring tab.
 */
package io.github.timmylich.wellness.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.timmylich.wellness.data.Store
import io.github.timmylich.wellness.ui.Icon
import io.github.timmylich.wellness.ui.L
import io.github.timmylich.wellness.ui.Nav
import io.github.timmylich.wellness.ui.PagerJump
import io.github.timmylich.wellness.ui.rememberPagerJump
import io.github.timmylich.wellness.ui.LocalMotion
import io.github.timmylich.wellness.ui.Route
import io.github.timmylich.wellness.ui.Screen
import io.github.timmylich.wellness.ui.T
import io.github.timmylich.wellness.ui.a
import io.github.timmylich.wellness.ui.c
import io.github.timmylich.wellness.ui.tap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

@Composable
fun AppScreen(r: Route) = when (r) {
  Route.Tabs -> Tabs()
  is Route.FoodSearch -> FoodSearch(r.date)
  is Route.FoodEdit -> FoodEdit(r)
  is Route.Settings -> SettingsScreen(r.page)
  Route.About -> About()
  Route.Support -> Support()
  Route.Onboarding -> Onboarding()
}

private val TAB_ICONS = mapOf("tips" to "tips", "pantry" to "pantry", "overview" to "overview", "health" to "health", "more" to "more")

/** Lets the overview move the outer tab pager when swiped past its edge. */
val LocalTabs = compositionLocalOf<PagerJump?> { null }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tabs() {
  val sections = Store.data.settings
  val tabs = buildList {
    if (sections.section("tips")) add("tips")
    if (sections.section("pantry")) add("pantry")
    add("overview")
    if (sections.section("health")) add("health")
    add("more")
  }
  val overview = tabs.indexOf("overview")
  val pager = remember(tabs) { PagerState(overview) { tabs.size } }
  val jump = rememberPagerJump(pager)
  // Only when no screen is pushed above the tabs (that back gesture belongs to the stack).
  BackHandler(Nav.stack.size == 1 && pager.currentPage != overview) { jump.go(overview) }
  CompositionLocalProvider(LocalTabs provides jump) {
    Column(Modifier.fillMaxSize().background(c.bg)) {
      HorizontalPager(
        pager,
        Modifier.weight(1f),
        // The section pager inside Overview owns horizontal swipes there.
        userScrollEnabled = pager.currentPage != overview,
        beyondViewportPageCount = jump.beyond,
        key = { tabs[it] },
      ) { i ->
        androidx.compose.foundation.layout.Box(jump.page(i)) {
        when (tabs[i]) {
          "tips" -> Tips()
          "pantry" -> TabPage(L["tabs.pantry"]) { Pantry() }
          "overview" -> Overview()
          "health" -> TabPage(L["tabs.health"]) { Health() }
          else -> More()
        }
        }
      }
      BottomBar(tabs, jump)
    }
  }
}

@Composable
private fun BottomBar(tabs: List<String>, jump: PagerJump) {
  val pal = c
  Box(Modifier.fillMaxWidth().background(pal.card).navigationBarsPadding().height(66.dp)) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(pal.border))
    BoxWithConstraints(Modifier.fillMaxSize()) {
      val slot = constraints.maxWidth.toFloat() / tabs.size
      val pillW = 56.dp
      // Highlight follows the pager position mid-swipe, or glides straight to a distant tab.
      Box(
        Modifier
          .offset { IntOffset((jump.position * slot + (slot - pillW.toPx()) / 2).roundToInt(), 8.dp.roundToPx()) }
          .size(pillW, 32.dp)
          .background(pal.accent.a(0.16f), RoundedCornerShape(16.dp)),
      )
      Row(Modifier.fillMaxSize()) {
        tabs.forEachIndexed { i, t ->
          val focused = jump.target == i
          Column(Modifier.weight(1f).fillMaxSize().tap(scaleTo = 1f) { jump.go(i) }.padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
              Modifier.height(32.dp).graphicsLayer { alpha = 1f - 0.45f * abs(jump.position - i).coerceAtMost(1f) },
              contentAlignment = Alignment.Center,
            ) { Icon(TAB_ICONS.getValue(t), if (t == "overview") 25.dp else 23.dp, pal.accent) }
            T(L["tabs.$t"], Modifier.padding(top = 4.dp), size = 11, bold = true, color = if (focused) pal.accent else pal.textDim, maxLines = 1)
          }
        }
      }
    }
  }
}

/** Tab page with a large title (tab screens have no header). */
@Composable
fun TabPage(title: String?, content: @Composable ColumnScope.() -> Unit) {
  Column(
    Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).statusBarsPadding().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (title != null) T(title, Modifier.padding(top = 4.dp), size = 28, bold = true)
    content()
    Spacer(Modifier.height(16.dp))
  }
}

@Composable
fun About() = Screen(L["more.about"]) { AboutContent() }

@Composable
fun Support() = Screen(L["donation.screenTitle"]) { SupportContent() }
