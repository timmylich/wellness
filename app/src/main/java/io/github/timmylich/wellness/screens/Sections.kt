/** Overview sections: Summary, Nutrition, Water, Activity, Heart, Body. */
package io.github.timmylich.wellness.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.timmylich.wellness.data.Actions
import io.github.timmylich.wellness.data.AppData
import io.github.timmylich.wellness.data.BodyEntry
import io.github.timmylich.wellness.data.FoodEntry
import io.github.timmylich.wellness.data.HeartEntry
import io.github.timmylich.wellness.data.Nutrient
import io.github.timmylich.wellness.data.Store
import io.github.timmylich.wellness.data.WaterEntry
import io.github.timmylich.wellness.data.WorkoutEntry
import io.github.timmylich.wellness.data.WorkoutTemplate
import io.github.timmylich.wellness.lib.BASELINE_STEPS
import io.github.timmylich.wellness.lib.HealthConnect
import io.github.timmylich.wellness.lib.KCAL_PER_KG_FAT
import io.github.timmylich.wellness.lib.Nutrients
import io.github.timmylich.wellness.lib.Range
import io.github.timmylich.wellness.lib.Targets
import io.github.timmylich.wellness.lib.WaterRange
import io.github.timmylich.wellness.lib.activityKcalFor
import io.github.timmylich.wellness.lib.adaptiveCorrection
import io.github.timmylich.wellness.lib.age
import io.github.timmylich.wellness.lib.bmi
import io.github.timmylich.wellness.lib.bmiBodyFat
import io.github.timmylich.wellness.lib.bmiCategory
import io.github.timmylich.wellness.lib.bodyFatCategory
import io.github.timmylich.wellness.lib.bpCategory
import io.github.timmylich.wellness.lib.entryNutrients
import io.github.timmylich.wellness.lib.expenditureFor
import io.github.timmylich.wellness.lib.healthyWeight
import io.github.timmylich.wellness.lib.humanDate
import io.github.timmylich.wellness.lib.intakeKcalFor
import io.github.timmylich.wellness.lib.addDays
import io.github.timmylich.wellness.lib.lastDays
import io.github.timmylich.wellness.lib.maxHeartRate
import io.github.timmylich.wellness.lib.navyBodyFat
import io.github.timmylich.wellness.lib.nutritionAdvice
import io.github.timmylich.wellness.lib.pulseCategory
import io.github.timmylich.wellness.lib.rangeStatus
import io.github.timmylich.wellness.lib.referenceWeight
import io.github.timmylich.wellness.lib.shortLabel
import io.github.timmylich.wellness.lib.stepsKcal
import io.github.timmylich.wellness.lib.sumEntries
import io.github.timmylich.wellness.lib.targetsFor
import io.github.timmylich.wellness.lib.timeLabel
import io.github.timmylich.wellness.lib.todayKey
import io.github.timmylich.wellness.lib.wakingDayProgress
import io.github.timmylich.wellness.lib.waterRange
import io.github.timmylich.wellness.lib.weekdayLabel
import io.github.timmylich.wellness.lib.weightTrend
import io.github.timmylich.wellness.ui.AmountForm
import io.github.timmylich.wellness.ui.AppSheet
import io.github.timmylich.wellness.ui.Band
import io.github.timmylich.wellness.ui.BarChart
import io.github.timmylich.wellness.ui.BarDatum
import io.github.timmylich.wellness.ui.Bar
import io.github.timmylich.wellness.ui.BodyEdit
import io.github.timmylich.wellness.ui.BodyForm
import io.github.timmylich.wellness.ui.Button
import io.github.timmylich.wellness.ui.Card
import io.github.timmylich.wellness.ui.Chip
import io.github.timmylich.wellness.ui.DateNav
import io.github.timmylich.wellness.ui.Empty
import io.github.timmylich.wellness.ui.H
import io.github.timmylich.wellness.ui.HRow
import io.github.timmylich.wellness.ui.HeartEdit
import io.github.timmylich.wellness.ui.HeartForm
import io.github.timmylich.wellness.ui.Hint
import io.github.timmylich.wellness.ui.Icon
import io.github.timmylich.wellness.ui.L
import io.github.timmylich.wellness.ui.LineChart
import io.github.timmylich.wellness.ui.ListItem
import io.github.timmylich.wellness.ui.Nav
import io.github.timmylich.wellness.ui.NutrientDetails
import io.github.timmylich.wellness.ui.RangeBar
import io.github.timmylich.wellness.ui.Ring
import io.github.timmylich.wellness.ui.Route
import io.github.timmylich.wellness.ui.Segmented
import io.github.timmylich.wellness.ui.rememberEdgeSwipe
import io.github.timmylich.wellness.ui.Series
import io.github.timmylich.wellness.ui.Stat
import io.github.timmylich.wellness.ui.Stats
import io.github.timmylich.wellness.ui.StepsForm
import io.github.timmylich.wellness.ui.T
import io.github.timmylich.wellness.ui.TSpans
import io.github.timmylich.wellness.ui.Span
import io.github.timmylich.wellness.ui.TemplateForm
import io.github.timmylich.wellness.ui.TemplateGrid
import io.github.timmylich.wellness.ui.WaterEdit
import io.github.timmylich.wellness.ui.WaterForm
import io.github.timmylich.wellness.ui.WorkoutEdit
import io.github.timmylich.wellness.ui.WorkoutForm
import io.github.timmylich.wellness.ui.a
import io.github.timmylich.wellness.ui.c
import io.github.timmylich.wellness.ui.fmt
import io.github.timmylich.wellness.ui.heartTitle
import io.github.timmylich.wellness.ui.motion
import io.github.timmylich.wellness.ui.nutrientUnit
import io.github.timmylich.wellness.ui.tap
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Everything the nutrition and summary screens need for one day. */
class DaySummary(val entries: List<FoodEntry>, val totals: Nutrients, val targets: Targets, val expenditure: Double, val activityKcal: Double) {
  val balance get() = totals.kcal - expenditure
}

@Composable
fun daySummary(d: AppData, date: String): DaySummary = remember(date, d.foodLog, d.workouts, d.steps, d.profile, d.settings) {
  val entries = d.foodLog.filter { it.date == date }
  DaySummary(entries, sumEntries(entries), targetsFor(d, date), expenditureFor(d, date), activityKcalFor(d, date))
}

class WaterDay(val entries: List<WaterEntry>, val total: Double, val other: Double, val range: WaterRange) {
  val fluid get() = total + other
}

@Composable
fun waterDay(d: AppData, date: String): WaterDay = remember(date, d.water, d.workouts, d.profile, d.settings.waterDangerMl) {
  val entries = d.water.filter { it.date == date }.sortedByDescending { it.time }
  // Only plain water counts towards the target; every drink counts towards the danger limit.
  val minutes = d.workouts.filter { it.date == date }.sumOf { it.minutes }
  WaterDay(entries, entries.filter { !it.isOther }.sumOf { it.ml }, entries.filter { it.isOther }.sumOf { it.ml }, waterRange(d.profile, minutes, d.settings.waterDangerMl))
}

@Composable
private fun PeriodPicker(value: Int, onChange: (Int) -> Unit) =
  Segmented(value, listOf(7 to L["common.days7"], 30 to L["common.days30"], 90 to L["common.days90"], 365 to L["common.year"]), onChange)

// ---- summary ---------------------------------------------------------------------------

@Composable
fun Summary(onOpen: (String) -> Unit) {
  val d = Store.data
  val dict = L
  val haptic = LocalHapticFeedback.current
  val today = todayKey()
  val s = d.settings
  val steps = d.steps[today] ?: 0.0
  val lastHeart = d.heart.lastOrNull { it.systolic != null }
  val day = daySummary(d, today)
  val water = waterDay(d, today)
  val week = remember(d.foodLog, day.expenditure) {
    lastDays(7).map { dd ->
      val intake = intakeKcalFor(d, dd)
      Triple(dd, if (intake > 0) intake - expenditureFor(d, dd) else 0.0, intake > 0)
    }
  }
  val weekBalance = week.sumOf { it.second }
  val kcal = day.targets.getValue(Nutrient.Kcal)
  val left = kcal.target - day.totals.kcal

  if (s.section("nutrition")) Card(Modifier.tap(scaleTo = 0.98f) { onOpen("nutrition") }) {
    H(dict["summary.calories"], "nutrition")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Stat(dict["summary.eaten"], fmt(day.totals.kcal), unit = dict["units.kcal"])
      Stat(if (left >= 0) dict["summary.left"] else dict["summary.over"], fmt(abs(left)), unit = dict["units.kcal"], color = if (left >= 0) c.ok else c.danger)
      Stat(dict["summary.spent"], fmt(day.expenditure), unit = dict["units.kcal"])
    }
    RangeBar(day.totals.kcal, kcal, 14.dp)
    T(dict.t("summary.recommended", kcal.min, kcal.max, kcal.target) + if (day.activityKcal != 0.0) dict.t("summary.withActivity", day.activityKcal) else "", dim = true, size = 12)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      for (k in listOf(Nutrient.Protein, Nutrient.Fat, Nutrient.Carbs)) Column(Modifier.weight(1f)) {
        T(dict["nutrients.${k.key}.label"], dim = true, size = 12)
        T("${fmt(day.totals[k])} / ${fmt(day.targets.getValue(k).target)} ${dict["units.g"]}", bold = true)
        RangeBar(day.totals[k], day.targets.getValue(k), 6.dp)
      }
    }
  }

  if (s.section("water")) Card(Modifier.tap(scaleTo = 0.98f) { onOpen("water") }) {
    H(dict["summary.water"], "water")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      Ring(water.total, water.range.target, 100.dp, 10.dp, c.water) { T("${fmt(water.total / 1000, 2)} ${dict["units.l"]}", bold = true) }
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        T(dict.t("summary.waterGoal", water.range.target / 1000), dim = true, size = 13)
        Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            Modifier.weight(1f).tap(label = dict.t("water.addLabel", s.waterAmountMl)) {
              Actions.addWater(s.waterAmountMl)
              haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }.background(c.water, RoundedCornerShape(18.dp)).padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon("water", 20.dp, Color.White)
            T("+ ${fmt(s.waterAmountMl)} ${dict["units.ml"]}", size = 18, bold = true, color = Color.White, maxLines = 1)
          }
          Box(
            Modifier.width(58.dp).fillMaxHeight().tap(label = dict["water.kinds.other"]) {
              Actions.addWater(s.waterAmountMl, kind = "other")
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }.background(c.high.a(0.18f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
          ) { Icon("drink", 22.dp, c.high) }
        }
      }
    }
  }

  if (s.section("activity")) Card(Modifier.tap(scaleTo = 0.98f) { onOpen("activity") }) {
    H(dict["summary.activity"], "activity")
    Stats {
      Stat(dict["summary.steps"], fmt(steps), Modifier.weight(1f), "/ ${fmt(s.stepGoal)}", if (steps >= s.stepGoal) c.ok else null)
      Stat(dict["summary.burned"], fmt(day.activityKcal), Modifier.weight(1f), dict["units.kcal"])
    }
    Bar((steps / s.stepGoal).toFloat(), c.accent)
  }

  if (s.section("nutrition")) Card {
    H(dict["summary.week"], "flame")
    BarChart(
      week.map { (dd, bal, logged) -> BarDatum(weekdayLabel(dd, dict), abs(bal), if (!logged) c.border else if (bal > 0) c.high.a(0.85f) else c.ok.a(0.85f)) },
      height = 120.dp,
    )
    TSpans(
      Span("${dict["summary.weekTotal"]}: "),
      Span("${if (weekBalance > 0) "+" else "−"}${fmt(abs(weekBalance))} ${dict["units.kcal"]}", if (weekBalance > 0) c.high else c.ok, bold = true),
      Span(" ${dict.fatEq(abs(weekBalance) / KCAL_PER_KG_FAT * 1000, weekBalance > 0)}"),
    )
    Hint(dict["summary.weekHintTitle"], dict["summary.weekHint"])
  }

  Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    if (s.section("heart")) Card(Modifier.weight(1f).fillMaxHeight().tap(scaleTo = 0.97f) { onOpen("heart") }) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon("heart", 16.dp, c.accent)
        T(dict["summary.pressure"], dim = true, size = 12)
      }
      if (lastHeart != null) {
        val bp = bpCategory(lastHeart.systolic!!, lastHeart.diastolic ?: 0.0)
        T("${fmt(lastHeart.systolic)}/${fmt(lastHeart.diastolic ?: 0.0)}", size = 20, bold = true)
        T(dict["heart.bp.${bp.key}.label"], size = 12, color = c.tone(bp.tone))
      } else T(dict["summary.noEntries"], dim = true)
    }
    if (s.section("body")) Card(Modifier.weight(1f).fillMaxHeight().tap(scaleTo = 0.97f) { onOpen("body") }) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon("body", 16.dp, c.accent)
        T(dict["summary.weight"], dim = true, size = 12)
      }
      T("${fmt(d.profile.weightKg, 1)} ${dict["units.kg"]}", size = 20, bold = true)
      T(dict["summary.tapToUpdate"], size = 12, dim = true)
    }
  }
}

// ---- nutrition -------------------------------------------------------------------------

@Composable
private fun NutrientRow(k: Nutrient, value: Double, range: Range, onOpen: (Nutrient) -> Unit) {
  val dict = L
  val status = rangeStatus(value, range)
  Column(Modifier.fillMaxWidth().tap(scaleTo = 0.98f) { onOpen(k) }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      T(dict["nutrients.${k.key}.label"])
      TSpans(
        Span(fmt(value, if (value < 10 && value % 1 != 0.0) 1 else 0), c.tone(if (status == "high") "danger" else if (status == "low") "low" else "ok"), bold = true),
        Span(" / ${if (range.lowerIsBetter) "≤ ${fmt(range.max)}" else fmt(range.target)} ${nutrientUnit(k, dict)}", size = 13, dim = true),
      )
    }
    RangeBar(value, range, 8.dp)
  }
}

@Composable
fun Nutrition() {
  val d = Store.data
  val dict = L
  var date by rememberSaveable { mutableStateOf(todayKey()) }
  var editing by remember { mutableStateOf<FoodEntry?>(null) }
  var detail by remember { mutableStateOf<Nutrient?>(null) }
  var period by rememberSaveable { mutableStateOf(7) }
  var chartKey by rememberSaveable { mutableStateOf(Nutrient.Kcal) }
  val day = daySummary(d, date)
  val totals = day.totals
  val targets = day.targets
  val advice = nutritionAdvice(totals, targets, if (date == todayKey()) wakingDayProgress() else 1.0)
  val correction = remember(date, d.foodLog, d.settings) { adaptiveCorrection(d, date) }
  val trend = remember(period, date, d.foodLog, d.workouts, d.steps, d.profile, d.settings, chartKey) {
    // Trend charts always end today: they show history, not the selected day.
    // Each day reads only its own week (the adaptive correction looks 7 days back), so a year
    // of history does not rescan the whole log for every bar.
    val food = d.foodLog.groupBy { it.date }
    val workouts = d.workouts.groupBy { it.date }
    lastDays(period).map { dd ->
      val week = lastDays(8, dd)
      val slice = d.copy(foodLog = week.flatMap { food[it].orEmpty() }, workouts = week.flatMap { workouts[it].orEmpty() })
      val entries = food[dd].orEmpty()
      TrendDay(dd, sumEntries(entries), entries.isNotEmpty(), expenditureFor(slice, dd), targetsFor(slice, dd).getValue(chartKey))
    }
  }
  val logged = trend.filter { it.logged }
  val periodBalance = logged.sumOf { it.n.kcal - it.exp }
  val kcal = targets.getValue(Nutrient.Kcal)
  val left = kcal.target - totals.kcal
  val lastTarget = trend.last().target
  val rows: @Composable (List<Nutrient>) -> Unit = { keys -> for (k in keys) NutrientRow(k, totals[k], targets.getValue(k)) { detail = it } }

  val pages: List<Pair<String, @Composable () -> Unit>> = listOf(
    dict["nutrition.pages.calories"] to {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.tap(label = dict["nutrients.kcal.label"]) { detail = Nutrient.Kcal }) {
          Ring(totals.kcal, kcal.target, 120.dp, 12.dp, if (totals.kcal > kcal.max) c.danger else c.accent) {
            T(fmt(totals.kcal), size = 20, bold = true)
            T("/ ${fmt(kcal.target)}", dim = true, size = 12)
          }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Stat(if (left >= 0) dict["summary.left"] else dict["summary.over"], fmt(abs(left)), unit = dict["units.kcal"], color = if (left >= 0) c.ok else c.danger)
          T(dict.t("summary.recommended", kcal.min, kcal.max, kcal.target), dim = true, size = 12)
        }
      }
      rows(listOf(Nutrient.Protein, Nutrient.Fat, Nutrient.Carbs))
      if (d.settings.adaptiveTargets && correction != 0.0) T(dict.adaptive(correction), size = 12, color = c.accent)
    },
    dict["nutrition.pages.quality"] to {
      rows(listOf(Nutrient.Sugar, Nutrient.AddedSugar, Nutrient.Fiber, Nutrient.SatFat, Nutrient.TransFat, Nutrient.Cholesterol, Nutrient.Sodium, Nutrient.Potassium))
      Hint(dict["nutrition.scalesHintTitle"], dict["nutrition.scalesHint"])
    },
    dict["nutrition.pages.balance"] to {
      val bal = day.balance
      Stats {
        Stat(dict["summary.eaten"], fmt(totals.kcal), Modifier.weight(1f), dict["units.kcal"])
        Stat(dict["summary.spent"], fmt(day.expenditure), Modifier.weight(1f), dict["units.kcal"])
        Stat(if (bal > 0) dict["nutrition.surplus"] else dict["nutrition.deficit"], fmt(abs(bal)), Modifier.weight(1f), dict["units.kcal"], if (bal > 0) c.high else c.ok)
      }
      T(dict.t("nutrition.fatMass", abs(bal) / KCAL_PER_KG_FAT * 1000), dim = true, size = 13)
      T(dict["nutrition.intakeVsSpent"], dim = true, size = 13)
      val last7 = trend.takeLast(7)
      LineChart(
        last7.map { shortLabel(it.d) },
        listOf(Series(last7.map { if (it.logged) Math.round(it.n.kcal).toDouble() else null }, c.accent), Series(last7.map { it.exp }, c.textDim)),
        height = 130.dp,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        T("● ${dict["nutrition.eatenLegend"]}", size = 12, color = c.accent)
        T("● ${dict["nutrition.spentLegend"]}", size = 12, dim = true)
      }
      for (a in advice) Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(c.tone(a.tone), RoundedCornerShape(2.dp)))
        T(dict["advice.${a.key}"], Modifier.weight(1f), size = 14)
      }
    },
    dict["nutrition.pages.trends"] to {
      PeriodPicker(period) { period = it }
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (k in Nutrient.entries) Chip(dict["nutrients.${k.key}.short"], active = chartKey == k) { chartKey = k }
      }
      BarChart(
        trend.map { BarDatum(shortLabel(it.d), it.n[chartKey], if (rangeStatus(it.n[chartKey], it.target) == "high") c.danger else if (it.logged) c.accent else c.border) },
        bands = listOf(Band(if (lastTarget.lowerIsBetter) 0.0 else lastTarget.min, lastTarget.max, c.ok.a(0.12f))),
        target = if (lastTarget.lowerIsBetter) null else lastTarget.target,
        height = 130.dp,
      )
      T(dict.periodBalance(logged.size, periodBalance, abs(periodBalance) / KCAL_PER_KG_FAT), dim = true, size = 12)
    },
  )

  DateNav(date) { date = it }
  StatCarousel(pages)
  Button(dict["nutrition.addFood"], Modifier.fillMaxWidth(), icon = "plus", big = true) { Nav.push(Route.FoodSearch(date)) }
  Card {
    T(dict["nutrition.log"], size = 17, bold = true)
    val sorted = day.entries.sortedBy { it.time }
    if (sorted.isEmpty()) Empty(dict["nutrition.emptyLog"])
    for (e in sorted) {
      val n = entryNutrients(e)
      val amount = if (e.pieces != null) "${e.pieces} × ${fmt(e.grams / e.pieces)}" else fmt(e.grams)
      ListItem(
        e.food.name,
        "${timeLabel(e.time)} · $amount ${dict["units.g"]} · ${dict["nutrients.protein.short"]} ${fmt(n[Nutrient.Protein])} ${dict["nutrients.fat.short"]} ${fmt(n[Nutrient.Fat])} ${dict["nutrients.carbs.short"]} ${fmt(n[Nutrient.Carbs])}",
        right = { T(fmt(n.kcal), bold = true) },
        onDelete = { Actions.removeFoodEntry(e.id) },
      ) { editing = e }
    }
    if (sorted.isNotEmpty()) T(dict["common.longPressDelete"], dim = true, size = 12)
  }

  detail?.let { k ->
    AppSheet({ detail = null }) { NutrientDetails(k, totals[k], targets.getValue(k), day.entries) }
  }
  editing?.let { e ->
    AppSheet({ editing = null }) {
      AmountForm(
        e.food, e.grams, e.pieces, dict["common.save"],
        onEditData = {
          close()
          Nav.push(Route.FoodEdit(entry = e.id))
        },
        onDelete = {
          Actions.removeFoodEntry(e.id)
          close()
        },
      ) { g, p ->
        Actions.updateFoodEntry(e.id) { it.copy(grams = g, pieces = p.takeIf { x -> x > 1 }) }
        close()
      }
    }
  }
}

private class TrendDay(val d: String, val n: Nutrients, val logged: Boolean, val exp: Double, val target: Range)

/** Swipeable stat cards; the card follows the height of the page shown, with page dots. */
@Composable
private fun StatCarousel(pages: List<Pair<String, @Composable () -> Unit>>) {
  val pager = rememberPagerState { pages.size }
  val scope = rememberCoroutineScope()
  val edge = rememberEdgeSwipe(LocalSections.current)
  Card(padding = 0.dp, gap = 0.dp) {
    HorizontalPager(pager, Modifier.fillMaxWidth().nestedScroll(edge).animateContentSize(motion(spring(stiffness = 400f))), verticalAlignment = Alignment.Top) { i ->
      Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        T(pages[i].first, size = 17, bold = true)
        pages[i].second()
      }
    }
    // Page dots with one sliding indicator.
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
      Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        repeat(pages.size) { i -> Box(Modifier.size(7.dp).tap { scope.launch { pager.animateScrollToPage(i) } }.background(c.accent.a(0.3f), CircleShape)) }
      }
      val pos = pager.currentPage + pager.currentPageOffsetFraction
      Box(
        Modifier.offset { IntOffset(((pos - (pages.size - 1) / 2f) * 16.dp.toPx()).roundToInt(), 0) }.size(18.dp, 8.dp).background(c.accent, CircleShape),
      )
    }
  }
}

// ---- water -----------------------------------------------------------------------------

@Composable
fun Water() {
  val d = Store.data
  val dict = L
  var date by rememberSaveable { mutableStateOf(todayKey()) }
  var period by rememberSaveable { mutableStateOf(7) }
  var editing by remember { mutableStateOf<WaterEntry?>(null) }
  val w = waterDay(d, date)
  val range = w.range
  // Target progress uses plain water; the danger state uses all fluid.
  val status = when {
    w.fluid >= range.danger -> "danger"
    w.total > range.max -> "high"
    w.total >= range.target -> "good"
    w.total >= range.min -> "ok"
    else -> "low"
  }
  val color = when (status) {
    "danger" -> c.danger
    "high" -> c.high
    "low" -> c.low
    else -> c.water
  }
  val chart = remember(period, d.water) {
    val byDate = d.water.groupBy { it.date }
    lastDays(period).map { dd ->
      val day = byDate[dd].orEmpty()
      Triple(shortLabel(dd), day.filter { !it.isOther }.sumOf { it.ml }, day.sumOf { it.ml })
    }
  }
  val withData = chart.filter { it.second > 0 }
  val avg = if (withData.isEmpty()) 0.0 else withData.sumOf { it.second } / withData.size

  DateNav(date) { date = it }
  Card {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Ring(w.total, range.target, 180.dp, 16.dp, color) {
        T(fmt(w.total / 1000, 2), size = 32, bold = true)
        T(dict.t("water.of", range.target / 1000), dim = true)
      }
      T(dict["water.status.$status"], color = color, bold = true, center = true)
    }
    Stats {
      Stat(dict["water.pure"], fmt(w.total / 1000, 2), Modifier.weight(1f), dict["units.l"], c.water)
      Stat(dict["water.otherDrunk"], fmt(w.other / 1000, 2), Modifier.weight(1f), dict["units.l"], c.high)
      Stat(dict["water.totalFluid"], fmt(w.fluid / 1000, 2), Modifier.weight(1f), dict["units.l"])
    }
    WaterForm(date)
  }
  Card {
    H(dict["water.journal"], "glass")
    if (w.entries.isEmpty()) Empty(dict["water.empty"])
    for (e in w.entries) ListItem(
      "${fmt(e.ml)} ${dict["units.ml"]}",
      "${timeLabel(e.time)} · ${dict["water.kinds.${if (e.isOther) "other" else "water"}"]}",
      left = { Icon(if (e.isOther) "drink" else "water", 20.dp, if (e.isOther) c.high else c.water) },
      onDelete = { Actions.removeWater(e.id) },
    ) { editing = e }
    if (w.entries.isNotEmpty()) T(dict["common.longPressDelete"], dim = true, size = 12)
  }
  Card {
    H(dict["water.norms"], "target")
    Stats {
      Stat(dict["common.min"], fmt(range.min / 1000, 1), Modifier.weight(1f), dict["units.l"], c.low)
      Stat(dict["common.target"], fmt(range.target / 1000, 1), Modifier.weight(1f), dict["units.l"], c.water)
      Stat(dict["common.max"], fmt(range.max / 1000, 1), Modifier.weight(1f), dict["units.l"], c.high)
      Stat(dict["common.danger"], ">${fmt(range.danger / 1000, 1)}", Modifier.weight(1f), dict["units.l"], c.danger)
    }
    T(dict.t("water.dangerNote", w.fluid / 1000), dim = true, size = 12)
    Hint(dict["water.hintTitle"]) { for (p in dict.list("water.hint")) T(p, size = 14) }
  }
  Card {
    H(dict["water.chart"], right = { PeriodPicker(period) { period = it } })
    BarChart(
      chart.map { (l, v, fluid) -> BarDatum(l, v, if (fluid >= range.danger) c.danger else if (v > range.max) c.high else if (v >= range.min) c.water else c.water.a(0.4f)) },
      bands = listOf(Band(range.min, range.max, c.ok.a(0.12f)), Band(range.danger, range.danger * 2, c.danger.a(0.12f))),
      target = range.target,
      // Axis ends at the recommended maximum; once values get close it grows to value + 1 L.
      maxY = ceil(max(range.max, (chart.maxOfOrNull { it.second } ?: 0.0) + 1000) / 500) * 500,
      format = { "${fmt(it / 1000, 2)} ${dict["units.l"]}" },
    )
    T(dict.t("water.avg", avg / 1000), dim = true, size = 13)
  }
  editing?.let { e -> AppSheet({ editing = null }) { WaterEdit(e, close) } }
}

// ---- activity --------------------------------------------------------------------------

private sealed interface ActivitySheet {
  data class Log(val template: WorkoutTemplate?) : ActivitySheet
  data class Template(val template: WorkoutTemplate?) : ActivitySheet
  data object Steps : ActivitySheet
}

@Composable
fun Activity() {
  val d = Store.data
  val dict = L
  val ctx = LocalContext.current
  val scope = rememberCoroutineScope()
  var date by rememberSaveable { mutableStateOf(todayKey()) }
  var period by rememberSaveable { mutableStateOf(7) }
  var sheet by remember { mutableStateOf<ActivitySheet?>(null) }
  var editing by remember { mutableStateOf<WorkoutEntry?>(null) }
  var syncedAt by remember { mutableStateOf<Long?>(null) }
  val s = d.settings
  val steps = d.steps[date] ?: 0.0
  val workouts = d.workouts.filter { it.date == date }
  val chart = remember(period, date, d.steps) { lastDays(period, date).map { shortLabel(it) to (d.steps[it] ?: 0.0) } }

  DateNav(date) { date = it }
  Card {
    H(dict["activity.steps"], "activity", right = { T(dict["common.edit"], Modifier.tap { sheet = ActivitySheet.Steps }, color = c.accent) })
    T(fmt(steps), size = 40, bold = true)
    Bar((steps / s.stepGoal).toFloat(), if (steps >= s.stepGoal) c.ok else c.accent, 10.dp)
    Stats {
      Stat(dict["activity.goal"], fmt(s.stepGoal), Modifier.weight(1f))
      Stat(dict["activity.distance"], fmt(steps / 1300, 1), Modifier.weight(1f), dict["units.km"])
      Stat(dict["activity.extra"], fmt(stepsKcal(steps, d.profile.weightKg)), Modifier.weight(1f), dict["units.kcal"])
    }
    if (s.stepsSource == "healthConnect") Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
      T(syncedAt?.let { dict.t("activity.synced", timeLabel(it)) } ?: "Health Connect", dim = true, size = 12)
      Button(dict["activity.sync"], kind = "secondary", icon = "refresh") {
        scope.launch {
          if (HealthConnect.syncSteps(ctx, 30)) syncedAt = System.currentTimeMillis() else Nav.push(Route.Settings("steps"))
        }
      }
    }
    Hint(dict["activity.stepsHintTitle"]) {
      for (p in dict.list(if (s.stepsSource == "device") "activity.info.device" else "activity.info.hc")) T(p, size = 14)
      T(dict.t("activity.stepsHintBaseline", BASELINE_STEPS), size = 14)
    }
  }
  Card {
    H(dict["activity.workouts"], "workout", right = { T("${fmt(workouts.sumOf { it.kcal })} ${dict["units.kcal"]}", dim = true, size = 13) })
    if (workouts.isEmpty()) Empty(dict["activity.workoutsEmpty"])
    for (w in workouts) ListItem(
      w.name, "${fmt(w.minutes)} ${dict["units.min"]}${w.note?.let { " · $it" } ?: ""}",
      right = { T("${fmt(w.kcal)} ${dict["units.kcal"]}", bold = true) },
      onDelete = { Actions.removeWorkout(w.id) },
    ) { editing = w }
    if (workouts.isNotEmpty()) T(dict["common.longPressDelete"], dim = true, size = 12)
    Button(dict["activity.custom"], Modifier.fillMaxWidth(), kind = "secondary", icon = "plus") { sheet = ActivitySheet.Log(null) }
  }
  Card {
    H(dict["activity.templates"], right = { T("+ ${dict["activity.newTemplate"]}", Modifier.tap { sheet = ActivitySheet.Template(null) }, color = c.accent) })
    T(dict["activity.templatesHint"], dim = true, size = 13)
    TemplateGrid({ sheet = ActivitySheet.Log(it) }, { sheet = ActivitySheet.Template(it) })
  }
  Card {
    H(dict["activity.byDay"], right = { PeriodPicker(period) { period = it } })
    BarChart(chart.map { (l, v) -> BarDatum(l, v, if (v >= s.stepGoal) c.ok else c.accent) }, target = s.stepGoal)
  }
  sheet?.let { sh ->
    AppSheet({ sheet = null }) {
      when (sh) {
        ActivitySheet.Steps -> StepsForm(date, close)
        is ActivitySheet.Log -> WorkoutForm(sh.template, date, close)
        is ActivitySheet.Template -> TemplateForm(sh.template, close)
      }
    }
  }
  editing?.let { e -> AppSheet({ editing = null }) { WorkoutEdit(e, close) } }
}

// ---- heart -----------------------------------------------------------------------------

private val BP_CLASSES = listOf(
  Triple("normal", "< 120 / < 80", "ok"), Triple("elevated", "120–129 / < 80", "high"), Triple("stage1", "130–139 / 80–89", "high"),
  Triple("stage2", "≥ 140 / ≥ 90", "danger"), Triple("crisis", "> 180 / > 120", "danger"), Triple("low", "< 90 / < 60", "low"),
)
private val ZONES = listOf(0.5 to 0.6, 0.6 to 0.7, 0.7 to 0.8, 0.8 to 0.9, 0.9 to 1.0)

@Composable
fun Heart() {
  val d = Store.data
  val dict = L
  var count by rememberSaveable { mutableStateOf(14) }
  var editing by remember { mutableStateOf<HeartEntry?>(null) }
  val sorted = remember(d.heart) { d.heart.sortedBy { it.time } }
  val recent = sorted.takeLast(count)
  val bp = recent.filter { it.systolic != null && it.diastolic != null }
  val pulses = recent.mapNotNull { it.pulse }
  val avgSys = bp.map { it.systolic!! }.average()
  val avgDia = bp.map { it.diastolic!! }.average()
  val avgBp = if (bp.isNotEmpty()) bpCategory(avgSys, avgDia) else null
  val hrMax = maxHeartRate(d.profile)

  Card {
    H(dict["heart.newMeasurement"], "heart")
    HeartForm()
  }
  Card {
    H(dict["heart.stats"], right = { Segmented(count, listOf(14 to "14", 30 to "30", 90 to "90", 365 to "365")) { count = it } })
    Stats {
      Stat(dict["heart.avgBp"], if (bp.isNotEmpty()) "${fmt(avgSys)}/${fmt(avgDia)}" else "—", Modifier.weight(1f))
      Stat(dict["heart.avgPulse"], if (pulses.isNotEmpty()) fmt(pulses.average()) else "—", Modifier.weight(1f), dict["units.bpm"])
      Stat(dict["heart.count"], recent.size.toString(), Modifier.weight(1f))
    }
    if (avgBp != null) T(dict.t("heart.onAverage", dict["heart.bp.${avgBp.key}.label"]), color = c.tone(avgBp.tone), bold = true)
    if (pulses.isNotEmpty()) T(dict.t("heart.restingPulse", dict["heart.pulseCat.${pulseCategory(pulses.average()).key}"]), size = 13, dim = true)
    T(dict.t("heart.bpChart", count), Modifier.padding(top = 6.dp), dim = true, size = 13)
    LineChart(
      recent.map { shortLabel(it.date) },
      listOf(Series(recent.map { it.systolic }, c.danger), Series(recent.map { it.diastolic }, c.water)),
      bands = listOf(Band(60.0, 80.0, c.ok.a(0.1f)), Band(90.0, 120.0, c.ok.a(0.1f)), Band(140.0, 220.0, c.danger.a(0.08f))),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      T("● ${dict["heart.upper"]}", size = 12, color = c.danger)
      T("● ${dict["heart.lower"]}", size = 12, color = c.water)
      T(dict["heart.normBands"], size = 12, dim = true)
    }
    T(dict["heart.pulseChart"], Modifier.padding(top = 6.dp), dim = true, size = 13)
    LineChart(recent.map { shortLabel(it.date) }, listOf(Series(recent.map { it.pulse }, c.accent)), bands = listOf(Band(60.0, 100.0, c.ok.a(0.1f))), height = 140.dp)
  }
  Card {
    H(dict["heart.classification"])
    for ((k, v, tone) in BP_CLASSES) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      T(dict["heart.bp.$k.label"], color = c.tone(tone))
      T(v, dim = true)
    }
    T(dict["heart.classNote"], dim = true, size = 12)
  }
  Card {
    H(dict["heart.zones"], "pulse")
    T(dict.t("heart.maxHr", hrMax), dim = true, size = 13)
    val names = dict.list("heart.zoneNames")
    ZONES.forEachIndexed { i, (a, b) ->
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        T(names.getOrElse(i) { "" })
        T("${Math.round(hrMax * a)}–${Math.round(hrMax * b)}", dim = true)
      }
    }
  }
  Card {
    H(dict["heart.log"])
    if (sorted.isEmpty()) Empty(dict["common.noRecords"])
    for (e in sorted.asReversed().take(50)) {
      val cat = if (e.systolic != null && e.diastolic != null) bpCategory(e.systolic, e.diastolic) else null
      ListItem(
        heartTitle(e), "${humanDate(e.date, dict)}, ${timeLabel(e.time)}${e.note?.let { " · $it" } ?: ""}",
        right = cat?.let { { T(dict["heart.bp.${it.key}.label"], size = 12, color = c.tone(it.tone)) } },
        onDelete = { Actions.removeHeart(e.id) },
      ) { editing = e }
    }
    if (sorted.isNotEmpty()) T(dict["common.longPressDelete"], dim = true, size = 12)
  }
  Hint(dict["heart.urgentTitle"], dict["heart.urgent"], force = true)
  editing?.let { e -> AppSheet({ editing = null }) { HeartEdit(e, close) } }
}

// ---- body ------------------------------------------------------------------------------

@Composable
fun Body() {
  val d = Store.data
  val dict = L
  val p = d.profile
  var editing by remember { mutableStateOf<BodyEntry?>(null) }
  val sorted = remember(d.body) { d.body.sortedBy { it.time } }
  fun last(f: (BodyEntry) -> Double?) = sorted.lastOrNull { f(it) != null }?.let(f)
  val waist = last { it.waistCm }
  val neck = last { it.neckCm }
  val navy = if (waist != null && neck != null) navyBodyFat(p.sex, p.heightCm, waist, neck, last { it.hipCm }) else null
  val measured = last { it.bodyFatPct }
  val b = bmi(p.weightKg, p.heightCm)
  val fatPct = measured ?: navy ?: bmiBodyFat(p.sex, b, age(p))
  val fatSource = if (measured != null) null else if (navy != null) "navy" else "bmi"
  val cat = bmiCategory(b)
  val hw = healthyWeight(p.heightCm, p.sex)
  val whtr = waist?.let { it / p.heightCm }
  val ref = referenceWeight(p)
  var period by rememberSaveable { mutableStateOf(90) }
  val since = addDays(todayKey(), 1 - period)
  val weights = sorted.filter { it.weightKg != null && it.date >= since }
  // Time-weighted moving average smooths daily water fluctuations.
  val smooth = weightTrend(weights.map { it.time to it.weightKg!! })

  Card {
    H(dict["body.now"], "body")
    Stats {
      Stat(dict["body.weight"], fmt(p.weightKg, 1), Modifier.weight(1f), dict["units.kg"])
      Stat(dict["body.target"], fmt(p.targetWeightKg, 1), Modifier.weight(1f), dict["units.kg"])
      Stat(dict["body.height"], fmt(p.heightCm), Modifier.weight(1f), dict["units.cm"])
    }
    Stats {
      Stat(dict["body.bmi"], fmt(b, 1), Modifier.weight(1f), color = c.tone(cat.tone))
      Stat(dict["body.fat"], (if (fatSource == "bmi") "≈" else "") + fmt(fatPct, 1), Modifier.weight(1f), "%")
      Stat(dict["body.whtr"], whtr?.let { fmt(it, 2) } ?: "—", Modifier.weight(1f), color = whtr?.let { if (it < 0.5) c.ok else if (it < 0.6) c.high else c.danger })
    }
    T(dict.t("body.bmiLine", dict["body.bmiCat.${cat.key}"]), color = c.tone(cat.tone))
    T(dict.fatLine(dict["body.fatCat.${bodyFatCategory(p.sex, fatPct)}"], fatSource), dim = true, size = 13)
  }
  Card {
    H(dict["body.healthy"], "target")
    T(dict.t("body.healthyLine", p.heightCm, hw.min, hw.max))
    T(dict.t("body.devine", hw.devine), dim = true, size = 13)
    if (abs(ref - p.weightKg) > 0.5) T(dict.t("body.refWeight", ref), dim = true, size = 13)
    Hint(dict["body.whyTitle"], dict["body.why"])
  }
  Card {
    H(dict["body.measure"], "ruler")
    BodyForm()
  }
  Card {
    H(dict["body.trend"], right = { PeriodPicker(period) { period = it } })
    if (weights.isEmpty()) Empty(dict[if (sorted.any { it.weightKg != null }) "common.noRecords" else "body.first"])
    else {
      LineChart(
        weights.map { shortLabel(it.date) },
        listOf(Series(weights.map { it.weightKg }, c.accent.a(0.4f)), Series(smooth, c.accent)),
        bands = listOf(Band(hw.min, hw.max, c.ok.a(0.1f))),
      )
      T(dict["body.trendNote"], dim = true, size = 12)
      if (weights.size > 1) T(dict.change(smooth.last() - smooth.first(), humanDate(weights[0].date, dict)))
    }
  }
  Card {
    H(dict["body.log"])
    if (sorted.isEmpty()) Empty(dict["common.noRecords"])
    for (e in sorted.asReversed().take(50)) {
      val title = listOfNotNull(
        e.weightKg?.let { "${fmt(it, 1)} ${dict["body.logParts.kg"]}" },
        e.bodyFatPct?.let { "${fmt(it, 1)} ${dict["body.logParts.fat"]}" },
        e.waistCm?.let { "${dict["body.logParts.waist"]} ${fmt(it)}" },
        e.neckCm?.let { "${dict["body.logParts.neck"]} ${fmt(it)}" },
        e.hipCm?.let { "${dict["body.logParts.hip"]} ${fmt(it)}" },
        e.heightCm?.let { "${dict["body.logParts.height"]} ${fmt(it)}" },
      ).joinToString(" · ")
      ListItem(title, humanDate(e.date, dict), onDelete = { Actions.removeBody(e.id) }) { editing = e }
    }
    if (sorted.isNotEmpty()) T(dict["common.longPressDelete"], dim = true, size = 12)
  }
  editing?.let { e -> AppSheet({ editing = null }) { BodyEdit(e, close) } }
}
