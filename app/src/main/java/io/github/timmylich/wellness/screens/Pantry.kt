/**
 * Pantry tab, three swipeable pages: products at home (expiry, opened packages, counts, eaten /
 * thrown away), the shopping list, and statistics with history and family members. Pantry and
 * shopping list can be shared by a family code (lib/PantryShare.kt).
 */
package io.github.timmylich.wellness.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.timmylich.wellness.data.Actions
import io.github.timmylich.wellness.data.Assets
import io.github.timmylich.wellness.data.PantryItem
import io.github.timmylich.wellness.data.PantryOutcome
import io.github.timmylich.wellness.data.Per100
import io.github.timmylich.wellness.data.ShelfBase
import io.github.timmylich.wellness.data.ShoppingItem
import io.github.timmylich.wellness.data.Store
import io.github.timmylich.wellness.data.uid
import io.github.timmylich.wellness.i18n.Lang
import io.github.timmylich.wellness.lib.PantryShare
import io.github.timmylich.wellness.lib.Reminders
import io.github.timmylich.wellness.lib.ShareError
import io.github.timmylich.wellness.lib.addDays
import io.github.timmylich.wellness.lib.atTime
import io.github.timmylich.wellness.lib.daysBetween
import io.github.timmylich.wellness.lib.foodTags
import io.github.timmylich.wellness.lib.humanDate
import io.github.timmylich.wellness.lib.keyOf
import io.github.timmylich.wellness.lib.searchLocal
import io.github.timmylich.wellness.lib.timeLabel
import io.github.timmylich.wellness.lib.todayKey
import io.github.timmylich.wellness.ui.AppSheet
import io.github.timmylich.wellness.ui.Bar
import io.github.timmylich.wellness.ui.Button
import io.github.timmylich.wellness.ui.Card
import io.github.timmylich.wellness.ui.Chip
import io.github.timmylich.wellness.ui.DateStep
import io.github.timmylich.wellness.ui.DialogButton
import io.github.timmylich.wellness.ui.Divider
import io.github.timmylich.wellness.ui.Empty
import io.github.timmylich.wellness.ui.Field
import io.github.timmylich.wellness.ui.H
import io.github.timmylich.wellness.ui.HRow
import io.github.timmylich.wellness.ui.Icon
import io.github.timmylich.wellness.ui.L
import io.github.timmylich.wellness.ui.Segmented
import io.github.timmylich.wellness.ui.SheetTitle
import io.github.timmylich.wellness.ui.Slider
import io.github.timmylich.wellness.ui.Stat
import io.github.timmylich.wellness.ui.Stats
import io.github.timmylich.wellness.ui.T
import io.github.timmylich.wellness.ui.Toggle
import io.github.timmylich.wellness.ui.Wrap
import io.github.timmylich.wellness.ui.a
import io.github.timmylich.wellness.ui.c
import io.github.timmylich.wellness.ui.confirmDialog
import io.github.timmylich.wellness.ui.fmt
import io.github.timmylich.wellness.ui.numStr
import io.github.timmylich.wellness.ui.parseNum
import io.github.timmylich.wellness.ui.rememberEdgeSwipe
import io.github.timmylich.wellness.ui.showDialog
import io.github.timmylich.wellness.ui.tap
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** How long a finished (0 %) item stays visible with "eaten / thrown away" before it counts as eaten. */
private const val REMOVE_DELAY_MS = 10_000L

/** While the pantry tab is open, the shared list is checked this often (a tiny request when nothing changed). */
private const val POLL_MS = 30_000L

/** Units counted in whole pieces: they get −/+ buttons instead of a slider. */
private val COUNTED = setOf("pcs", "pack")

private val PAGES = listOf("home", "shopping", "stats")

fun daysLeft(p: PantryItem, today: String = todayKey()): Long = daysBetween(today, p.expiresAt)

/** used: share of the shelf life that has passed; state: fresh | soon | expired. */
fun freshness(p: PantryItem, today: String = todayKey()): Pair<Double, String> {
  val total = max(1L, daysBetween(p.boughtAt, p.expiresAt))
  val left = daysLeft(p, today)
  val used = (1 - left.toDouble() / total).coerceIn(0.0, 1.0)
  // "Close to expiry": the last quarter of the shelf life, or 2 days, whichever is longer.
  val state = if (left < 0) "expired" else if (left <= max(2.0, total * 0.25)) "soon" else "fresh"
  return used to state
}

/** Expiry after opening on [today]: the earlier of the package date and [ShelfBase.opened] days. */
fun openedExpiry(expires: String, base: ShelfBase?, today: String = todayKey()): String {
  val days = base?.opened ?: return expires
  return minOf(expires, addDays(today, days))
}

/** A product bought today, typed, dated and sized from its name. */
fun boughtItem(name: String, today: String = todayKey()): PantryItem {
  val base = Assets.guessBase(name) ?: Assets.shelfBase("other")!!
  return PantryItem(
    uid(), name, base.id, boughtAt = today, expiresAt = addDays(today, base.days),
    amountG = base.amount, unit = base.unit, createdAt = System.currentTimeMillis(),
  )
}

data class PantryStats(val eaten: Int, val wasted: Int, val lossPct: Int, val top: List<Pair<String, Int>>)

/** The last [days] days: products used up, thrown away, the thrown share and the most thrown names. */
fun pantryStats(log: List<PantryOutcome>, today: String = todayKey(), days: Int = 30): PantryStats {
  val from = addDays(today, -(days - 1))
  val recent = log.filter { it.date >= from }
  val wasted = recent.filter { it.kind == "wasted" }
  val loss = if (recent.isEmpty()) 0 else (wasted.sumOf { it.share } / recent.size * 100).roundToInt()
  val top = wasted.groupBy { it.name.trim().lowercase() }.values.map { it.first().name.trim() to it.size }.sortedByDescending { it.second }.take(3)
  return PantryStats(recent.size - wasted.size, wasted.size, loss, top)
}

/** Reminder at 10:00 the day before expiry, if enabled. */
private fun scheduleReminder(ctx: android.content.Context, p: PantryItem, title: String, body: String) {
  val n = Store.data.settings.notifications
  Reminders.cancel(ctx, "pantry-${p.id}")
  if (!n.enabled || !n.expiry || p.left <= 0) return
  Reminders.schedule(ctx, "pantry-${p.id}", atTime(addDays(p.expiresAt, -1), 10, 0), title, body)
}

@Composable
fun Pantry() {
  val dict = L
  val ctx = LocalContext.current
  val d = Store.data
  val scope = rememberCoroutineScope()
  val pager = rememberPagerState { PAGES.size }
  var sharing by remember { mutableStateOf(false) }
  // Swiping past the first or last page moves to the neighbouring bottom tab.
  val edge = rememberEdgeSwipe(LocalTabs.current)
  Column(Modifier.fillMaxSize().statusBarsPadding()) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      T(dict["tabs.pantry"], size = 28, bold = true)
      val waiting = d.shopping.count { it.boughtAt == null }
      Segmented(
        pager.targetPage,
        listOf(
          0 to "${dict["pantry.tabHome"]} · ${d.pantry.count { it.left > 0 }}",
          1 to dict["pantry.tabShopping"] + if (waiting > 0) " · $waiting" else "",
          2 to dict["pantry.tabStats"],
        ),
      ) { scope.launch { pager.animateScrollToPage(it) } }
    }
    HorizontalPager(pager, Modifier.weight(1f).nestedScroll(edge), key = { PAGES[it] }) { i ->
      Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (PantryShare.expired) Card {
          T(dict["pantry.share.expired"], size = 14)
          Button(dict["pantry.share.ok"], Modifier.fillMaxWidth(), kind = "secondary") { PantryShare.dismissExpired() }
        }
        when (PAGES[i]) {
          "shopping" -> ShoppingList()
          "stats" -> PantryStatsPage()
          else -> PantryHome()
        }
        if (PantryShare.available) Button(
          if (PantryShare.code != null) dict["pantry.share.connected"] else dict["pantry.share.button"],
          Modifier.fillMaxWidth(), kind = "secondary", icon = "user",
        ) { sharing = true }
        if (PAGES[i] == "home") T(dict["pantry.source"], dim = true, size = 11)
      }
    }
  }
  if (sharing) AppSheet({ sharing = false }) { ShareForm() }
  ShareSync()
}

/**
 * Keeps the shared list in step while the tab is on screen: own edits go out 1.5 s after they
 * settle, and the server is checked every [POLL_MS] for the others' edits.
 */
@Composable
private fun ShareSync() {
  val dict = L
  val ctx = LocalContext.current
  val d = Store.data
  val code = PantryShare.code ?: return
  // Products added on another phone get their expiry reminders here too.
  val after = { changed: Boolean -> if (changed) for (p in Store.data.pantry) scheduleReminder(ctx, p, dict["pantry.notifTitle"], dict.t("pantry.notifBody", p.name)) }
  LaunchedEffect(code, d.pantry, d.shopping) {
    delay(1500)
    after(PantryShare.sync())
  }
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  LaunchedEffect(code) {
    lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
      while (isActive) {
        after(PantryShare.sync(maxAgeMs = POLL_MS - 5000))
        delay(POLL_MS)
      }
    }
  }
}

// ---- products at home ------------------------------------------------------------------

private class Zeroed(val deadline: Long, val leftBefore: Double)

/** A product just marked eaten / thrown away, kept on screen a few seconds for "to shopping list" and undo. */
private class Ghost(val item: PantryItem, val outcomeId: String, val kind: String, val until: Long)

/** How long a marked product stays visible after "eaten" / "thrown away". */
private const val GHOST_MS = 6000L

@Composable
private fun PantryHome() {
  val dict = L
  val ctx = LocalContext.current
  val d = Store.data
  val items = d.pantry
  var editing by remember { mutableStateOf<PantryItem?>(null) }
  var adding by remember { mutableStateOf(false) }
  // A bought shopping-list item being added through the form.
  var fromShopping by remember { mutableStateOf<ShoppingItem?>(null) }
  // Finished items stay a while with "eaten / thrown away" buttons.
  val zeroed = remember { mutableStateMapOf<String, Zeroed>() }
  val ghosts = remember { mutableStateMapOf<String, Ghost>() }
  val sorted = items.filter { it.left > 0 || it.id in zeroed }.sortedBy { it.expiresAt }
  val shownGhosts = ghosts.values.filter { g -> sorted.none { it.id == g.item.id } }
  val rows = (sorted.map { it to null } + shownGhosts.map { it.item to it }).sortedBy { it.first.expiresAt }
  val states = sorted.groupingBy { freshness(it).second }.eachCount()
  val bought = d.shopping.filter { it.boughtAt != null }.sortedByDescending { it.boughtAt }
  val finish = { p: PantryItem, kind: String ->
    val z = zeroed.remove(p.id)
    val item = if (z != null) p.copy(left = z.leftBefore) else p
    val oid = uid()
    Reminders.cancel(ctx, "pantry-${p.id}")
    Actions.finishPantryItem(item, kind, oid, PantryShare.author())
    ghosts[p.id] = Ghost(item, oid, kind, System.currentTimeMillis() + GHOST_MS)
  }

  AnimatedVisibility(bought.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
    Card(Modifier.animateContentSize()) {
      H(dict["pantry.boughtTitle"], "package")
      T(dict["pantry.boughtHint"], dim = true, size = 13)
      for (s in bought) key(s.id) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          T(s.name, Modifier.weight(1f), bold = true)
          Chip("+ ${dict["pantry.addShort"]}") { fromShopping = s }
          Box(Modifier.size(36.dp).tap { Actions.removeShopping(s.id) }, contentAlignment = Alignment.Center) { Icon("close", 16.dp, c.textDim) }
        }
      }
      if (bought.size > 1) Button(dict["pantry.addAll"], Modifier.fillMaxWidth(), kind = "ghost") {
        for (s in bought) {
          val p = boughtItem(s.name)
          Actions.savePantryItem(p)
          Actions.removeShopping(s.id)
          scheduleReminder(ctx, p, dict["pantry.notifTitle"], dict.t("pantry.notifBody", p.name))
        }
      }
    }
  }

  Card(Modifier.animateContentSize()) {
    H(dict["pantry.title"], "pantry", right = { T("+ ${dict["pantry.add"]}", Modifier.tap { adding = true }, color = c.accent) })
    if ((states["expired"] ?: 0) + (states["soon"] ?: 0) > 0) Wrap(6.dp) {
      states["expired"]?.let { Badge("${dict["pantry.expired"]}: $it", c.danger) }
      states["soon"]?.let { Badge("${dict["pantry.soon"]}: $it", c.high) }
    }
    if (rows.isEmpty()) Empty(dict["pantry.empty"])
    for ((p, ghost) in rows) key(p.id) {
      if (ghost != null) GhostRow(ghost) { ghosts.remove(p.id) }
      else PantryRow(
        p, zeroed[p.id]?.deadline, { editing = p },
        onZero = { before -> zeroed[p.id] = Zeroed(System.currentTimeMillis() + REMOVE_DELAY_MS, before) },
        onRevive = { zeroed.remove(p.id) },
        onFinish = { kind -> finish(p, kind) },
      )
    }
  }
  Button(dict["pantry.add"], Modifier.fillMaxWidth(), icon = "plus", big = true) { adding = true }
  if (adding || editing != null || fromShopping != null) AppSheet({ adding = false; editing = null; fromShopping = null }) {
    val s = fromShopping
    PantryForm(editing, prefill = s?.name, onSaved = { if (s != null) Actions.removeShopping(s.id) }, onFinish = finish, onDone = close)
  }
  // Used up: counted as eaten once the countdown is over (or right away if it was left at 0 earlier).
  LaunchedEffect(zeroed.toMap(), items) {
    for (p in items) if (p.left <= 0 && p.id !in zeroed) Actions.finishPantryItem(p, "eaten", by = PantryShare.author())
    val next = zeroed.values.minOfOrNull { it.deadline } ?: return@LaunchedEffect
    delay(max(0, next - System.currentTimeMillis()))
    val due = zeroed.filterValues { it.deadline <= System.currentTimeMillis() }.keys.toList()
    for (id in due) Store.data.pantry.firstOrNull { it.id == id && it.left <= 0 }?.let { finish(it, "eaten") } ?: zeroed.remove(id)
  }
}

/** "Eaten ✓ · to shopping list · undo", fading out after [GHOST_MS]. */
@Composable
private fun GhostRow(g: Ghost, onGone: () -> Unit) {
  val dict = L
  val wasted = g.kind == "wasted"
  val visible = remember { MutableTransitionState(false).apply { targetState = true } }
  LaunchedEffect(g) {
    delay(max(0, g.until - System.currentTimeMillis()))
    visible.targetState = false
    delay(350)
    onGone()
  }
  AnimatedVisibility(visible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
    Row(
      Modifier.fillMaxWidth().padding(vertical = 6.dp).background(c.cardAlt, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Box(Modifier.size(30.dp).background((if (wasted) c.danger else c.ok).a(0.16f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(if (wasted) "trash" else "check", 16.dp, if (wasted) c.danger else c.ok)
      }
      Column(Modifier.weight(1f)) {
        BasicText(g.item.name, style = TextStyle(color = c.textDim, fontSize = 15.sp, textDecoration = TextDecoration.LineThrough), maxLines = 1)
        T(dict[if (wasted) "pantry.wasted" else "pantry.eaten"], size = 12, color = if (wasted) c.danger else c.ok)
      }
      val listed = Store.data.shopping.any { it.boughtAt == null && it.name.equals(g.item.name, true) }
      Chip(if (listed) "✓ ${dict["pantry.toShoppingShort"]}" else "+ ${dict["pantry.toShoppingShort"]}", active = listed) { if (!listed) Actions.addShopping(g.item.name) }
      T(dict["pantry.undo"], Modifier.tap {
        Actions.undoFinish(g.item, g.outcomeId)
        onGone()
      }.padding(vertical = 6.dp), size = 14, bold = true, color = c.accent)
    }
  }
}

@Composable
private fun Badge(text: String, color: Color) =
  T(text, Modifier.background(color.a(0.14f), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 2.dp), size = 12, color = color)

@Composable
private fun RoundIcon(icon: String, enabled: Boolean = true, onClick: () -> Unit) =
  Box(Modifier.size(36.dp).tap(enabled, onClick = onClick).background(c.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
    Icon(icon, 18.dp, if (enabled) c.accent else c.textDim)
  }

@Composable
private fun PantryRow(item: PantryItem, removeAt: Long?, onEdit: () -> Unit, onZero: (Double) -> Unit, onRevive: () -> Unit, onFinish: (String) -> Unit) {
  val dict = L
  val ctx = LocalContext.current
  val (used, state) = freshness(item)
  val color = when (state) {
    "expired" -> c.danger
    "soon" -> c.high
    else -> c.ok
  }
  val tags = foodTags(item.per100g)
  // Local copy while dragging; committed on release.
  var draft by remember { mutableStateOf<Double?>(null) }
  val left = draft ?: item.left
  var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(removeAt) {
    while (removeAt != null && now < removeAt) {
      delay(250)
      now = System.currentTimeMillis()
    }
  }
  val commit = { v: Double ->
    val before = item.left
    Actions.setPantryLeft(item.id, v)
    if (v <= 0) {
      onZero(before)
      Reminders.cancel(ctx, "pantry-${item.id}")
    } else onRevive()
  }
  val unit = item.unit ?: "g"
  val count = item.amountG?.takeIf { unit in COUNTED && it >= 1 }
  val leaving = removeAt != null && removeAt - now < 280
  AnimatedVisibility(!leaving, exit = fadeOut() + slideOutHorizontally { it / 6 }) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Column(Modifier.tap(scaleTo = 1f, onClick = onEdit), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          T(item.name, Modifier.weight(1f), bold = true)
          T(dict.daysLeft(daysLeft(item)), size = 13, color = color)
        }
        // Freshness: how much of the shelf life has passed.
        Bar(max(0.04, used).toFloat(), color, 6.dp)
        val showState = state != "fresh"
        if (showState || item.openedAt != null || tags.isNotEmpty()) Wrap(4.dp) {
          if (showState) Badge(dict["pantry.$state"], color)
          if (item.openedAt != null) Badge(dict["pantry.opened"], c.accent)
          for (t in tags) Badge(dict["pantry.tags.$t"], c.textDim)
        }
      }
      if (count != null) {
        val have = (left * count).roundToInt()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          T("${dict["pantry.left"]}: $have / ${fmt(count)} ${dict["pantry.units.$unit"]}", Modifier.weight(1f), dim = true, size = 13)
          RoundIcon("minus", have > 0) { commit(max(0, have - 1) / count) }
          RoundIcon("plus", have < count) { commit(minOf(count, have + 1.0) / count) }
        }
      } else Row(verticalAlignment = Alignment.CenterVertically) {
        val amount = item.amountG?.let { " · ${fmt(left * it)} ${dict["pantry.units.$unit"]}" } ?: ""
        T("${dict["pantry.left"]}: ${(left * 100).roundToInt()}%$amount", Modifier.width(92.dp), dim = true, size = 12)
        Box(Modifier.weight(1f)) {
          Slider(left, 0.0, 1.0, 0.01, onChange = { draft = it }, haptics = false, onComplete = { v ->
            draft = null
            commit(v)
          })
        }
      }
      AnimatedVisibility(removeAt != null && item.left <= 0, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
      Column(
        Modifier.fillMaxWidth().background(c.cardAlt, RoundedCornerShape(12.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        T(dict.t("pantry.finishedAsk", max(0L, ceil(((removeAt ?: now) - now) / 1000.0).toLong())), size = 13)
        HRow {
          Button(dict["pantry.eaten"], Modifier.weight(1f), kind = "secondary", icon = "check") { onFinish("eaten") }
          Button(dict["pantry.wasted"], Modifier.weight(1f), kind = "danger", icon = "trash") { onFinish("wasted") }
        }
        val listed = Store.data.shopping.any { it.boughtAt == null && it.name.equals(item.name, true) }
        Chip(if (listed) "✓ ${dict["pantry.inShopping"]}" else "+ ${dict["pantry.toShopping"]}", active = listed) { Actions.addShopping(item.name) }
      }
      }
    }
  }
}

@Composable
private fun PantryForm(item: PantryItem?, prefill: String? = null, onSaved: () -> Unit = {}, onFinish: (PantryItem, String) -> Unit, onDone: () -> Unit) {
  val dict = L
  val ctx = LocalContext.current
  val lang = dict.lang
  var name by remember { mutableStateOf(item?.name ?: prefill ?: "") }
  var baseId by remember { mutableStateOf(item?.baseId) }
  var per100 by remember { mutableStateOf<Per100?>(item?.per100g) }
  var bought by remember { mutableStateOf(item?.boughtAt ?: todayKey()) }
  var expires by remember { mutableStateOf(item?.expiresAt) }
  var opened by remember { mutableStateOf(item?.openedAt) }
  // null: follow the product type's typical package until the user types an amount or picks a unit.
  var amount by remember { mutableStateOf(item?.amountG?.let { numStr(it) }) }
  var unit by remember { mutableStateOf(item?.unit) }
  var showDb by remember { mutableStateOf(false) }
  val base = Assets.shelfBase(baseId) ?: Assets.guessBase(name) ?: Assets.shelfBase("other")!!
  // Until the user sets a date, it follows the product type (and the opening day).
  val expiry = expires ?: addDays(bought, base.days).let { e -> opened?.let { openedExpiry(e, base, it) } ?: e }
  val amountText = amount ?: if (unit == null) base.amount?.let { numStr(it) }.orEmpty() else ""
  val unitV = unit ?: if (amount == null) base.unit ?: "g" else "g"
  val userFoods = Store.data.foods
  val matches = remember(showDb, name, userFoods, lang) { if (showDb && name.trim().length >= 2) searchLocal(name.trim(), userFoods, lang).take(5) else emptyList() }
  val baseName = { b: ShelfBase -> if (lang == Lang.Ru) b.ru else b.en }

  SheetTitle(if (item != null) dict["pantry.edit"] else dict["pantry.add"])
  Field(name, { name = it; showDb = true }, Modifier.fillMaxWidth(), label = dict["pantry.name"], numeric = false, placeholder = dict["pantry.namePh"], search = true)
  if (matches.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    T(dict["pantry.fromDb"], dim = true, size = 12)
    for (f in matches) T(f.name, Modifier.fillMaxWidth().tap(scaleTo = 1f) {
      name = f.name
      per100 = f.per100g
      showDb = false
    }.padding(vertical = 6.dp), size = 14)
  }
  T(dict["pantry.base"], dim = true, size = 13)
  Wrap(6.dp) {
    for (b in Assets.shelfBases) Chip(baseName(b), active = base.id == b.id) {
      baseId = b.id
      // Empty name: use the product type as the name.
      if (name.isBlank()) name = baseName(b)
      expires = null
    }
  }
  T(dict.t("pantry.baseHint", base.days), dim = true, size = 12)
  DateStep(dict["pantry.bought"], bought) { bought = it }
  DateStep(dict["pantry.expires"], expiry) { expires = it }
  Toggle(dict["pantry.openedToggle"], opened != null, base.opened?.let { dict.t("pantry.openedHint", it) }) { on ->
    val today = todayKey()
    opened = if (on) today else null
    if (on) expires = openedExpiry(expiry, base, today)
  }
  Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Field(amountText, { amount = it }, Modifier.weight(1f), label = dict["pantry.amount"], suffix = dict["pantry.units.$unitV"])
    Segmented(unitV, listOf("g", "ml", "pcs", "pack").map { it to dict["pantry.units.$it"] }) {
      unit = it
      if (amount == null) amount = amountText
    }
  }
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    if (item != null) Button(dict["pantry.remove"], Modifier.weight(1f), kind = "ghost") {
      Reminders.cancel(ctx, "pantry-${item.id}")
      Actions.removePantryItem(item.id)
      onDone()
    }
    Button(dict["pantry.save"], Modifier.weight(2f), enabled = name.isNotBlank()) {
      if (name.isBlank()) return@Button
      val cur = item?.let { i -> Store.data.pantry.firstOrNull { it.id == i.id } } ?: item
      val p = PantryItem(
        id = item?.id ?: uid(), name = name.trim(), baseId = base.id, per100g = per100, boughtAt = bought, expiresAt = expiry,
        amountG = parseNum(amountText), unit = unitV, left = cur?.left ?: 1.0, createdAt = item?.createdAt ?: System.currentTimeMillis(), openedAt = opened,
      )
      Actions.savePantryItem(p)
      scheduleReminder(ctx, p, dict["pantry.notifTitle"], dict.t("pantry.notifBody", p.name))
      onSaved()
      onDone()
    }
  }
  if (item != null) {
    val finish = { kind: String ->
      onFinish(Store.data.pantry.firstOrNull { it.id == item.id } ?: item, kind)
      onDone()
    }
    HRow {
      Button(dict["pantry.eaten"], Modifier.weight(1f), kind = "secondary", icon = "check") { finish("eaten") }
      Button(dict["pantry.wasted"], Modifier.weight(1f), kind = "secondary", icon = "trash") { finish("wasted") }
    }
    val listed = Store.data.shopping.any { it.boughtAt == null && it.name.equals(item.name, true) }
    Button(if (listed) dict["pantry.inShopping"] else dict["pantry.toShopping"], Modifier.fillMaxWidth(), kind = "ghost", icon = if (listed) "check" else "plus", enabled = !listed) {
      Actions.addShopping(item.name)
    }
  }
}

// ---- shopping list ---------------------------------------------------------------------

@Composable
private fun ShoppingList() {
  val dict = L
  val d = Store.data
  var input by remember { mutableStateOf("") }
  val add = {
    Actions.addShopping(input)
    input = ""
  }
  val todo = d.shopping.filter { it.boughtAt == null }
  val done = d.shopping.filter { it.boughtAt != null && !it.hidden }.sortedByDescending { it.boughtAt }
  Card(Modifier.animateContentSize()) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Field(input, { input = it }, Modifier.weight(1f), numeric = false, placeholder = dict["pantry.shopPh"])
      Box(
        Modifier.size(46.dp).tap(input.isNotBlank()) { add() }.background(if (input.isNotBlank()) c.accent else c.accentSoft, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
      ) { Icon("plus", 22.dp, if (input.isNotBlank()) Color.White else c.accent) }
    }
    if (todo.isEmpty() && done.isEmpty()) Empty(dict["pantry.shopEmpty"])
    if (todo.isEmpty() && done.isNotEmpty()) T(dict["pantry.shopAllDone"], Modifier.fillMaxWidth().padding(vertical = 8.dp), dim = true, center = true)
    for (s in todo) key(s.id) { ShoppingRow(s) }
    if (done.isNotEmpty()) {
      Divider()
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        T("${dict["pantry.shopDone"]} · ${done.size}", Modifier.weight(1f), dim = true, size = 13, bold = true)
        T(dict["pantry.clearDone"], Modifier.tap { Actions.clearBought() }.padding(vertical = 6.dp), size = 14, color = c.accent, bold = true)
      }
      for (s in done) key(s.id) { ShoppingRow(s) }
      T(dict["pantry.shopHint"], dim = true, size = 12)
    }
  }
  // Recently used-up products not on the list and not at home: one tap to add them back.
  val again = d.pantryLog.asReversed().map { it.name.trim() }.distinctBy { it.lowercase() }
    .filter { n -> todo.none { it.name.equals(n, true) } && d.pantry.none { it.name.equals(n, true) } }.take(10)
  if (again.isNotEmpty()) Card {
    T(dict["pantry.again"], bold = true)
    Wrap(6.dp) { for (n in again) Chip("+ $n") { Actions.addShopping(n) } }
  }
}

/** A shopping-list row; the tick animates before the item moves to "bought". */
@Composable
private fun ShoppingRow(s: ShoppingItem) {
  val scope = rememberCoroutineScope()
  val haptic = LocalHapticFeedback.current
  var pending by remember { mutableStateOf(false) }
  val checked = (s.boughtAt != null) != pending
  val fill by animateColorAsState(if (checked) c.accent else Color.Transparent, label = "tick")
  val tick by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "tickScale")
  Row(
    Modifier.fillMaxWidth().tap(scaleTo = 0.98f, enabled = !pending) {
      haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
      pending = true
      scope.launch {
        delay(420)
        Actions.toggleShopping(s.id)
        pending = false
      }
    }.padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(26.dp).background(fill, CircleShape).border(2.dp, if (checked) c.accent else c.textDim.a(0.6f), CircleShape), contentAlignment = Alignment.Center) {
      Box(Modifier.scale(tick)) { Icon("check", 16.dp, Color.White) }
    }
    BasicText(
      s.name,
      Modifier.weight(1f).padding(start = 14.dp),
      style = TextStyle(
        color = if (checked) c.textDim else c.text, fontSize = 16.sp,
        textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
      ),
    )
    Box(Modifier.size(36.dp).tap { Actions.removeShopping(s.id) }, contentAlignment = Alignment.Center) { Icon("close", 16.dp, c.textDim) }
  }
}

// ---- statistics, history, family ---------------------------------------------------------

@Composable
private fun PantryStatsPage() {
  val dict = L
  val d = Store.data
  val s = pantryStats(d.pantryLog)
  Card {
    H(dict["pantry.statsTitle"], "pantry")
    if (s.eaten + s.wasted == 0) Empty(dict["pantry.statsEmpty"]) else {
      Stats {
        Stat(dict["pantry.statEaten"], "${s.eaten}", color = c.ok)
        Stat(dict["pantry.statWasted"], "${s.wasted}", color = c.danger)
        Stat(dict["pantry.statLoss"], "${s.lossPct}", unit = "%", color = if (s.lossPct > 20) c.danger else if (s.lossPct > 10) c.high else c.ok)
      }
      Bar((1 - s.lossPct / 100f).coerceIn(0.04f, 1f), c.ok, 8.dp)
      if (s.top.isNotEmpty()) {
        T(dict["pantry.statsTop"], bold = true)
        for ((n, k) in s.top) Row(Modifier.fillMaxWidth()) {
          T(n, Modifier.weight(1f))
          T("× $k", dim = true)
        }
      }
      T(dict["pantry.statsNote"], dim = true, size = 12)
    }
  }
  if (PantryShare.code != null) Members()
  History()
}

@Composable
private fun Members() {
  val dict = L
  val list = PantryShare.members.sortedByDescending { it.changedAt }
  Card {
    H(dict["pantry.members"], "user")
    if (list.isEmpty()) Empty(dict["pantry.membersEmpty"])
    for (m in list) Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(38.dp).background(c.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
        T(m.name.take(1).uppercase(), bold = true, color = c.accent)
      }
      Column(Modifier.weight(1f).padding(start = 12.dp)) {
        T(m.name + if (m.id == PantryShare.me) " · ${dict["pantry.you"]}" else "", bold = true)
        T(
          if (m.changedAt > 0) "${humanDate(keyOf(m.changedAt), dict)} ${timeLabel(m.changedAt)}" + (if (m.change.isNotEmpty()) " · ${m.change}" else "") else dict["pantry.noChanges"],
          dim = true, size = 13,
        )
      }
    }
  }
}

@Composable
private fun History() {
  val dict = L
  val log = Store.data.pantryLog
  var shown by remember { mutableIntStateOf(20) }
  var editing by remember { mutableStateOf<PantryOutcome?>(null) }
  // Newest first: by day, then by the moment it was marked.
  val ordered = remember(log) { log.sortedWith(compareByDescending<PantryOutcome> { it.date }.thenByDescending { it.at }) }
  Card(Modifier.animateContentSize()) {
    H(dict["pantry.history"], "database")
    if (log.isEmpty()) Empty(dict["pantry.historyEmpty"]) else T(dict["pantry.historyHint"], dim = true, size = 12)
    var lastDate = ""
    for (o in ordered.take(shown)) {
      if (o.date != lastDate) {
        lastDate = o.date
        T(humanDate(o.date, dict), Modifier.padding(top = 6.dp), dim = true, size = 12, bold = true)
      }
      key(o.id) {
        val wasted = o.kind == "wasted"
        Row(
          Modifier.fillMaxWidth().tap(scaleTo = 0.98f) { editing = o }.padding(vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(Modifier.size(28.dp).background((if (wasted) c.danger else c.ok).a(0.14f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (wasted) "trash" else "check", 15.dp, if (wasted) c.danger else c.ok)
          }
          Column(Modifier.weight(1f).padding(start = 10.dp)) {
            T(o.name, maxLines = 1)
            if (o.by != null) T(o.by, dim = true, size = 12, maxLines = 1)
          }
          T(if (wasted) "${dict["pantry.wasted"]} ${(o.share * 100).roundToInt()}%" else dict["pantry.eaten"], dim = true, size = 13)
        }
      }
    }
    if (ordered.size > shown) Button(dict["pantry.showMore"], Modifier.fillMaxWidth(), kind = "ghost") { shown += 30 }
  }
  editing?.let { o -> AppSheet({ editing = null }) { OutcomeForm(o, close) } }
}

/** Edit or delete a history entry (wrong product, eaten vs thrown away, how much was left). */
@Composable
private fun OutcomeForm(o: PantryOutcome, onDone: () -> Unit) {
  val dict = L
  var name by remember { mutableStateOf(o.name) }
  var kind by remember { mutableStateOf(o.kind) }
  var share by remember { mutableStateOf(if (o.kind == "wasted") o.share else 1.0) }
  var date by remember { mutableStateOf(o.date) }
  SheetTitle(dict["pantry.historyEdit"])
  Field(name, { name = it }, Modifier.fillMaxWidth(), label = dict["pantry.name"], numeric = false)
  Segmented(kind, listOf("eaten" to dict["pantry.eaten"], "wasted" to dict["pantry.wasted"])) { kind = it }
  AnimatedVisibility(kind == "wasted", enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      T(dict.t("pantry.wastedShare", (share * 100).roundToInt()), dim = true, size = 13)
      Slider(share, 0.05, 1.0, 0.05, onChange = { share = it })
    }
  }
  DateStep(dict["common.date"], date) { if (it <= todayKey()) date = it }
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Button(dict["common.delete"], Modifier.weight(1f), kind = "ghost", icon = "trash") {
      Actions.removeOutcome(o.id)
      onDone()
      showDialog(dict["pantry.historyDeleted"], o.name, "trash", listOf(DialogButton(dict["pantry.undo"], "secondary") { Actions.restoreOutcome(o) }, DialogButton(dict["common.done"], "primary")))
    }
    Button(dict["pantry.save"], Modifier.weight(2f), enabled = name.isNotBlank()) {
      Actions.updateOutcome(o.copy(name = name.trim(), kind = kind, share = if (kind == "wasted") share else 1.0, date = date))
      onDone()
    }
  }
}

// ---- sharing ---------------------------------------------------------------------------

/** Create a family code, or join one; once connected, show the code, members and the sync state. */
@Composable
private fun ShareForm() {
  val dict = L
  val ctx = LocalContext.current
  val scope = rememberCoroutineScope()
  val haptic = LocalHapticFeedback.current
  val code = PantryShare.code
  var input by remember { mutableStateOf("") }
  var working by remember { mutableStateOf(false) }
  var copied by remember { mutableStateOf(false) }
  var myName by remember { mutableStateOf(PantryShare.name.ifBlank { Store.data.profile.name }) }
  LaunchedEffect(copied) {
    if (copied) {
      delay(2000)
      copied = false
    }
  }
  fun run(block: suspend () -> Unit) {
    working = true
    scope.launch {
      try {
        block()
      } catch (e: ShareError) {
        showDialog(dict["common.error"], dict["pantry.share.errors.${e.key}"])
      } finally {
        working = false
      }
    }
  }
  val busy = working || PantryShare.busy
  SheetTitle(dict["pantry.share.title"])
  Field(myName, { myName = it; PantryShare.rename(it) }, Modifier.fillMaxWidth(), label = dict["pantry.share.nameLabel"], numeric = false, placeholder = android.os.Build.MODEL)
  if (code == null) {
    T(dict["pantry.share.about"], dim = true, size = 14)
    Button(dict["pantry.share.create"], Modifier.fillMaxWidth(), icon = "plus", enabled = !busy) { run { PantryShare.create() } }
    T(dict["pantry.share.or"], bold = true)
    Field(input, { input = it }, Modifier.fillMaxWidth(), label = dict["pantry.share.codeLabel"], numeric = false, placeholder = "XXXX-XXXX-XXXX-XXXX")
    Button(dict["pantry.share.join"], Modifier.fillMaxWidth(), kind = "secondary", enabled = !busy && input.isNotBlank()) {
      val c = PantryShare.normalize(input) ?: return@Button showDialog(dict["common.error"], dict["pantry.share.errors.badCode"])
      run {
        val remote = PantryShare.fetch(c) ?: throw ShareError("notFound")
        val join = { replace: Boolean -> run { PantryShare.join(c, remote, replace) } }
        showDialog(
          dict["pantry.share.joinTitle"], dict.t("pantry.share.joinText", remote.items.p.size, Store.data.pantry.size), "user",
          listOf(DialogButton(dict["pantry.share.merge"], "primary") { join(false) }, DialogButton(dict["pantry.share.replace"], "danger") { join(true) }),
        )
      }
    }
    return
  }
  T(dict["pantry.share.yourCode"], dim = true, size = 14)
  T(PantryShare.pretty(code), Modifier.fillMaxWidth().background(c.cardAlt, RoundedCornerShape(14.dp)).padding(vertical = 14.dp), size = 22, bold = true, center = true, spacing = 1.5f)
  HRow {
    Button(if (copied) dict["pantry.share.copied"] else dict["pantry.share.copy"], Modifier.weight(1f), kind = if (copied) "primary" else "secondary", icon = if (copied) "check" else "copy") {
      val clip = ClipData.newPlainText("code", PantryShare.pretty(code))
      // Keeps the code out of clipboard previews (Android 13+).
      clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
      (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
      copied = true
    }
    Button(dict["pantry.share.send"], Modifier.weight(1f), kind = "secondary", icon = "send") {
      val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, dict.t("pantry.share.sendText", PantryShare.pretty(code)))
      ctx.startActivity(Intent.createChooser(send, null))
    }
  }
  val at = PantryShare.syncedAt
  T(
    PantryShare.error?.let { dict["pantry.share.errors.$it"] } ?: if (at > 0) dict.t("pantry.share.synced", "${humanDate(keyOf(at), dict)} ${timeLabel(at)}") else "",
    dim = PantryShare.error == null, size = 13, color = if (PantryShare.error != null) c.danger else null,
  )
  Button(if (busy) "…" else dict["pantry.share.syncNow"], Modifier.fillMaxWidth(), icon = "refresh", enabled = !busy) { run { PantryShare.sync(force = true) } }
  Button(dict["pantry.share.leave"], Modifier.fillMaxWidth(), kind = "ghost") {
    confirmDialog(dict["pantry.share.leaveTitle"], dict["pantry.share.leaveText"], dict["common.cancel"], dict["pantry.share.leave"]) { PantryShare.leave() }
  }
  Spacer(Modifier.height(4.dp))
}
