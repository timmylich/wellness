/** Settings pages opened from "More". */
package io.github.timmylich.wellness.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.timmylich.wellness.data.Actions
import io.github.timmylich.wellness.data.LanguageSetting
import io.github.timmylich.wellness.data.Nutrient
import io.github.timmylich.wellness.data.RangeOverride
import io.github.timmylich.wellness.data.SECTION_IDS
import io.github.timmylich.wellness.data.Sex
import io.github.timmylich.wellness.data.Store
import io.github.timmylich.wellness.data.ThemeMode
import io.github.timmylich.wellness.lib.Backup
import io.github.timmylich.wellness.lib.BackupError
import io.github.timmylich.wellness.lib.FoodCache
import io.github.timmylich.wellness.lib.HealthConnect
import io.github.timmylich.wellness.lib.TargetContext
import io.github.timmylich.wellness.lib.Updates
import io.github.timmylich.wellness.lib.bmr
import io.github.timmylich.wellness.lib.computeTargets
import io.github.timmylich.wellness.lib.openUrl
import io.github.timmylich.wellness.lib.pendingMilestone
import io.github.timmylich.wellness.steps.StepService
import io.github.timmylich.wellness.ui.ACCENTS
import io.github.timmylich.wellness.ui.Button
import io.github.timmylich.wellness.ui.Card
import io.github.timmylich.wellness.ui.Chip
import io.github.timmylich.wellness.ui.Divider
import io.github.timmylich.wellness.ui.Field
import io.github.timmylich.wellness.ui.HRow
import io.github.timmylich.wellness.ui.Hint
import io.github.timmylich.wellness.ui.HeightWheel
import io.github.timmylich.wellness.ui.KgWheels
import io.github.timmylich.wellness.ui.Labeled
import io.github.timmylich.wellness.ui.YearWheel
import io.github.timmylich.wellness.ui.L
import io.github.timmylich.wellness.ui.Nav
import io.github.timmylich.wellness.ui.PaceSlider
import io.github.timmylich.wellness.ui.Route
import io.github.timmylich.wellness.ui.Screen
import io.github.timmylich.wellness.ui.Segmented
import io.github.timmylich.wellness.ui.T
import io.github.timmylich.wellness.ui.Toggle
import io.github.timmylich.wellness.ui.Wrap
import io.github.timmylich.wellness.ui.c
import io.github.timmylich.wellness.ui.confirmDialog
import io.github.timmylich.wellness.ui.hex
import io.github.timmylich.wellness.ui.numStr
import io.github.timmylich.wellness.ui.parseNum
import io.github.timmylich.wellness.ui.showDialog
import io.github.timmylich.wellness.ui.tap
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun SettingsScreen(page: String) = Screen(L["more.$page"]) {
  when (page) {
    "goal" -> GoalPage()
    "appearance" -> AppearancePage()
    "sections" -> SectionsPage()
    "nutrition" -> NutritionPage()
    "waterActivity" -> WaterActivityPage()
    "steps" -> StepsPage()
    "notifications" -> NotificationsPage()
    "data" -> DataPage()
    "support" -> SupportPage()
    else -> ProfilePage()
  }
}

/** Numeric input bound to a stored value; commits only valid numbers. */
@Composable
private fun NumSetting(label: String, value: Double?, modifier: Modifier = Modifier, suffix: String? = null, hint: String? = null, onCommit: (Double?) -> Unit) {
  var s by remember { mutableStateOf(numStr(value)) }
  Field(s, {
    s = it
    onCommit(parseNum(it))
  }, modifier, label = label, suffix = suffix, hint = hint)
}

private fun confirm(title: String, message: String, cancel: String, yes: String, onOk: () -> Unit) =
  confirmDialog(title, message, cancel, yes, icon = "info", onOk = onOk)

/** Lifestyle options from the dictionary: value, label, hint. */
@Composable
fun lifestyles(): List<Triple<Double, String, String>> = L.objects("lifestyles").map {
  Triple(it["value"]!!.jsonPrimitive.content.toDouble(), it["label"]!!.jsonPrimitive.content, it["hint"]!!.jsonPrimitive.content)
}

@Composable
private fun ProfilePage() {
  val dict = L
  val p = Store.data.profile
  Card {
    Field(p.name, { v -> Actions.updateProfile { it.copy(name = v) } }, Modifier.fillMaxWidth(), label = dict["settings.name"], numeric = false)
    Segmented(p.sex, listOf(Sex.Male to dict["onboarding.male"], Sex.Female to dict["onboarding.female"])) { v -> Actions.updateProfile { it.copy(sex = v) } }
    Wrap(16.dp, center = true) {
      Labeled(dict["onboarding.birthYear"]) { YearWheel(p.birthYear) { v -> Actions.updateProfile { it.copy(birthYear = v) } } }
      Labeled(dict["onboarding.height"]) { HeightWheel(p.heightCm) { v -> Actions.updateProfile { it.copy(heightCm = v) } } }
      Labeled(dict["onboarding.weight"]) { KgWheels(p.weightKg) { v -> Actions.updateProfile { it.copy(weightKg = v) } } }
    }
  }
}

@Composable
private fun GoalPage() {
  val dict = L
  val p = Store.data.profile
  val ls = lifestyles()
  Card {
    Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
      Labeled(dict["onboarding.targetWeight"]) { KgWheels(p.targetWeightKg) { v -> Actions.updateProfile { it.copy(targetWeightKg = v) } } }
    }
    T(dict["pace.title"], bold = true)
    PaceSlider(p) { v -> Actions.updateProfile { it.copy(weeklyChangeKg = v) } }
  }
  Card {
    T(dict["settings.lifestyle"], bold = true)
    Segmented(p.lifestyleFactor, ls.map { it.first to it.second }) { v -> Actions.updateProfile { it.copy(lifestyleFactor = v) } }
    ls.firstOrNull { it.first == p.lifestyleFactor }?.let { T(it.third, dim = true, size = 12) }
    NumSetting(dict["settings.bmrManual"], p.bmrOverride, Modifier.fillMaxWidth(), dict["units.kcal"], dict.t("settings.bmrManualHint", bmr(p.copy(bmrOverride = null)))) { v ->
      Actions.updateProfile { it.copy(bmrOverride = v?.takeIf { x -> x > 500 }) }
    }
  }
}

@Composable
private fun AppearancePage() {
  val dict = L
  val s = Store.data.settings
  Card {
    T(dict["settings.language"], bold = true)
    Segmented(s.language, LanguageSetting.entries.map { it to dict["settings.languages.${it.name.lowercase()}"] }) { v -> Actions.updateSettings { it.copy(language = v) } }
    Divider()
    T(dict["settings.theme"], bold = true)
    Segmented(s.theme, ThemeMode.entries.map { it to dict["settings.themes.${it.name.lowercase()}"] }) { v -> Actions.updateSettings { it.copy(theme = v) } }
    Toggle(dict["settings.amoled"], s.amoled, dict["settings.amoledHint"]) { v -> Actions.updateSettings { it.copy(amoled = v) } }
    T(dict["settings.accent"], bold = true)
    Wrap {
      for (a in ACCENTS) Box(
        Modifier.size(38.dp).tap { Actions.updateSettings { it.copy(accent = a) } }.background(hex(a), CircleShape)
          .then(if (s.accent.equals(a, true)) Modifier.border(3.dp, c.text, CircleShape) else Modifier),
      )
    }
    Divider()
    Toggle(dict["settings.animations"], s.animations, dict["settings.animationsHint"]) { v -> Actions.updateSettings { it.copy(animations = v) } }
    Toggle(dict["settings.hints"], s.showHints, dict["settings.hintsHint"]) { v -> Actions.updateSettings { it.copy(showHints = v) } }
  }
}

@Composable
private fun SectionsPage() {
  val dict = L
  val s = Store.data.settings
  Card {
    T(dict["settings.sectionsNote"], dim = true, size = 13)
    for (id in SECTION_IDS) Toggle(dict["sectionsLong.$id"], s.section(id)) { Actions.setSection(id, it) }
  }
}

@Composable
private fun NutritionPage() {
  val dict = L
  val ctx = LocalContext.current
  val scope = rememberCoroutineScope()
  val d = Store.data
  val s = d.settings
  var key by remember { mutableStateOf(Nutrient.Sodium) }
  var cacheSize by remember { mutableIntStateOf(0) }
  LaunchedEffect(Unit) { cacheSize = FoodCache.size(ctx) }
  val defaults = computeTargets(d.profile, TargetContext(0.0, 0.0))
  val override = s.nutrientOverrides[key.key] ?: RangeOverride()
  val unit = io.github.timmylich.wellness.ui.nutrientUnit(key, dict)
  fun setOverride(next: RangeOverride) = Actions.updateSettings {
    val all = it.nutrientOverrides.toMutableMap()
    if (next.min == null && next.max == null) all.remove(key.key) else all[key.key] = next
    it.copy(nutrientOverrides = all)
  }
  Card {
    Toggle(dict["settings.adaptive"], s.adaptiveTargets, dict["settings.adaptiveHint"]) { v -> Actions.updateSettings { it.copy(adaptiveTargets = v) } }
    Toggle(dict["settings.online"], s.onlineFoodSearch, dict["settings.onlineHint"]) { v -> Actions.updateSettings { it.copy(onlineFoodSearch = v) } }
    if (s.onlineFoodSearch) Field(s.usdaApiKey, { v -> Actions.updateSettings { it.copy(usdaApiKey = v.trim()) } }, Modifier.fillMaxWidth(), label = dict["settings.usdaKey"], hint = dict["settings.usdaKeyHint"], numeric = false)
    T(dict.t("settings.foodCache", cacheSize), dim = true, size = 13)
    Button(dict["settings.clearCache"], Modifier.fillMaxWidth(), kind = "ghost") { scope.launch { FoodCache.clear(ctx); cacheSize = 0 } }
  }
  Card {
    T(dict["settings.overrides"], bold = true)
    T(dict["settings.overridesNote"], dim = true, size = 13)
    Wrap {
      for (k in Nutrient.entries) Chip(dict["nutrients.${k.key}.short"] + if (s.nutrientOverrides[k.key] != null) " •" else "", active = key == k) { key = k }
    }
    key(key) {
      HRow {
        NumSetting(dict.t("settings.minOf", unit), override.min, Modifier.weight(1f), hint = dict.t("settings.byDefault", defaults.getValue(key).min)) { setOverride(override.copy(min = it)) }
        NumSetting(dict.t("settings.maxOf", unit), override.max, Modifier.weight(1f), hint = dict.t("settings.byDefault", defaults.getValue(key).max)) { setOverride(override.copy(max = it)) }
      }
    }
    Button(dict["settings.resetOverrides"], Modifier.fillMaxWidth(), kind = "ghost") { Actions.updateSettings { it.copy(nutrientOverrides = emptyMap()) } }
  }
}

@Composable
private fun WaterActivityPage() {
  val dict = L
  val s = Store.data.settings
  var presets by remember { mutableStateOf(s.waterPresets.joinToString(", ") { numStr(it) }) }
  Card {
    Field(presets, { v ->
      presets = v
      val list = v.split(Regex("""[,;\s]+""")).mapNotNull { it.toDoubleOrNull() }.filter { it > 0 && it < 5000 }
      if (list.isNotEmpty()) Actions.updateSettings { it.copy(waterPresets = list.take(6)) }
    }, Modifier.fillMaxWidth(), label = dict["settings.waterPresets"], numeric = false)
    NumSetting(dict["settings.waterDanger"], s.waterDangerMl, Modifier.fillMaxWidth(), dict["units.ml"]) { v -> if (v != null && v >= 2000) Actions.updateSettings { it.copy(waterDangerMl = v) } }
    NumSetting(dict["settings.stepGoal"], s.stepGoal, Modifier.fillMaxWidth(), dict["units.steps"]) { v -> if (v != null && v > 0) Actions.updateSettings { it.copy(stepGoal = v) } }
  }
}

@Composable
private fun StepsPage() {
  val dict = L
  val ctx = LocalContext.current
  val scope = rememberCoroutineScope()
  val source = Store.data.settings.stepsSource
  var status by remember { mutableStateOf("checking") }
  val sensor = remember { StepService.stepSensor(ctx.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager) != null }
  val refresh = { scope.launch { status = HealthConnect.status(ctx) } }
  // Re-check after the user returns from Health Connect or system screens.
  LifecycleResumeEffect(Unit) {
    refresh()
    onPauseOrDispose {}
  }
  val hcPermission = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
    if (HealthConnect.STEPS_READ in granted) {
      Actions.updateSettings { it.copy(stepsSource = "healthConnect") }
      scope.launch { HealthConnect.syncSteps(ctx, 30) }
    }
    refresh()
  }
  val activityPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
  val connect = { hcPermission.launch(setOf(HealthConnect.STEPS_READ)) }
  Card {
    T(dict["settings.stepsSource"], bold = true)
    Segmented(source, listOf("device" to dict["settings.sourceDevice"], "healthConnect" to dict["settings.sourceHc"])) { v ->
      Actions.updateSettings { it.copy(stepsSource = v) }
      if (v == "device" && Build.VERSION.SDK_INT >= 29 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
        activityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
      }
      if (v == "healthConnect" && status != "connected") connect()
    }
    for (p in dict.list(if (source == "device") "activity.info.device" else "activity.info.hc")) T(p, dim = true, size = 13)
  }
  if (source == "device") Card {
    T(if (sensor) dict["settings.deviceNote"] else dict["settings.deviceUnsupported"], bold = true, color = if (sensor) c.ok else c.danger)
    T(dict["activity.hideNotificationHint"], dim = true, size = 13)
    Button(dict["activity.hideNotification"], Modifier.fillMaxWidth(), kind = "secondary", icon = "bell") { ctx.startActivity(StepService.channelSettingsIntent(ctx)) }
  }
  Card {
    T("Health Connect: ${dict["settings.hcStatus.$status"]}", bold = true, color = when (status) { "connected" -> c.ok; "checking" -> c.textDim; else -> c.high })
    if (status == "unavailable" || status == "update") Button(dict["settings.hcInstall"], Modifier.fillMaxWidth(), icon = "plus") { HealthConnect.install(ctx) }
    if (status == "noPermission") Button(dict["settings.hcConnect"], Modifier.fillMaxWidth()) { connect() }
    if (status == "connected") Button(dict["activity.sync"], Modifier.fillMaxWidth(), kind = "secondary", icon = "refresh") { scope.launch { HealthConnect.syncSteps(ctx, 30) } }
    if (status != "unavailable" && status != "checking") Button(dict["settings.hcOpen"], Modifier.fillMaxWidth(), kind = "ghost") { HealthConnect.openSettings(ctx) }
    Hint("Health Connect", dict["settings.hcNeedsSource"])
  }
}

@Composable
private fun NotificationsPage() {
  val dict = L
  val ctx = LocalContext.current
  val scope = rememberCoroutineScope()
  val n = Store.data.settings.notifications
  var checking by remember { mutableStateOf(false) }
  var result by remember { mutableStateOf<String?>(null) }
  val set = { fn: (io.github.timmylich.wellness.data.NotificationSettings) -> io.github.timmylich.wellness.data.NotificationSettings ->
    Actions.updateSettings { it.copy(notifications = fn(it.notifications)) }
  }
  val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) set { it.copy(enabled = true) } }
  Card {
    T(dict["settings.notifNote"], dim = true, size = 13)
    Toggle(dict["settings.notifEnabled"], n.enabled) { v ->
      // Android 13+ asks for POST_NOTIFICATIONS; older versions grant it at install.
      if (v && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
      } else set { it.copy(enabled = v) }
    }
    if (n.enabled) {
      Toggle(dict["settings.notifExpiry"], n.expiry) { v -> set { it.copy(expiry = v) } }
      if (Updates.builtIn) Toggle(dict["settings.notifUpdates"], n.updates, dict["settings.notifUpdatesHint"]) { v -> set { it.copy(updates = v) } }
    }
    if (Updates.builtIn) Button(if (checking) "…" else dict["settings.checkUpdates"], Modifier.fillMaxWidth(), kind = "secondary", icon = "download", enabled = !checking) {
      checking = true
      scope.launch {
        val latest = Updates.fetchLatest()
        checking = false
        result = when {
          latest == null -> dict["settings.updateError"]
          Updates.isNewer(latest.version, Updates.current) -> dict.t("settings.updateAvailable", latest.version).also { openUrl(ctx, latest.url) }
          else -> dict.t("settings.upToDate", Updates.current)
        }
      }
    }
    result?.let { T(it, size = 13) }
  }
}

@Composable
private fun DataPage() {
  val dict = L
  val ctx = LocalContext.current
  val scope = rememberCoroutineScope()
  var busy by remember { mutableStateOf(false) }
  fun run(block: suspend () -> String?) {
    busy = true
    scope.launch {
      try {
        block()?.let { showDialog(dict["common.done"], it, "check") }
      } catch (e: BackupError) {
        showDialog(dict["common.error"], dict["settings.${e.key}"])
      } catch (e: Exception) {
        showDialog(dict["common.error"], e.message ?: e.toString())
      } finally {
        busy = false
      }
    }
  }
  val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
    if (uri != null) run {
      Backup.export(ctx, uri)
      dict.t("settings.exported", Backup.fileName())
    }
  }
  val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) run {
      Store.replace(Backup.import(ctx, uri))
      dict["settings.restored"]
    }
  }
  val packLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) run {
      val foods = Backup.importFoodPack(ctx, uri)
      val ids = foods.map { it.id }.toSet()
      Store.update { s -> s.copy(foods = s.foods.filter { it.id !in ids } + foods) }
      dict.t("settings.packAdded", foods.size)
    }
  }
  // Some file managers report JSON as text/plain or octet-stream, so accept anything.
  val any = arrayOf("*/*")
  Card {
    T(dict["settings.dataNote"], dim = true, size = 13)
    Button(dict["settings.export"], Modifier.fillMaxWidth(), icon = "database", enabled = !busy) { exportLauncher.launch(Backup.fileName()) }
    Button(dict["settings.import"], Modifier.fillMaxWidth(), kind = "secondary", enabled = !busy) {
      confirm(dict["settings.importTitle"], dict["settings.importText"], dict["common.cancel"], dict["common.yes"]) { importLauncher.launch(any) }
    }
    Button(dict["settings.importPack"], Modifier.fillMaxWidth(), kind = "secondary", enabled = !busy) { packLauncher.launch(any) }
    Hint(dict["settings.packHintTitle"], dict["settings.packHint"])
  }
  Card {
    T(dict["settings.wipe"], bold = true)
    val labels = (dict.node("settings.wipeLabels") as? kotlinx.serialization.json.JsonObject)?.keys ?: emptySet()
    Wrap {
      for (k in labels) Chip(dict["settings.wipeLabels.$k"]) {
        confirm(dict["settings.wipeTitle"], dict.t("settings.wipeText", dict["settings.wipeLabels.$k"]), dict["common.cancel"], dict["common.yes"]) { Actions.wipeSection(k) }
      }
    }
    Button(dict["settings.redoOnboarding"], Modifier.fillMaxWidth(), kind = "ghost") {
      Actions.restartOnboarding()
      Nav.reset()
    }
    Button(dict["settings.wipeAll"], Modifier.fillMaxWidth(), kind = "danger") {
      confirm(dict["settings.wipeAllTitle"], dict["settings.wipeAllText"], dict["common.cancel"], dict["common.yes"]) {
        Store.replace(Store.createDefaultData(ctx))
        Nav.reset()
      }
    }
  }
}

@Composable
private fun SupportPage() {
  val dict = L
  val ctx = LocalContext.current
  val meta = Store.data.meta
  Card {
    T(dict.usage(meta.usageDays.size, meta.donationShown.size, pendingMilestone(meta.usageDays.size, meta.donationShown) != null), dim = true, size = 13)
    Button(dict["settings.resetDonations"], Modifier.fillMaxWidth(), kind = "ghost") { Actions.resetDonationPrompts() }
    Button(dict["settings.previewDonation"], Modifier.fillMaxWidth(), kind = "secondary") { DonationPreview.on = true }
  }
  Card {
    Button(dict["donation.money"], Modifier.fillMaxWidth(), icon = "donate", subtitle = dict["donation.moneyHint"]) { Nav.push(Route.Support) }
    Button(dict["donation.star"], Modifier.fillMaxWidth(), kind = "secondary", icon = "star") { openUrl(ctx, Updates.REPO_URL) }
    Button(dict["donation.telegram"], Modifier.fillMaxWidth(), kind = "secondary", icon = "send") { openUrl(ctx, TELEGRAM_URL) }
  }
}
