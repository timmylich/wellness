/** Pantry (expiry dates) and Health records (doctor visits, lab tests, photos). */
package io.github.timmylich.wellness.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import io.github.timmylich.wellness.data.Actions
import io.github.timmylich.wellness.data.Assets
import io.github.timmylich.wellness.data.HealthRecord
import io.github.timmylich.wellness.data.PantryItem
import io.github.timmylich.wellness.data.Per100
import io.github.timmylich.wellness.data.Store
import io.github.timmylich.wellness.data.uid
import io.github.timmylich.wellness.i18n.Lang
import io.github.timmylich.wellness.lib.Photos
import io.github.timmylich.wellness.lib.Reminders
import io.github.timmylich.wellness.lib.addDays
import io.github.timmylich.wellness.lib.atTime
import io.github.timmylich.wellness.lib.daysBetween
import io.github.timmylich.wellness.lib.foodTags
import io.github.timmylich.wellness.lib.humanDate
import io.github.timmylich.wellness.lib.searchLocal
import io.github.timmylich.wellness.lib.todayKey
import io.github.timmylich.wellness.ui.AppSheet
import io.github.timmylich.wellness.ui.Bar
import io.github.timmylich.wellness.ui.Button
import io.github.timmylich.wellness.ui.Card
import io.github.timmylich.wellness.ui.Chip
import io.github.timmylich.wellness.ui.DateStep
import io.github.timmylich.wellness.ui.Empty
import io.github.timmylich.wellness.ui.Field
import io.github.timmylich.wellness.ui.H
import io.github.timmylich.wellness.ui.Icon
import io.github.timmylich.wellness.ui.IconBadge
import io.github.timmylich.wellness.ui.L
import io.github.timmylich.wellness.ui.LocalMotion
import io.github.timmylich.wellness.ui.Segmented
import io.github.timmylich.wellness.ui.SheetTitle
import io.github.timmylich.wellness.ui.Slider
import io.github.timmylich.wellness.ui.T
import io.github.timmylich.wellness.ui.Wrap
import io.github.timmylich.wellness.ui.a
import io.github.timmylich.wellness.ui.c
import io.github.timmylich.wellness.ui.confirmDialog
import io.github.timmylich.wellness.ui.fmt
import io.github.timmylich.wellness.ui.numStr
import io.github.timmylich.wellness.ui.parseNum
import io.github.timmylich.wellness.ui.tap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

// ---- pantry ----------------------------------------------------------------------------

/** How long a finished (0 %) item stays visible so the user can undo. */
private const val REMOVE_DELAY_MS = 5000L

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
  val items = Store.data.pantry
  var editing by remember { mutableStateOf<PantryItem?>(null) }
  var adding by remember { mutableStateOf(false) }
  // Finished items stay for a few seconds with a countdown; value: removal deadline.
  val zeroed = remember { mutableStateMapOf<String, Long>() }
  val sorted = items.filter { it.left > 0 || it.id in zeroed }.sortedBy { it.expiresAt }
  Card {
    H(dict["pantry.title"], "pantry", right = { T("+ ${dict["pantry.add"]}", Modifier.tap { adding = true }, color = c.accent) })
    if (sorted.isEmpty()) Empty(dict["pantry.empty"])
    for (p in sorted) androidx.compose.runtime.key(p.id) {
      PantryRow(p, zeroed[p.id], { editing = p }, { zeroed[p.id] = System.currentTimeMillis() + REMOVE_DELAY_MS }, { zeroed.remove(p.id) })
    }
  }
  Button(dict["pantry.add"], Modifier.fillMaxWidth(), icon = "plus", big = true) { adding = true }
  T(dict["pantry.source"], dim = true, size = 11)
  if (adding || editing != null) AppSheet({ adding = false; editing = null }) { PantryForm(editing, close) }
  // Drop finished items once their countdown is over.
  LaunchedEffect(zeroed.toMap()) {
    val next = zeroed.values.minOrNull() ?: return@LaunchedEffect
    delay(max(0, next - System.currentTimeMillis()))
    zeroed.entries.removeAll { it.value <= System.currentTimeMillis() }
  }
}

@Composable
private fun PantryRow(item: PantryItem, removeAt: Long?, onEdit: () -> Unit, onZero: () -> Unit, onRevive: () -> Unit) {
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
  val leaving = removeAt != null && removeAt - now < 280
  AnimatedVisibility(!leaving, exit = fadeOut() + slideOutHorizontally { it / 6 }) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Column(Modifier.tap(scaleTo = 1f, onClick = onEdit), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          T(item.name, Modifier.weight(1f), bold = true)
          T(dict.daysLeft(daysLeft(item)), size = 13, color = color)
        }
        // Freshness: how much of the shelf life has passed.
        Bar(max(0.04, used).toFloat(), color, 6.dp)
        if (tags.isNotEmpty()) Wrap(4.dp) {
          for (t in tags) T(dict["pantry.tags.$t"], Modifier.background(c.accent.a(0.12f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp), size = 11, color = c.accent)
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        val amount = item.amountG?.let { " · ${fmt(left * it)} ${dict["pantry.units.${item.unit ?: "g"}"]}" } ?: ""
        T("${dict["pantry.left"]}: ${(left * 100).roundToInt()}%$amount", Modifier.width(92.dp), dim = true, size = 12)
        Box(Modifier.weight(1f)) {
          Slider(left, 0.0, 1.0, 0.01, onChange = { draft = it }, haptics = false, onComplete = { v ->
            draft = null
            Actions.setPantryLeft(item.id, v)
            if (v <= 0) {
              onZero()
              Reminders.cancel(ctx, "pantry-${item.id}")
            } else onRevive()
          })
        }
      }
      if (removeAt != null && item.left <= 0) T(dict.t("pantry.removeIn", max(0L, ceil((removeAt - now) / 1000.0).toLong())), size = 12, color = c.danger)
    }
  }
}

@Composable
private fun PantryForm(item: PantryItem?, onDone: () -> Unit) {
  val dict = L
  val ctx = LocalContext.current
  val lang = dict.lang
  var name by remember { mutableStateOf(item?.name ?: "") }
  var baseId by remember { mutableStateOf(item?.baseId) }
  var per100 by remember { mutableStateOf<Per100?>(item?.per100g) }
  var bought by remember { mutableStateOf(item?.boughtAt ?: todayKey()) }
  var expires by remember { mutableStateOf(item?.expiresAt) }
  var amount by remember { mutableStateOf(numStr(item?.amountG)) }
  var unit by remember { mutableStateOf(item?.unit ?: "g") }
  var showDb by remember { mutableStateOf(false) }
  val base = Assets.shelfBase(baseId) ?: Assets.guessBase(name) ?: Assets.shelfBase("other")!!
  // Until the user sets a date, it follows the product type.
  val expiry = expires ?: addDays(bought, base.days)
  val userFoods = Store.data.foods
  val matches = remember(showDb, name, userFoods, lang) { if (showDb && name.trim().length >= 2) searchLocal(name.trim(), userFoods, lang).take(5) else emptyList() }
  val baseName = { b: io.github.timmylich.wellness.data.ShelfBase -> if (lang == Lang.Ru) b.ru else b.en }

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
  Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Field(amount, { amount = it }, Modifier.weight(1f), label = dict["pantry.amount"], suffix = dict["pantry.units.$unit"])
    Segmented(unit, listOf("g", "ml", "pcs").map { it to dict["pantry.units.$it"] }) { unit = it }
  }
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    if (item != null) Button(dict["pantry.remove"], Modifier.weight(1f), kind = "danger") {
      Reminders.cancel(ctx, "pantry-${item.id}")
      Actions.removePantryItem(item.id)
      onDone()
    }
    Button(dict["pantry.save"], Modifier.weight(2f), enabled = name.isNotBlank()) {
      if (name.isBlank()) return@Button
      val p = PantryItem(
        id = item?.id ?: uid(), name = name.trim(), baseId = base.id, per100g = per100, boughtAt = bought, expiresAt = expiry,
        amountG = parseNum(amount), unit = unit, left = item?.left ?: 1.0, createdAt = item?.createdAt ?: System.currentTimeMillis(),
      )
      Actions.savePantryItem(p)
      scheduleReminder(ctx, p, dict["pantry.notifTitle"], dict.t("pantry.notifBody", p.name))
      onDone()
    }
  }
}

// ---- health records --------------------------------------------------------------------

private val KIND_ICON = mapOf("visit" to "user", "test" to "pulse", "other" to "info")

@Composable
fun Thumb(uri: String, size: Dp = 72.dp, modifier: Modifier = Modifier) {
  val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() * 2 }
  val img by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, uri) { value = Photos.load(uri, px) }
  Box(modifier.size(size).clip(RoundedCornerShape(10.dp)).background(c.cardAlt)) {
    img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
  }
}

@Composable
fun Health() {
  val dict = L
  val records = Store.data.health
  var editing by remember { mutableStateOf<HealthRecord?>(null) }
  var adding by remember { mutableStateOf(false) }
  var query by remember { mutableStateOf("") }
  var viewer by remember { mutableStateOf<String?>(null) }
  val q = query.trim().lowercase()
  val list = records
    .filter { q.isEmpty() || "${it.title} ${it.place ?: ""} ${it.notes ?: ""}".lowercase().contains(q) }
    .sortedWith(compareByDescending<HealthRecord> { it.date }.thenByDescending { it.createdAt })

  Button(dict["health.add"], Modifier.fillMaxWidth(), icon = "plus", big = true) { adding = true }
  if (records.size > 3) Field(query, { query = it }, Modifier.fillMaxWidth(), numeric = false, placeholder = dict["health.search"], search = true)
  if (list.isEmpty()) Card {
    H(dict["health.title"], "health")
    Empty(dict["health.empty"])
  }
  for (r in list) Card {
    Column(Modifier.tap(scaleTo = 0.99f) { editing = r }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconBadge(KIND_ICON[r.kind] ?: "info")
        Column(Modifier.weight(1f)) {
          T(r.title, bold = true)
          T("${dict["health.kinds.${r.kind}"]} · ${humanDate(r.date, dict)}${r.place?.let { " · $it" } ?: ""}", dim = true, size = 12)
        }
      }
      if (r.notes != null) T(r.notes, size = 14, maxLines = 4)
    }
    if (r.photos.isNotEmpty()) Wrap(6.dp) { for (p in r.photos) Thumb(p, modifier = Modifier.tap(scaleTo = 0.96f) { viewer = p }) }
  }
  T(dict["health.privacy"], dim = true, size = 11)
  if (adding || editing != null) AppSheet({ adding = false; editing = null }) { HealthForm(editing, close) }
  viewer?.let { uri ->
    Dialog({ viewer = null }, DialogProperties(usePlatformDefaultWidth = false)) {
      val img by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, uri) { value = Photos.load(uri, 2048) }
      Box(Modifier.fillMaxSize().background(Color.Black.a(0.93f)).pointerInput(Unit) { detectTapGestures { viewer = null } }, contentAlignment = Alignment.Center) {
        img?.let { Image(it, null, Modifier.fillMaxWidth(), contentScale = ContentScale.Fit) }
      }
    }
  }
}

@Composable
private fun HealthForm(record: HealthRecord?, onDone: () -> Unit) {
  val dict = L
  val ctx = LocalContext.current
  val scope = rememberCoroutineScope()
  var kind by remember { mutableStateOf(record?.kind ?: "visit") }
  var title by remember { mutableStateOf(record?.title ?: "") }
  var place by remember { mutableStateOf(record?.place ?: "") }
  var notes by remember { mutableStateOf(record?.notes ?: "") }
  var date by remember { mutableStateOf(record?.date ?: todayKey()) }
  var photos by remember { mutableStateOf(record?.photos ?: emptyList()) }
  // Photos added in this session; deleted again if the form is discarded.
  var added by remember { mutableStateOf(emptyList<String>()) }
  val keep = { uris: List<Uri> ->
    scope.launch {
      val kept = Photos.keep(ctx, uris)
      photos = photos + kept
      added = added + kept
    }
  }
  val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { if (it.isNotEmpty()) keep(it) }
  var shot by remember { mutableStateOf<Uri?>(null) }
  val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> shot?.let { if (ok) keep(listOf(it)) } }

  SheetTitle(if (record != null) dict["health.edit"] else dict["health.add"])
  Segmented(kind, listOf("visit", "test", "other").map { it to dict["health.kinds.$it"] }) { kind = it }
  Field(title, { title = it }, Modifier.fillMaxWidth(), label = dict["health.titleLabel"], numeric = false, placeholder = dict["health.titlePh"])
  Field(place, { place = it }, Modifier.fillMaxWidth(), label = dict["health.place"], numeric = false)
  DateStep(dict["health.date"], date) { date = it }
  Field(notes, { notes = it }, Modifier.fillMaxWidth(), label = dict["health.notes"], numeric = false, multiline = true)
  T(dict["health.photos"], dim = true, size = 13)
  if (photos.isNotEmpty()) Wrap(6.dp) {
    for (p in photos) Box {
      Thumb(p)
      Box(
        Modifier.offset(x = 54.dp, y = (-6).dp).size(24.dp).tap { photos = photos - p }.background(c.danger, CircleShape),
        contentAlignment = Alignment.Center,
      ) { Icon("close", 14.dp, Color.White) }
    }
  }
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Button(dict["health.addPhoto"], Modifier.weight(1f), kind = "secondary", icon = "image") {
      gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    Button(dict["health.takePhoto"], Modifier.weight(1f), kind = "secondary", icon = "camera") {
      val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
      val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(dir, "${uid()}.jpg"))
      shot = uri
      camera.launch(uri)
    }
  }
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    if (record != null) Button(dict["health.remove"], Modifier.weight(1f), kind = "danger") {
      confirmDialog(dict["health.removeTitle"], dict["health.removeText"], dict["common.cancel"], dict["common.delete"]) {
        Photos.delete(record.photos)
        Actions.removeHealthRecord(record.id)
        onDone()
      }
    } else Button(dict["common.cancel"], Modifier.weight(1f), kind = "ghost") {
      Photos.delete(added)
      onDone()
    }
    Button(dict["health.save"], Modifier.weight(2f), enabled = title.isNotBlank()) {
      if (title.isBlank()) return@Button
      Photos.delete((record?.photos ?: emptyList()).filter { it !in photos })
      Actions.saveHealthRecord(
        HealthRecord(
          id = record?.id ?: uid(), date = date, kind = kind, title = title.trim(), place = place.trim().ifEmpty { null },
          notes = notes.trim().ifEmpty { null }, photos = photos, createdAt = record?.createdAt ?: System.currentTimeMillis(),
        ),
      )
      onDone()
    }
  }
}
