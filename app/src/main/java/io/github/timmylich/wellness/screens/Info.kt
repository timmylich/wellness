/** Tips, More, About, Support and the donation prompt. */
package io.github.timmylich.wellness.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.timmylich.wellness.data.Actions
import io.github.timmylich.wellness.data.Assets
import io.github.timmylich.wellness.data.Store
import io.github.timmylich.wellness.lib.Updates
import io.github.timmylich.wellness.lib.openUrl
import io.github.timmylich.wellness.lib.pendingMilestone
import io.github.timmylich.wellness.lib.weightLost
import io.github.timmylich.wellness.ui.Button
import io.github.timmylich.wellness.ui.Card
import io.github.timmylich.wellness.ui.Chevron
import io.github.timmylich.wellness.ui.Chip
import io.github.timmylich.wellness.ui.Collapse
import io.github.timmylich.wellness.ui.Field
import io.github.timmylich.wellness.ui.H
import io.github.timmylich.wellness.ui.Icon
import io.github.timmylich.wellness.ui.IconBadge
import io.github.timmylich.wellness.ui.L
import io.github.timmylich.wellness.ui.Logo
import io.github.timmylich.wellness.ui.Nav
import io.github.timmylich.wellness.ui.Route
import io.github.timmylich.wellness.ui.T
import io.github.timmylich.wellness.ui.a
import io.github.timmylich.wellness.ui.c
import io.github.timmylich.wellness.ui.tap
import kotlinx.coroutines.launch

const val TELEGRAM_URL = "https://t.me/timmylich_portfolio"
private const val TRIBUTE_TELEGRAM_URL = "https://t.me/tribute/app?startapp=dRVs"
private const val TRIBUTE_WEB_URL = "https://web.tribute.tg/d/RVs"

private data class Wallet(val id: String, val networks: String, val address: String)

private val WALLETS = listOf(
  // One EVM address works on every EVM-compatible network.
  Wallet("evm", "Ethereum · BNB Chain · Base · Arbitrum · Optimism · Polygon", "0xe790989b801ddbc706f391b10bec0e6c1a1fd334"),
  Wallet("sol", "Solana (SOL, USDC, USDT)", "HJq4ocLn8eua95WKQs82N4hZ2S7VABANefTrSoUqo6rb"),
  Wallet("tron", "TRON (TRX, USDT TRC-20)", "TMKejJ5a5unasnZiVZ1Vz8GvkXsDj7mvPE"),
  Wallet("ton", "TON · Gram (wallet W5)", "UQCOymZAvj5ho73JlD1s1-qo-pD-bqIQ-H_Y4FEjX7Sp68rI"),
)

// ---- tips ------------------------------------------------------------------------------

/** Health tips library; category chips scroll to their section, the search filters tips. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Tips() {
  val dict = L
  val all = Assets.tips(dict.lang)
  var query by rememberSaveable { mutableStateOf("") }
  var open by rememberSaveable { mutableStateOf<String?>(null) }
  val q = query.trim().lowercase()
  val cats = all.map { cat -> cat.copy(tips = cat.tips.filter { t -> q.isEmpty() || t.title.lowercase().contains(q) || t.body.any { it.lowercase().contains(q) } }) }.filter { it.tips.isNotEmpty() }
  val list = rememberLazyListState()
  val scope = rememberCoroutineScope()
  // Leave room for the sticky tag row.
  val headerPx = with(androidx.compose.ui.platform.LocalDensity.current) { 60.dp.roundToPx() }
  LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), state = list, contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item { Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) { Field(query, { query = it }, Modifier.fillMaxWidth(), numeric = false, placeholder = dict["tips.search"]) } }
    // The tag row sticks to the top; content scrolls under the pills.
    stickyHeader {
      Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (cat in all) Chip(cat.title, icon = cat.icon) {
          val i = cats.indexOfFirst { it.id == cat.id }
          if (i >= 0) scope.launch { list.animateScrollToItem(i + 2, -headerPx) }
        }
      }
    }
    items(cats.size, key = { cats[it].id }) { i ->
      val cat = cats[i]
      Card(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(cat.icon, 22.dp, c.accent, glow = true)
          T(cat.title, size = 18, bold = true)
        }
        for (tip in cat.tips) {
          val isOpen = open == tip.id || q.isNotEmpty()
          Column(Modifier.fillMaxWidth().tap(scaleTo = 1f) { open = if (isOpen) null else tip.id }.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              T(tip.title, Modifier.weight(1f), bold = true, color = c.accent)
              Chevron(isOpen, c.accent)
            }
            Collapse(isOpen) {
              for (p in tip.body) T("• $p", size = 14)
              if (tip.source != null) T("${dict["tips.source"]}: ${tip.source}", dim = true, size = 12)
            }
          }
        }
      }
    }
    item { T(dict["tips.footer"], Modifier.fillMaxWidth().padding(horizontal = 16.dp), dim = true, size = 12, center = true) }
  }
}

// ---- more ------------------------------------------------------------------------------

private val MORE_ICONS = mapOf(
  "profile" to "user", "goal" to "target", "appearance" to "palette", "sections" to "layers", "nutrition" to "nutrition",
  "waterActivity" to "water", "steps" to "activity", "notifications" to "bell", "data" to "database", "support" to "donate",
  "onboarding" to "restart", "about" to "info",
)

/** Data: what describes the user. Settings: app behaviour. App: information and support. */
private val GROUPS = listOf(
  "data" to listOf("profile", "goal", "data", "onboarding"),
  "settings" to listOf("appearance", "sections", "nutrition", "waterActivity", "steps", "notifications"),
  "app" to listOf("support", "about"),
)

@Composable
fun More() = TabPage(L["tabs.more"]) {
  val dict = L
  for ((g, items) in GROUPS) {
    T(dict["more.groups.$g"].uppercase(), Modifier.padding(top = 4.dp, start = 4.dp), dim = true, size = 12, bold = true, spacing = 0.8f)
    Card(gap = 0.dp, vertical = 4.dp) {
      items.forEachIndexed { idx, i ->
        if (idx > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.border))
        Row(
          Modifier.fillMaxWidth().tap(scaleTo = 0.985f) {
            when (i) {
              "about" -> Nav.push(Route.About)
              // Keep all data; just walk through the setup wizard again.
              "onboarding" -> Actions.restartOnboarding()
              else -> Nav.push(Route.Settings(i))
            }
          }.padding(vertical = 11.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          IconBadge(MORE_ICONS.getValue(i))
          Column(Modifier.weight(1f)) {
            T(dict["more.$i"], bold = true)
            T(dict["more.subtitles.$i"], dim = true, size = 12)
          }
          Icon("right", 18.dp, c.textDim)
        }
      }
    }
  }
  Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
    Logo(28.dp)
    T(dict["about.tagline"], dim = true, size = 13)
  }
}

// ---- about & support -------------------------------------------------------------------

@Composable
fun AboutContent() {
  val dict = L
  val ctx = LocalContext.current
  Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Logo(80.dp)
    T("Wellness", size = 24, bold = true)
    T(dict.t("about.version", Updates.current), dim = true)
  }
  Card {
    H(dict["about.openSource"], "code")
    T(dict["about.openSourceText"])
    Button(dict["about.github"], Modifier.fillMaxWidth(), kind = "secondary") { openUrl(ctx, Updates.REPO_URL) }
  }
  Card {
    H(dict["about.privacy"], "info")
    T(dict["about.privacyText"])
  }
  Card {
    H(dict["about.sources"])
    for (s in dict.list("about.sourcesList")) T("• $s", size = 14)
  }
  Card {
    H(dict["about.important"])
    T(dict["about.importantText"], size = 14)
  }
}

@Composable
fun SupportContent() {
  val dict = L
  Card(padding = 24.dp) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
      IconBadge("donate", 72.dp, 36.dp, 24.dp)
      T(dict["donation.screenTitle"], size = 19, bold = true, center = true)
      T(dict["donation.screenText"], dim = true, center = true)
    }
  }
  Card { DonationOptions() }
}

/** Tribute (Telegram / web) and crypto addresses (tap to copy). */
@Composable
private fun DonationOptions() {
  val dict = L
  val ctx = LocalContext.current
  val haptic = LocalHapticFeedback.current
  var copied by remember { mutableStateOf<String?>(null) }
  T(dict["donation.tribute"], bold = true)
  T(dict["donation.tributeHint"], dim = true, size = 12)
  Button(dict["donation.tributeTelegram"], Modifier.fillMaxWidth(), icon = "send") { openUrl(ctx, TRIBUTE_TELEGRAM_URL) }
  Button(dict["donation.tributeWeb"], Modifier.fillMaxWidth(), kind = "secondary", icon = "globe") { openUrl(ctx, TRIBUTE_WEB_URL) }
  T(dict["donation.crypto"], Modifier.padding(top = 6.dp), bold = true)
  for (w in WALLETS) Column(
    Modifier.fillMaxWidth().tap(scaleTo = 0.98f, label = "${dict["donation.wallets.${w.id}"]}: ${dict["donation.copy"]}") {
      (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(w.id, w.address))
      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
      copied = w.id
    }.background(c.cardAlt, RoundedCornerShape(14.dp)).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      T(dict["donation.wallets.${w.id}"], Modifier.weight(1f), bold = true)
      Icon(if (copied == w.id) "check" else "copy", 18.dp, c.accent)
    }
    T(w.networks, dim = true, size = 12)
    androidx.compose.foundation.text.BasicText(
      if (copied == w.id) dict["donation.copied"] else w.address,
      Modifier.background(c.accent.a(0.06f), RoundedCornerShape(6.dp)).padding(4.dp),
      style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = if (copied == w.id) c.accent else c.text),
    )
  }
  T(dict["donation.evmNote"], dim = true, size = 11)
}

/** Support request on usage milestones (3, 7, 14, 30, then every 30 days), or on demand. */
object DonationPreview {
  var on by mutableStateOf(false)
}

@Composable
fun DonationPrompt() {
  val d = Store.data
  val dict = L
  val ctx = LocalContext.current
  val milestone = if (d.meta.onboarded) pendingMilestone(d.meta.usageDays.size, d.meta.donationShown) else null
  val open = milestone != null || DonationPreview.on
  if (!open) return
  // Computed once per opening; a rough figure is enough.
  val lost = remember { weightLost(d) }
  val close = {
    if (DonationPreview.on) DonationPreview.on = false else if (milestone != null) Actions.markDonationShown(milestone)
  }
  val go = { fn: () -> Unit -> close(); fn() }
  Dialog(onDismissRequest = close) {
    Column(
      Modifier.fillMaxWidth().heightIn(max = 640.dp).background(c.card, RoundedCornerShape(24.dp)).verticalScroll(rememberScrollState()).padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      IconBadge("donate", 72.dp, 36.dp, 24.dp)
      T(if (milestone != null && !DonationPreview.on) dict.t("donation.title", milestone) else dict["donation.previewTitle"], size = 20, bold = true, center = true)
      if (lost != null) T(dict.t("donation.lost", lost), bold = true, center = true, color = c.ok)
      T(dict["donation.text"], dim = true, center = true)
      Button(dict["donation.star"], Modifier.fillMaxWidth(), kind = "secondary", icon = "star") { go { openUrl(ctx, Updates.REPO_URL) } }
      Button(dict["donation.money"], Modifier.fillMaxWidth(), icon = "donate", subtitle = dict["donation.moneyHint"]) { go { Nav.push(Route.Support) } }
      Button(dict["donation.telegram"], Modifier.fillMaxWidth(), kind = "secondary", icon = "send") { go { openUrl(ctx, TELEGRAM_URL) } }
      Button(dict["donation.later"], Modifier.fillMaxWidth(), kind = "ghost") { close() }
    }
  }
}
