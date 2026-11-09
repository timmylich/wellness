/**
 * Minimal stack navigation: screens slide in from the right over the ones below, which stay
 * composed (so the overview keeps its section, scroll and state, like a native stack).
 */
package io.github.timmylich.wellness.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

sealed interface Route {
  data object Tabs : Route
  data class FoodSearch(val date: String? = null) : Route
  /** [id]: edit a saved food; [quick]: per-portion entry logged at once; [entry]: one log entry's snapshot. */
  data class FoodEdit(val id: String? = null, val quick: Boolean = false, val date: String? = null, val entry: String? = null) : Route
  data class Settings(val page: String) : Route
  data object About : Route
  data object Support : Route
  data object Onboarding : Route
}

class NavEntry(val route: Route, start: Float) {
  /** 0 = on screen, 1 = off to the right. */
  val offset = Animatable(start)
  var leaving = false
}

object Nav {
  val stack = mutableStateListOf(NavEntry(Route.Tabs, 0f))
  internal var animate: ((NavEntry, Boolean) -> Unit)? = null

  fun push(r: Route) {
    stack += NavEntry(r, 1f)
  }

  fun pop() {
    val top = stack.lastOrNull() ?: return
    if (stack.size <= 1 || top.leaving) return
    top.leaving = true
    animate?.invoke(top, false) ?: stack.remove(top)
  }

  /** Replace the whole stack (e.g. after onboarding or wiping data). */
  fun reset(r: Route = Route.Tabs) {
    stack.clear()
    stack += NavEntry(r, 0f)
  }
}

@Composable
fun NavHost(screen: @Composable (Route) -> Unit) {
  val scope = rememberCoroutineScope()
  val motionOn = LocalMotion.current
  Nav.animate = { e, _ ->
    scope.launch {
      if (motionOn) e.offset.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
      Nav.stack.remove(e)
    }
  }
  BackHandler(Nav.stack.size > 1) { Nav.pop() }
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val w = constraints.maxWidth.toFloat()
    for (e in Nav.stack) key(e) {
      LaunchedEffect(e) {
        if (e.offset.value > 0f && !e.leaving) {
          if (motionOn) e.offset.animateTo(0f, tween(320, easing = FastOutSlowInEasing)) else e.offset.snapTo(0f)
        }
      }
      Box(Modifier.fillMaxSize().graphicsLayer { translationX = e.offset.value * w }.background(c.bg)) { screen(e.route) }
    }
  }
}

/** Pushed screen: header with a back arrow and title, then a scrolling column. */
@Composable
fun Screen(title: String?, back: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
  Column(Modifier.fillMaxSize().background(c.bg)) {
    if (title != null) Row(
      Modifier.fillMaxWidth().background(c.card).statusBarsPadding().height(56.dp).padding(horizontal = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (back) Box(Modifier.tap { Nav.pop() }.padding(10.dp)) { Icon("left", 24.dp, c.text) }
      T(title, Modifier.padding(start = 6.dp), size = 19, bold = true, maxLines = 1)
    }
    Column(
      Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).then(if (title == null) Modifier.statusBarsPadding() else Modifier)
        .navigationBarsPadding().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      content()
      Spacer(Modifier.height(16.dp))
    }
  }
}
