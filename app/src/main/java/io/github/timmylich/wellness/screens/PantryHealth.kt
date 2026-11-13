/** Health records (doctor visits, lab tests, photos). The pantry lives in Pantry.kt. */
package io.github.timmylich.wellness.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.timmylich.wellness.data.Actions
import io.github.timmylich.wellness.data.Assets
import io.github.timmylich.wellness.data.HealthRecord
import io.github.timmylich.wellness.data.PantryItem
import io.github.timmylich.wellness.data.Per100
import io.github.timmylich.wellness.data.Store
import io.github.timmylich.wellness.data.uid
import io.github.timmylich.wellness.i18n.Lang
import io.github.timmylich.wellness.lib.PantryShare
import io.github.timmylich.wellness.lib.Photos
import io.github.timmylich.wellness.lib.Reminders
import io.github.timmylich.wellness.lib.addDays
import io.github.timmylich.wellness.lib.atTime
import io.github.timmylich.wellness.lib.daysBetween
import io.github.timmylich.wellness.lib.foodTags
import io.github.timmylich.wellness.lib.humanDate
import io.github.timmylich.wellness.lib.keyOf
import io.github.timmylich.wellness.lib.searchLocal
import io.github.timmylich.wellness.lib.ShareError
import io.github.timmylich.wellness.lib.timeLabel
import io.github.timmylich.wellness.lib.todayKey
import io.github.timmylich.wellness.ui.AppSheet
import io.github.timmylich.wellness.ui.Bar
import io.github.timmylich.wellness.ui.Button
import io.github.timmylich.wellness.ui.Card
import io.github.timmylich.wellness.ui.Chip
import io.github.timmylich.wellness.ui.DateStep
import io.github.timmylich.wellness.ui.DialogButton
import io.github.timmylich.wellness.ui.Empty
import io.github.timmylich.wellness.ui.Field
import io.github.timmylich.wellness.ui.H
import io.github.timmylich.wellness.ui.HRow
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
import io.github.timmylich.wellness.ui.showDialog
import io.github.timmylich.wellness.ui.parseNum
import io.github.timmylich.wellness.ui.tap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

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
