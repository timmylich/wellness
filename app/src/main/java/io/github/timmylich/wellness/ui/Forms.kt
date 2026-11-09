/**
 * Data-entry forms shared by section screens and the "+" quick-add sheet, the food amount and
 * nutrient sheets, entry editors and pickers. Each form calls
 * `onDone` after saving so the host can close itself.
 */
package io.github.timmylich.wellness.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import io.github.timmylich.wellness.data.Actions
import io.github.timmylich.wellness.data.Assets
import io.github.timmylich.wellness.data.BodyEntry
import io.github.timmylich.wellness.data.Food
import io.github.timmylich.wellness.data.FoodEntry
import io.github.timmylich.wellness.data.HeartEntry
import io.github.timmylich.wellness.data.Nutrient
import io.github.timmylich.wellness.data.Profile
import io.github.timmylich.wellness.data.Sex
import io.github.timmylich.wellness.data.Store
import io.github.timmylich.wellness.data.WaterEntry
import io.github.timmylich.wellness.data.WorkoutEntry
import io.github.timmylich.wellness.data.WorkoutTemplate
import io.github.timmylich.wellness.data.uid
import io.github.timmylich.wellness.i18n.Dict
import io.github.timmylich.wellness.lib.PACE_MAX
import io.github.timmylich.wellness.lib.PACE_MIN
import io.github.timmylich.wellness.lib.Range
import io.github.timmylich.wellness.lib.atTime
import io.github.timmylich.wellness.lib.bpCategory
import io.github.timmylich.wellness.lib.entryNutrients
import io.github.timmylich.wellness.lib.goalDelta
import io.github.timmylich.wellness.lib.localTime
import io.github.timmylich.wellness.lib.metKcal
import io.github.timmylich.wellness.lib.navyBodyFat
import io.github.timmylich.wellness.lib.paceWarning
import io.github.timmylich.wellness.lib.rangeStatus
import io.github.timmylich.wellness.lib.shortLabel
import io.github.timmylich.wellness.lib.todayKey
import io.github.timmylich.wellness.lib.weeksToTarget
import io.github.timmylich.wellness.lib.weightTrend
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

fun nutrientUnit(n: Nutrient, L: Dict) = when (n) {
  Nutrient.Kcal -> L["units.kcal"]
  Nutrient.Sodium, Nutrient.Potassium, Nutrient.Cholesterol -> L["units.mg"]
  else -> L["units.g"]
}

/** Meal is inferred from the time of day (kept in the data model for backups). */
fun defaultMeal(): String {
  val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
  return when {
    h < 11 -> "breakfast"
    h < 16 -> "lunch"
    h < 21 -> "dinner"
    else -> "snack"
  }
}

/** "1 234" for whole numbers, "12,5" otherwise (inputs show what was stored). */
fun numStr(v: Double?): String = when {
  v == null -> ""
  v % 1.0 == 0.0 -> v.toLong().toString()
  else -> v.toString()
}

// ---- water -----------------------------------------------------------------------------

@Composable
fun WaterForm(date: String = todayKey(), onDone: (() -> Unit)? = null) {
  val s = Store.data.settings
  val haptic = LocalHapticFeedback.current
  var amount by remember { mutableStateOf(numStr(s.waterAmountMl)) }
  val ml = parseNum(amount) ?: 0.0
  val dict = L
  fun add(kind: String) {
    if (ml <= 0) return
    Actions.addWater(ml, date, kind)
    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    onDone?.invoke()
  }
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    // Big button: plain water. Small one: other drinks (count towards the danger limit only).
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Column(
        Modifier.weight(1f).tap(label = dict.t("water.addLabel", ml)) { add("water") }.background(c.water, RoundedCornerShape(24.dp)).padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon("water", 24.dp, Color.White)
          T("+ ${fmt(ml)} ${dict["units.ml"]}", size = 26, bold = true, color = Color.White)
        }
        T(dict["water.tapToAdd"], size = 13, color = Color.White.a(0.8f))
      }
      Column(
        Modifier.width(76.dp).fillMaxHeight().tap(label = dict["water.kinds.other"]) { add("other") }.background(c.high.a(0.18f), RoundedCornerShape(24.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
      ) {
        Icon("drink", 26.dp, c.high)
        T(dict["water.kinds.other"], size = 11, bold = true, center = true, color = c.high, maxLines = 2)
      }
    }
    T(dict["water.otherHint"], dim = true, size = 12)
    Wrap(center = true) {
      for (p in s.waterPresets) Chip("${fmt(p)} ${dict["units.ml"]}", active = ml == p) {
        amount = numStr(p)
        Actions.updateSettings { it.copy(waterAmountMl = p) }
      }
    }
    Field(amount, {
      amount = it
      val n = parseNum(it)
      if (n != null && n > 0 && n < 5000) Actions.updateSettings { st -> st.copy(waterAmountMl = n) }
    }, label = dict["water.custom"], suffix = dict["units.ml"])
  }
}

@Composable
fun StepsForm(date: String = todayKey(), onDone: (() -> Unit)? = null) {
  var value by remember { mutableStateOf(numStr(Store.data.steps[date] ?: 0.0)) }
  Field(value, { value = it }, label = L["activity.stepsOfDay"], suffix = L["units.steps"])
  Button(L["common.save"], Modifier.fillMaxWidth()) {
    Actions.setSteps(date, parseNum(value) ?: 0.0)
    onDone?.invoke()
  }
}

// ---- heart -----------------------------------------------------------------------------

private val SYS = range(60, 260)
private val DIA = range(30, 160)
private val PULSE = range(30, 220)
private val KG = range(20, 300)
private val TENTHS = range(0, 9)

@Composable
fun HeartForm(onDone: (() -> Unit)? = null) {
  // Wheels start from the previous reading (or typical values).
  val last = Store.data.heart.lastOrNull()
  var sys by remember { mutableIntStateOf(last?.systolic?.roundToInt() ?: 120) }
  var dia by remember { mutableIntStateOf(last?.diastolic?.roundToInt() ?: 80) }
  var pulse by remember { mutableIntStateOf(last?.pulse?.roundToInt() ?: 70) }
  var note by remember { mutableStateOf("") }
  val preview = bpCategory(sys.toDouble(), dia.toDouble())
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Top) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        T(L["heart.systolic"], dim = true, size = 12)
        Wheel(SYS, sys) { sys = it }
      }
      T("/", Modifier.padding(top = 108.dp), size = 22)
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        T(L["heart.diastolic"], dim = true, size = 12)
        Wheel(DIA, dia) { dia = it }
      }
      Column(Modifier.padding(start = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        T(L["heart.pulse"], dim = true, size = 12)
        Wheel(PULSE, pulse) { pulse = it }
      }
    }
    Field(note, { note = it }, label = L["heart.note"], numeric = false)
    Column(Modifier.fillMaxWidth().background(c.tone(preview.tone).a(0.15f), RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      T(L["heart.bp.${preview.key}.label"], bold = true, color = c.tone(preview.tone))
      T(L["heart.bp.${preview.key}.advice"], size = 13)
    }
    Button(L["common.save"], Modifier.fillMaxWidth()) {
      Actions.addHeart(sys.toDouble(), dia.toDouble(), pulse.toDouble(), note.trim().ifEmpty { null })
      note = ""
      onDone?.invoke()
    }
    Hint(L["heart.howTitle"], L["heart.how"])
  }
}

// ---- profile wheels --------------------------------------------------------------------

private val THIS_YEAR = Calendar.getInstance().get(Calendar.YEAR)
private val YEARS = range(THIS_YEAR - 100, THIS_YEAR - 10)
private val HEIGHTS = range(100, 230)
private val PROFILE_KG = range(30, 300)

/** A wheel (or several) with a caption above. */
@Composable
fun Labeled(label: String, content: @Composable () -> Unit) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    T(label, dim = true, size = 12, center = true)
    Row(verticalAlignment = Alignment.CenterVertically) { content() }
  }
}

@Composable
fun YearWheel(value: Int, onChange: (Int) -> Unit) = Wheel(YEARS, value.coerceIn(YEARS.first(), YEARS.last()), 96.dp, onChange = onChange)

@Composable
fun HeightWheel(value: Double, onChange: (Double) -> Unit) {
  Wheel(HEIGHTS, value.roundToInt().coerceIn(HEIGHTS.first(), HEIGHTS.last()), 84.dp) { onChange(it.toDouble()) }
  T(L["units.cm"], size = 18, dim = true)
}

/** Weight as whole kilograms + tenths on two wheels. */
@Composable
fun KgWheels(value: Double, onChange: (Double) -> Unit) {
  val kg = floor(value).toInt().coerceIn(PROFILE_KG.first(), PROFILE_KG.last())
  val tenth = ((value - floor(value)) * 10).roundToInt().coerceIn(0, 9)
  Wheel(PROFILE_KG, kg, 84.dp) { onChange(it + tenth / 10.0) }
  T(",", size = 26, bold = true)
  Wheel(TENTHS, tenth, 52.dp) { onChange(kg + it / 10.0) }
  T(L["units.kg"], size = 18, dim = true)
}

// ---- body ------------------------------------------------------------------------------

@Composable
fun BodyForm(onDone: (() -> Unit)? = null) {
  val data = Store.data
  val profile = data.profile
  val lastWeight = data.body.lastOrNull { it.weightKg != null }?.weightKg ?: profile.weightKg
  // Weight: whole kilograms + tenths on two wheels, starting from the last weigh-in.
  var kg by remember { mutableIntStateOf(floor(lastWeight).toInt()) }
  var tenth by remember { mutableIntStateOf(((lastWeight % 1) * 10).roundToInt() % 10) }
  var withWeight by remember { mutableStateOf(true) }
  var fat by remember { mutableStateOf("") }
  var waist by remember { mutableStateOf("") }
  var neck by remember { mutableStateOf("") }
  var hip by remember { mutableStateOf("") }
  var height by remember { mutableStateOf("") }
  val w = parseNum(waist)
  val n = parseNum(neck)
  val navy = if (w != null && n != null) navyBodyFat(profile.sex, profile.heightCm, w, n, parseNum(hip)) else null
  val draft = if (withWeight) kg + tenth / 10.0 else null
  val now = remember { System.currentTimeMillis() }
  val recent = remember(data.body) { data.body.filter { it.weightKg != null }.sortedBy { it.time }.takeLast(30) }
  val pts = recent.map { Triple(it.time, it.weightKg!!, it.date) } + (if (draft != null) listOf(Triple(now, draft, todayKey())) else emptyList())
  val trend = weightTrend(pts.map { it.first to it.second })
  val prevAvg = if (recent.isNotEmpty()) weightTrend(recent.map { it.time to it.weightKg!! }).last() else null
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    T(L["body.measureNote"], dim = true, size = 13)
    Toggle(L["body.weight"], withWeight) { withWeight = it }
    if (withWeight) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
      Wheel(KG, kg, 96.dp) { kg = it }
      T(",", size = 26, bold = true)
      Wheel(TENTHS, tenth, 56.dp) { tenth = it }
      T(L["units.kg"], size = 18, dim = true)
    }
    Field(fat, { fat = it }, Modifier.fillMaxWidth(), label = L["body.fatInput"], suffix = "%")
    HRow {
      Field(waist, { waist = it }, Modifier.weight(1f), label = L["body.waist"], suffix = L["units.cm"])
      Field(neck, { neck = it }, Modifier.weight(1f), label = L["body.neck"], suffix = L["units.cm"])
    }
    HRow {
      if (profile.sex == Sex.Female) Field(hip, { hip = it }, Modifier.weight(1f), label = L["body.hip"], suffix = L["units.cm"])
      Field(height, { height = it }, Modifier.weight(1f), label = L["body.heightChanged"], suffix = L["units.cm"])
    }
    if (navy != null && parseNum(fat) == null) T(L.t("body.navy", navy), dim = true, size = 13)
    if (pts.size >= 2) {
      LineChart(pts.map { shortLabel(it.third) }, listOf(Series(pts.map { it.second }, c.accent.a(0.4f)), Series(trend, c.accent)), height = 140.dp)
      if (prevAvg != null) T(L.avgLine(prevAvg, if (draft != null) trend.last() else null), dim = true, size = 13)
    }
    Button(L["common.save"], Modifier.fillMaxWidth()) {
      val e = BodyEntry("", "", 0, draft, parseNum(fat), w, n, parseNum(hip), parseNum(height))
      if (listOf(e.weightKg, e.bodyFatPct, e.waistCm, e.neckCm, e.hipCm, e.heightCm).all { it == null }) return@Button
      Actions.addBody(e)
      fat = ""; waist = ""; neck = ""; hip = ""; height = ""
      onDone?.invoke()
    }
    Hint(L["body.weighTitle"], L["body.weigh"])
  }
}

// ---- workouts --------------------------------------------------------------------------

@Composable
fun WorkoutForm(template: WorkoutTemplate?, date: String = todayKey(), onDone: (() -> Unit)? = null) {
  val dict = L
  val weight = Store.data.profile.weightKg
  var name by remember { mutableStateOf("") }
  var minutes by remember { mutableStateOf(numStr(template?.defaultMinutes ?: 30.0)) }
  var kcal by remember { mutableStateOf("") }
  var note by remember { mutableStateOf("") }
  val min = parseNum(minutes) ?: 0.0
  val estimated = template?.let { metKcal(it.met, weight, min) }
  T(if (template != null) Assets.workoutName(template, dict.lang) else dict["activity.custom"], size = 18, bold = true)
  if (template == null) Field(name, { name = it }, Modifier.fillMaxWidth(), label = dict["activity.name"], numeric = false)
  HRow {
    Field(minutes, { minutes = it }, Modifier.weight(1f), label = dict["activity.duration"], suffix = dict["units.min"])
    Field(kcal, { kcal = it }, Modifier.weight(1f), label = dict["activity.calories"], suffix = dict["units.kcal"], placeholder = estimated?.let { fmt(it) } ?: "—")
  }
  if (estimated != null) T(dict.t("activity.estimate", estimated), dim = true, size = 13)
  Field(note, { note = it }, Modifier.fillMaxWidth(), label = dict["activity.note"], numeric = false)
  Button(dict["common.save"], Modifier.fillMaxWidth()) {
    val k = parseNum(kcal) ?: estimated
    val title = if (template != null) Assets.workoutName(template, dict.lang) else name.trim()
    if (title.isEmpty() || k == null) return@Button showDialog(dict["activity.needNameKcal"])
    Actions.addWorkout(date, title, min, k.roundToLong().toDouble(), note.trim().ifEmpty { null })
    onDone?.invoke()
  }
}

@Composable
fun TemplateForm(template: WorkoutTemplate?, onDone: () -> Unit) {
  val dict = L
  var name by remember { mutableStateOf(template?.let { Assets.workoutName(it, dict.lang) } ?: "") }
  var met by remember { mutableStateOf(numStr(template?.met)) }
  var minutes by remember { mutableStateOf(numStr(template?.defaultMinutes ?: 30.0)) }
  SheetTitle(if (template != null) dict["activity.editTemplate"] else dict["activity.createTemplate"])
  Field(name, { name = it }, Modifier.fillMaxWidth(), label = dict["activity.name"], numeric = false)
  HRow {
    Field(met, { met = it }, Modifier.weight(1f), label = dict["activity.met"])
    Field(minutes, { minutes = it }, Modifier.weight(1f), label = dict["activity.defaultDuration"], suffix = dict["units.min"])
  }
  Hint(dict["activity.metHintTitle"], dict["activity.metHint"])
  Button(dict["common.save"], Modifier.fillMaxWidth()) {
    val m = parseNum(met)
    if (name.isBlank() || m == null || m == 0.0) return@Button showDialog(dict["activity.needNameMet"])
    Actions.saveWorkoutTemplate(WorkoutTemplate(template?.id ?: "custom-${uid()}", name.trim(), m, parseNum(minutes)?.takeIf { it > 0 } ?: 30.0, builtin = template?.builtin))
    onDone()
  }
  if (template != null) Button(dict["activity.deleteTemplate"], Modifier.fillMaxWidth(), kind = "danger") {
    Actions.deleteWorkoutTemplate(template.id)
    onDone()
  }
}

/** Grid of workout templates; tap logs, long-press edits. */
@Composable
fun TemplateGrid(onPick: (WorkoutTemplate) -> Unit, onEdit: ((WorkoutTemplate) -> Unit)? = null) {
  val data = Store.data
  val list = data.workoutTemplates
  val dict = L
  Grid2(list.size) { i, m ->
    val tp = list[i]
    Column(
      m.fillMaxHeight().tap(onLongClick = onEdit?.let { { it(tp) } }) { onPick(tp) }.background(c.cardAlt, RoundedCornerShape(14.dp)).padding(10.dp),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      T(Assets.workoutName(tp, dict.lang), size = 14, bold = true)
      T(dict.t("activity.perHour", metKcal(tp.met, data.profile.weightKg, 60.0)), dim = true, size = 12)
    }
  }
}

// ---- goal pace -------------------------------------------------------------------------

/** Weekly weight-change slider: left = lose, centre = maintain, right = gain. */
@Composable
fun PaceSlider(profile: Profile, onChange: (Double) -> Unit) {
  // Local while dragging, committed once on release.
  var draft by remember { mutableStateOf<Double?>(null) }
  val v = draft ?: profile.weeklyChangeKg
  val p = profile.copy(weeklyChangeKg = v)
  val delta = goalDelta(p)
  val warning = paceWarning(p)
  val weeks = weeksToTarget(p)
  val diff = profile.targetWeightKg - profile.weightKg
  val mismatch = v != 0.0 && abs(diff) >= 0.3 && Math.signum(diff) != Math.signum(v)
  val warnLoss = -profile.weightKg * 0.01
  val dangerLoss = -profile.weightKg * 0.0125
  val warnColor = if (warning == "lossVeryFast") c.danger else c.high
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    T(
      if (v == 0.0) L["pace.maintain"] else if (v < 0) L.t("pace.lose", -v) else L.t("pace.gain", v),
      Modifier.fillMaxWidth(), size = 18, bold = true, color = if (warning != null) warnColor else c.accent, center = true,
    )
    Slider(
      v, PACE_MIN, PACE_MAX, 0.05,
      onChange = { draft = it },
      onComplete = { x ->
        draft = null
        if (x != profile.weeklyChangeKg) onChange(x)
      },
      marks = listOf(0.0),
      zones = listOf(
        Zone(PACE_MIN, max(PACE_MIN, dangerLoss), c.danger.a(0.55f)),
        Zone(max(PACE_MIN, dangerLoss), max(PACE_MIN, warnLoss), c.high.a(0.5f)),
        Zone(max(PACE_MIN, warnLoss), 0.5, c.ok.a(0.45f)),
        Zone(0.5, PACE_MAX, c.high.a(0.5f)),
      ),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      T("−1,5 · ${L["pace.loseLabel"]}", dim = true, size = 12)
      T("0", dim = true, size = 12)
      T("${L["pace.gainLabel"]} · +1,5", dim = true, size = 12)
    }
    if (delta != 0.0) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      TSpans(Span("${if (delta < 0) L["pace.deficit"] else L["pace.surplus"]}: "), Span("${fmt(abs(delta))} ${L["pace.perDay"]}", bold = true), center = true)
    }
    if (weeks != null) T(L.t("pace.eta", weeks), Modifier.fillMaxWidth(), dim = true, center = true, size = 13)
    if (mismatch) T(L["pace.mismatch"], Modifier.fillMaxWidth(), center = true, size = 13, color = c.high)
    if (warning != null) T("⚠ ${L["pace.warnings.$warning"]}", Modifier.fillMaxWidth(), center = true, size = 13, color = warnColor)
    Hint(L["pace.hintTitle"], L["pace.hint"])
  }
}

// ---- food amount -----------------------------------------------------------------------

private val PORTIONS = listOf(50.0, 100.0, 200.0, 300.0, 450.0)
private const val PIECE_G_STEP = 10.0

/** Amount of a food (pieces × grams per piece) with a live nutrient preview. */
@Composable
fun AmountForm(
  food: Food,
  initialGrams: Double? = null,
  initialPieces: Int? = null,
  submitLabel: String? = null,
  onEditData: (() -> Unit)? = null,
  onDelete: (() -> Unit)? = null,
  onSubmit: (grams: Double, pieces: Int) -> Unit,
) {
  val dict = L
  val startPieces = max(1, initialPieces ?: 1)
  var pieces by remember { mutableIntStateOf(startPieces) }
  var perPiece by remember {
    mutableStateOf(numStr(if (initialGrams != null) (initialGrams / startPieces * 10).roundToLong() / 10.0 else food.servingG ?: 100.0))
  }
  val pp = parseNum(perPiece) ?: 0.0
  val g = (pieces * pp * 10).roundToLong() / 10.0
  val n = entryNutrients(food.per100g, g)
  val quick = (PORTIONS + listOfNotNull(food.servingG)).filter { it > 0 }.distinct().sorted()
  val stepBtn = Modifier.size(48.dp, 46.dp)
  Row(verticalAlignment = Alignment.Top) {
    Column(Modifier.weight(1f)) {
      T(food.name, size = 18, bold = true)
      if (food.brand != null) T(food.brand, dim = true, size = 13)
    }
    if (onEditData != null) Row(
      Modifier.tap(label = dict["food.editData"], onClick = onEditData).background(c.accentSoft, RoundedCornerShape(12.dp)).padding(6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon("edit", 16.dp, c.accent)
      T(dict["food.editData"], size = 13, color = c.accent)
    }
  }
  if (food.per100g.sodium == null || food.per100g.sodium == 0.0) T(dict["food.noSalt"], size = 12, color = c.high)
  Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Box(stepBtn.tap(enabled = pieces > 1) { pieces = max(1, pieces - 1) }.background(c.accentSoft, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon("minus", 22.dp, c.accent) }
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
      T(dict["food.pieces"], dim = true, size = 12)
      T(pieces.toString(), size = 22, bold = true)
    }
    Box(stepBtn.tap { pieces++ }.background(c.accentSoft, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon("plus", 22.dp, c.accent) }
  }
  Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Box(stepBtn.tap(enabled = pp > PIECE_G_STEP) { perPiece = numStr(max(PIECE_G_STEP, pp - PIECE_G_STEP)) }.background(c.accentSoft, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon("minus", 22.dp, c.accent) }
    Field(perPiece, { perPiece = it }, Modifier.weight(1f), label = dict["food.perPiece"], suffix = dict["units.g"])
    Box(stepBtn.tap { perPiece = numStr(pp + PIECE_G_STEP) }.background(c.accentSoft, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon("plus", 22.dp, c.accent) }
  }
  Wrap {
    for (q in quick) Chip(if (q == food.servingG) dict.t("food.portion", fmt(q)) else "${fmt(q)} ${dict["units.g"]}", active = pp == q) { perPiece = numStr(q) }
  }
  Column(Modifier.fillMaxWidth().background(c.cardAlt, RoundedCornerShape(12.dp)).padding(12.dp)) {
    T(dict.t("food.total", g), size = 13, bold = true)
    T(dict.t("food.preview", n.kcal, n[Nutrient.Protein], n[Nutrient.Fat], n[Nutrient.Carbs], n[Nutrient.Sugar], n[Nutrient.Sodium] * 2.5 / 1000), size = 13)
  }
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    if (onDelete != null) Button(dict["common.delete"], Modifier.weight(1f), kind = "danger", onClick = onDelete)
    Button(submitLabel ?: dict["common.add"], Modifier.weight(2f), enabled = g > 0) { if (g > 0) onSubmit(g, pieces) }
  }
}

// ---- nutrient details ------------------------------------------------------------------

/** One nutrient scale in detail: gauge, range, explanation and today's top contributors. */
@Composable
fun NutrientDetails(k: Nutrient, value: Double, range: Range, entries: List<FoodEntry>) {
  val dict = L
  val u = nutrientUnit(k, dict)
  val status = rangeStatus(value, range)
  val color = c.tone(if (status == "high") "danger" else if (status == "low") "low" else "ok")
  val digits = if (value < 10 && value % 1 != 0.0) 1 else 0
  val top = entries.map { it to entryNutrients(it)[k] }.filter { it.second > 0 }.sortedByDescending { it.second }.take(6)
  T(dict["nutrients.${k.key}.label"], size = 20, bold = true)
  Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    T(fmt(value, digits), size = 34, bold = true, color = color)
    T("/ ${if (range.lowerIsBetter) "≤ ${fmt(range.max)}" else fmt(range.target)} $u", Modifier.padding(bottom = 6.dp), dim = true)
  }
  RangeBar(value, range, 16.dp)
  Stats {
    if (range.lowerIsBetter) Stat(dict["common.limit"], "≤ ${fmt(range.max)}", Modifier.weight(1f), u, c.danger)
    else {
      Stat(dict["common.min"], fmt(range.min), Modifier.weight(1f), u, c.low)
      Stat(dict["common.target"], fmt(range.target), Modifier.weight(1f), u, c.ok)
      Stat(dict["common.max"], fmt(range.max), Modifier.weight(1f), u, c.danger)
    }
  }
  if (range.lowerIsBetter && range.max > 0) T(dict.ofLimit(value / range.max * 100), dim = true, size = 13)
  if (!range.lowerIsBetter && range.target > 0) T(dict.ofTarget(value / range.target * 100), dim = true, size = 13)
  Divider()
  T(dict["nutrition.sheet.about"], bold = true)
  T(dict["nutrients.${k.key}.hint"], size = 14)
  T(dict["nutrition.sheet.recs"], bold = true)
  T(dict["nutrients.${k.key}.rec"], size = 14)
  Divider()
  T(dict["nutrition.sheet.top"], bold = true)
  if (top.isEmpty()) T(dict["nutrition.sheet.noFoods"], dim = true)
  for ((e, v) in top) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    val share = v / max(value, 0.0001)
    Row {
      T(e.food.name, Modifier.weight(1f), maxLines = 1)
      T("${fmt(v, if (v < 10) 1 else 0)} $u", bold = true)
    }
    Bar(share.toFloat(), c.accent, 6.dp)
    T("${fmt(e.grams)} ${dict["units.g"]} · ${dict.share(share * 100)}", dim = true, size = 12)
  }
}

// ---- entry editors ---------------------------------------------------------------------

private val HOURS = range(0, 23)
private val MINUTES = range(0, 59)
private fun two(v: Int) = v.toString().padStart(2, '0')

/** Date (− / + days) and, unless [dateOnly], hour and minute wheels of an entry. */
private class When(time: Long, date: String) {
  var day by mutableStateOf(date)
  var h by mutableIntStateOf(localTime(time).hour)
  var m by mutableIntStateOf(localTime(time).minute)
  val time get() = atTime(day, h, m)
}

@Composable
private fun WhenFields(w: When, dateOnly: Boolean) {
  DateStep(L["common.date"], w.day) { w.day = it }
  if (!dateOnly) {
    T(L["common.time"], dim = true, size = 13)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
      Wheel(HOURS, w.h, 72.dp, ::two) { w.h = it }
      T(":", size = 26, bold = true)
      Wheel(MINUTES, w.m, 72.dp, ::two) { w.m = it }
    }
  }
}

/** Title, the form, then Save and Delete. */
@Composable
private fun EditFrame(title: String, onSave: () -> Unit, onDelete: () -> Unit, content: @Composable () -> Unit) {
  val dict = L
  SheetTitle(dict["common.editEntry"])
  content()
  Button(dict["common.save"], Modifier.fillMaxWidth(), onClick = onSave)
  Button(dict["common.delete"], Modifier.fillMaxWidth(), kind = "danger", icon = "trash") {
    confirmDialog(dict["common.deleteEntry"], title, dict["common.cancel"], dict["common.delete"], onOk = onDelete)
  }
}

@Composable
fun WaterEdit(e: WaterEntry, onDone: () -> Unit) {
  val dict = L
  var ml by remember { mutableStateOf(numStr(e.ml)) }
  var kind by remember { mutableStateOf(e.kind ?: "water") }
  val w = remember { When(e.time, e.date) }
  EditFrame("${fmt(e.ml)} ${dict["units.ml"]}", {
    val v = parseNum(ml)
    if (v != null && v > 0) {
      Actions.updateWater(e.copy(ml = v.roundToLong().toDouble(), kind = kind, date = w.day, time = w.time))
      onDone()
    }
  }, { Actions.removeWater(e.id); onDone() }) {
    Field(ml, { ml = it }, Modifier.fillMaxWidth(), label = dict["common.amount"], suffix = dict["units.ml"])
    Segmented(kind, listOf("water" to dict["water.kinds.water"], "other" to dict["water.kinds.other"])) { kind = it }
    WhenFields(w, false)
  }
}

@Composable
fun WorkoutEdit(e: WorkoutEntry, onDone: () -> Unit) {
  val dict = L
  var name by remember { mutableStateOf(e.name) }
  var minutes by remember { mutableStateOf(numStr(e.minutes)) }
  var kcal by remember { mutableStateOf(numStr(e.kcal)) }
  var note by remember { mutableStateOf(e.note ?: "") }
  val w = remember { When(e.time, e.date) }
  EditFrame(e.name, {
    val k = parseNum(kcal)
    if (name.isBlank() || k == null) showDialog(dict["activity.needNameKcal"])
    else {
      Actions.updateWorkout(e.copy(name = name.trim(), minutes = parseNum(minutes) ?: 0.0, kcal = k.roundToLong().toDouble(), note = note.trim().ifEmpty { null }, date = w.day, time = w.time))
      onDone()
    }
  }, { Actions.removeWorkout(e.id); onDone() }) {
    Field(name, { name = it }, Modifier.fillMaxWidth(), label = dict["activity.name"], numeric = false)
    HRow {
      Field(minutes, { minutes = it }, Modifier.weight(1f), label = dict["activity.duration"], suffix = dict["units.min"])
      Field(kcal, { kcal = it }, Modifier.weight(1f), label = dict["activity.calories"], suffix = dict["units.kcal"])
    }
    Field(note, { note = it }, Modifier.fillMaxWidth(), label = dict["activity.note"], numeric = false)
    WhenFields(w, false)
  }
}

fun heartTitle(e: HeartEntry) = listOfNotNull(
  if (e.systolic != null && e.diastolic != null) "${fmt(e.systolic)}/${fmt(e.diastolic)}" else null,
  e.pulse?.let { "♥ ${fmt(it)}" },
).joinToString("   ")

@Composable
fun HeartEdit(e: HeartEntry, onDone: () -> Unit) {
  val dict = L
  var sys by remember { mutableStateOf(numStr(e.systolic)) }
  var dia by remember { mutableStateOf(numStr(e.diastolic)) }
  var pulse by remember { mutableStateOf(numStr(e.pulse)) }
  var note by remember { mutableStateOf(e.note ?: "") }
  val w = remember { When(e.time, e.date) }
  EditFrame(heartTitle(e), {
    val s = parseNum(sys)
    val d = parseNum(dia)
    val p = parseNum(pulse)
    // Pressure needs both numbers; a reading needs pressure or pulse.
    if ((s == null) == (d == null) && (s != null || p != null)) {
      Actions.updateHeart(
        e.copy(
          systolic = s?.roundToLong()?.toDouble(), diastolic = d?.roundToLong()?.toDouble(), pulse = p?.roundToLong()?.toDouble(),
          note = note.trim().ifEmpty { null }, date = w.day, time = atTime(w.day, localTime(e.time).hour, localTime(e.time).minute),
        ),
      )
      onDone()
    }
  }, { Actions.removeHeart(e.id); onDone() }) {
    HRow {
      Field(sys, { sys = it }, Modifier.weight(1f), label = dict["heart.systolic"])
      Field(dia, { dia = it }, Modifier.weight(1f), label = dict["heart.diastolic"])
    }
    Field(pulse, { pulse = it }, Modifier.fillMaxWidth(), label = dict["heart.pulse"])
    Field(note, { note = it }, Modifier.fillMaxWidth(), label = dict["heart.note"], numeric = false)
    WhenFields(w, true)
  }
}

@Composable
fun BodyEdit(e: BodyEntry, onDone: () -> Unit) {
  val dict = L
  var weight by remember { mutableStateOf(numStr(e.weightKg)) }
  var fat by remember { mutableStateOf(numStr(e.bodyFatPct)) }
  var waist by remember { mutableStateOf(numStr(e.waistCm)) }
  var neck by remember { mutableStateOf(numStr(e.neckCm)) }
  var hip by remember { mutableStateOf(numStr(e.hipCm)) }
  var height by remember { mutableStateOf(numStr(e.heightCm)) }
  val w = remember { When(e.time, e.date) }
  EditFrame(dict["body.weight"], {
    val next = e.copy(
      weightKg = parseNum(weight), bodyFatPct = parseNum(fat), waistCm = parseNum(waist), neckCm = parseNum(neck), hipCm = parseNum(hip), heightCm = parseNum(height),
      date = w.day, time = atTime(w.day, localTime(e.time).hour, localTime(e.time).minute),
    )
    if (listOf(next.weightKg, next.bodyFatPct, next.waistCm, next.neckCm, next.hipCm, next.heightCm).any { it != null }) {
      Actions.updateBody(next)
      onDone()
    }
  }, { Actions.removeBody(e.id); onDone() }) {
    HRow {
      Field(weight, { weight = it }, Modifier.weight(1f), label = dict["body.weight"], suffix = dict["units.kg"])
      Field(fat, { fat = it }, Modifier.weight(1f), label = dict["body.fat"], suffix = "%")
    }
    HRow {
      Field(waist, { waist = it }, Modifier.weight(1f), label = dict["body.waist"], suffix = dict["units.cm"])
      Field(neck, { neck = it }, Modifier.weight(1f), label = dict["body.neck"], suffix = dict["units.cm"])
    }
    HRow {
      Field(hip, { hip = it }, Modifier.weight(1f), label = dict["body.hip"], suffix = dict["units.cm"])
      Field(height, { height = it }, Modifier.weight(1f), label = dict["body.height"], suffix = dict["units.cm"])
    }
    WhenFields(w, true)
  }
}
