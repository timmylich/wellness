/**
 * Health math. Every formula cites its source so contributors
 * can verify it. None of this is medical advice: ranges are population guidelines for healthy adults.
 */
package io.github.timmylich.wellness.lib

import io.github.timmylich.wellness.data.AppData
import io.github.timmylich.wellness.data.FoodEntry
import io.github.timmylich.wellness.data.Nutrient
import io.github.timmylich.wellness.data.Per100
import io.github.timmylich.wellness.data.Profile
import io.github.timmylich.wellness.data.RangeOverride
import io.github.timmylich.wellness.data.Sex
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sign

/** JS Math.round (half up, also for negatives). */
fun jsRound(v: Double): Double = kotlin.math.floor(v + 0.5)

/** Energy stored in 1 kg of body fat tissue (commonly used ~7700 kcal estimate). */
const val KCAL_PER_KG_FAT = 7700.0

fun age(p: Profile): Int = Calendar.getInstance().get(Calendar.YEAR) - p.birthYear

/** Basal metabolic rate, Mifflin–St Jeor (1990). kcal/day. */
fun bmr(p: Profile): Double {
  p.bmrOverride?.takeIf { it > 0 }?.let { return it }
  val base = 10 * p.weightKg + 6.25 * p.heightCm - 5 * age(p)
  return jsRound(base + if (p.sex == Sex.Male) 5 else -161)
}

/** Everyday expenditure without logged workouts and extra steps. */
fun baseExpenditure(p: Profile): Double = jsRound(bmr(p) * p.lifestyleFactor)

/** Slider limits for the weekly weight change, kg/week. */
const val PACE_MIN = -1.5
const val PACE_MAX = 1.5

/** Signed daily calorie delta for the chosen weekly change (negative = deficit). */
fun goalDelta(p: Profile): Double = jsRound(p.weeklyChangeKg * KCAL_PER_KG_FAT / 7)

/**
 * Safety assessment of the weekly change. Loss above ~1 % of body weight per week raises the
 * risk of muscle loss and gallstones; gain above 0.5 kg/week is mostly fat.
 * Returns lossFast | lossVeryFast | gainFast or null.
 */
fun paceWarning(p: Profile): String? {
  val pct = -p.weeklyChangeKg / p.weightKg * 100
  return when {
    pct > 1.25 -> "lossVeryFast"
    pct > 1 -> "lossFast"
    p.weeklyChangeKg > 0.5 -> "gainFast"
    else -> null
  }
}

/** Weeks until the target weight at the chosen pace, or null if the pace points elsewhere. */
fun weeksToTarget(p: Profile): Int? {
  val diff = p.targetWeightKg - p.weightKg
  if (abs(diff) < 0.3 || p.weeklyChangeKg == 0.0 || sign(diff) != sign(p.weeklyChangeKg)) return null
  return ceil(diff / p.weeklyChangeKg).toInt()
}

/**
 * Body weight for weight-scaled norms (protein, water, fat floor). At high body weight g/kg norms
 * become unrealistically high, so following the clinical "adjusted body weight" idea (ASPEN/ESPEN)
 * we start from the goal weight (or the BMI-25 weight) and add 25 % of the excess.
 */
fun referenceWeight(p: Profile): Double {
  val h = p.heightCm / 100
  val healthyMax = 24.9 * h * h
  if (p.weightKg <= healthyMax) return p.weightKg
  val goal = if (p.targetWeightKg < p.weightKg) p.targetWeightKg else healthyMax
  val base = min(p.weightKg, max(healthyMax, goal))
  return base + 0.25 * (p.weightKg - base)
}

/** Steps below this are assumed already covered by the lifestyle factor. */
const val BASELINE_STEPS = 3000

/** Rough walking cost: ~0.5 kcal per kg per km, ~1300 steps per km. */
fun stepsKcal(steps: Double, weightKg: Double): Double = jsRound(max(0.0, steps - BASELINE_STEPS) / 1300 * 0.5 * weightKg)

/** Calories burned for a MET activity (Compendium of Physical Activities); resting 1 MET is in BMR. */
fun metKcal(met: Double, weightKg: Double, minutes: Double): Double = jsRound(max(0.0, met - 1) * weightKg * (minutes / 60))

// ---- nutrient sums ---------------------------------------------------------------------

/** Complete nutrient totals, indexed by [Nutrient]. */
class Nutrients(val v: DoubleArray = DoubleArray(Nutrient.entries.size)) {
  operator fun get(n: Nutrient) = v[n.ordinal]
  val kcal get() = v[Nutrient.Kcal.ordinal]
}

fun entryNutrients(food: Per100, grams: Double): Nutrients {
  val f = grams / 100
  return Nutrients(DoubleArray(Nutrient.entries.size) { (food[Nutrient.entries[it]] ?: 0.0) * f })
}

fun entryNutrients(e: FoodEntry) = entryNutrients(e.food.per100g, e.grams)

fun sumEntries(entries: List<FoodEntry>): Nutrients {
  val out = DoubleArray(Nutrient.entries.size)
  for (e in entries) {
    val f = e.grams / 100
    for (n in Nutrient.entries) out[n.ordinal] += (e.food.per100g[n] ?: 0.0) * f
  }
  return Nutrients(out)
}

// ---- daily targets ---------------------------------------------------------------------

/**
 * A recommended range. `min` = intake below which health suffers, `target` = the sweet spot,
 * `max` = above it the goal is unreachable or unsafe. [lowerIsBetter] marks limits where 0 is fine.
 */
data class Range(val min: Double, val target: Double, val max: Double, val lowerIsBetter: Boolean = false)

typealias Targets = Map<Nutrient, Range>

data class TargetContext(val activityKcal: Double, val adaptiveKcal: Double)

/** Energy covering the macro minimums (0.8 g/kg protein, 0.5 g/kg fat, 130 g carbs). */
fun macroMinimumKcal(p: Profile): Double {
  val w = referenceWeight(p)
  return jsRound(0.8 * w * 4 + 0.5 * w * 9 + 130 * 4)
}

/** Targets sit at least this factor above their minimum (a minimum is not a goal). */
private const val MACRO_MARGIN = 1.15

/** Absolute safety floor for any calorie recommendation without medical supervision. */
const val CALORIE_SAFETY_FLOOR = 1200.0

/**
 * Daily calorie range. The target (maintenance + the chosen weekly change) sits in the middle.
 * Losing: the minimum still covers the macro minimums, the maximum mirrors it above the target.
 * Gaining / maintaining: the band spans maintenance → target and mirrors it (at least ±10 %).
 */
fun dailyCalorieTarget(p: Profile, ctx: TargetContext): Range {
  val tdee = baseExpenditure(p) + ctx.activityKcal
  val floor = max(CALORIE_SAFETY_FLOOR, macroMinimumKcal(p))
  val target = jsRound(max(floor, tdee + goalDelta(p) + ctx.adaptiveKcal))
  val losing = target < tdee - 50
  val half = maxOf(150.0, jsRound(target * 0.1), if (losing) target - floor else abs(target - tdee))
  return Range(max(floor, target - half), target, target + half)
}

/**
 * Macro and micronutrient ranges. Sources: WHO sugar & sodium guidelines (2012/2015), US DRI
 * (IOM 2005), EFSA dietary reference values, AHA saturated fat advice.
 */
fun computeTargets(p: Profile, ctx: TargetContext, overrides: Map<String, RangeOverride> = emptyMap()): Targets {
  val cal = dailyCalorieTarget(p, ctx)
  val kcal = cal.target
  val w = referenceWeight(p)
  val losing = goalDelta(p) < 0

  // Protein: 0.8 g/kg is the RDA floor; higher intake preserves muscle in a deficit.
  val protein = Range(jsRound(0.8 * w), jsRound((if (losing) 1.6 else 1.4) * w), jsRound(2.5 * w))
  // Fat: 20–35 % of energy (IOM AMDR), never below ~0.5 g/kg for hormones.
  val fatMin = max(0.5 * w, kcal * 0.2 / 9)
  val fatTarget = max(kcal * 0.28 / 9, fatMin * MACRO_MARGIN)
  val fat = Range(jsRound(fatMin), jsRound(fatTarget), jsRound(max(kcal * 0.35 / 9, fatTarget * MACRO_MARGIN)))
  // Carbs: RDA 130 g (brain glucose) is the minimum; the target is the rest of the energy; cap 65 %.
  val carbTarget = max(130 * MACRO_MARGIN, (kcal - protein.target * 4 - fatTarget * 9) / 4)
  val carbs = Range(130.0, jsRound(carbTarget), jsRound(max(carbTarget * MACRO_MARGIN, kcal * 0.65 / 4)))

  val ranges = linkedMapOf(
    Nutrient.Kcal to cal,
    Nutrient.Protein to protein,
    Nutrient.Fat to fat,
    Nutrient.Carbs to carbs,
    // Total sugars (incl. fruit/milk): soft limit ~20 % of energy.
    Nutrient.Sugar to Range(0.0, jsRound(kcal * 0.1 / 4), jsRound(kcal * 0.2 / 4), true),
    // WHO: free sugars < 10 % of energy, ideally < 5 %.
    Nutrient.AddedSugar to Range(0.0, jsRound(kcal * 0.05 / 4), jsRound(kcal * 0.1 / 4), true),
    // WHO: < 2000 mg sodium (5 g salt). ~500 mg is the physiological minimum.
    Nutrient.Sodium to Range(500.0, 1500.0, 2000.0),
    // 14 g per 1000 kcal (IOM); > 70 g often causes GI discomfort.
    Nutrient.Fiber to Range(20.0, jsRound(max(25.0, kcal / 1000 * 14)), 70.0),
    // < 10 % of energy (WHO/AHA).
    Nutrient.SatFat to Range(0.0, jsRound(kcal * 0.07 / 9), jsRound(kcal * 0.1 / 9), true),
    // As low as possible; WHO: < 1 % of energy.
    Nutrient.TransFat to Range(0.0, 0.0, jsRound(kcal * 0.01 / 9 * 10) / 10, true),
    // Classic 300 mg/day guidance.
    Nutrient.Cholesterol to Range(0.0, 200.0, 300.0, true),
    // WHO: ≥ 3510 mg; EFSA AI 3500 mg. No UL for food sources in healthy kidneys.
    Nutrient.Potassium to Range(2600.0, 3500.0, 6000.0),
  )
  for ((k, o) in overrides) {
    val n = Nutrient.of(k) ?: continue
    val r = ranges[n]!!
    ranges[n] = r.copy(min = o.min ?: r.min, max = o.max ?: r.max)
  }
  return ranges
}

/** low | ok | good | high */
fun rangeStatus(value: Double, r: Range): String = when {
  value > r.max -> "high"
  r.lowerIsBetter -> if (value <= r.target) "good" else "ok"
  value < r.min -> "low"
  abs(value - r.target) <= r.target * 0.1 -> "good"
  else -> "ok"
}

// ---- per-day aggregates ----------------------------------------------------------------

fun activityKcalFor(data: AppData, date: String): Double =
  data.workouts.filter { it.date == date }.sumOf { it.kcal } + stepsKcal(data.steps[date] ?: 0.0, data.profile.weightKg)

fun intakeKcalFor(data: AppData, date: String): Double = jsRound(sumEntries(data.foodLog.filter { it.date == date }).kcal)

fun expenditureFor(data: AppData, date: String): Double = baseExpenditure(data.profile) + activityKcalFor(data, date)

/**
 * Adaptive correction: if the user over/under-ate vs target in the previous 7 days, spread half of
 * that imbalance over the next 7 days, capped at ±15 % of the base target. Days without food
 * entries are ignored (most likely just not logged).
 */
fun adaptiveCorrection(data: AppData, date: String): Double {
  if (!data.settings.adaptiveTargets) return 0.0
  val p = data.profile
  var imbalance = 0.0
  for (d in lastDays(7, addDays(date, -1))) {
    val entries = data.foodLog.filter { it.date == d }
    if (entries.isEmpty()) continue
    val t = dailyCalorieTarget(p, TargetContext(activityKcalFor(data, d), 0.0)).target
    imbalance += sumEntries(entries).kcal - t
  }
  val cap = dailyCalorieTarget(p, TargetContext(0.0, 0.0)).target * 0.15
  return jsRound(max(-cap, min(cap, -imbalance / 2 / 7)))
}

fun targetsFor(data: AppData, date: String): Targets = computeTargets(
  data.profile,
  TargetContext(activityKcalFor(data, date), adaptiveCorrection(data, date)),
  data.settings.nutrientOverrides,
)

// ---- water -----------------------------------------------------------------------------

data class WaterRange(val min: Double, val target: Double, val max: Double, val danger: Double)

private fun round50(v: Double) = jsRound(v / 50) * 50

/**
 * Drinking-water guidance: ~30–35 ml/kg (EFSA AI 2.0 L women / 2.5 L men total water, ~80 % from
 * drinks) plus ~500 ml per hour of exercise. Danger: kidneys excrete ~0.8–1 L/hour; very large
 * daily volumes risk hyponatremia.
 */
fun waterRange(p: Profile, workoutMinutes: Double, dangerMl: Double): WaterRange {
  val extra = workoutMinutes / 60 * 500
  val w = referenceWeight(p)
  val target = round50(w * 33 + extra)
  return WaterRange(round50(w * 25 + extra), target, round50(w * 45 + extra), max(dangerMl, target + 1500))
}

// ---- body composition ------------------------------------------------------------------

fun bmi(weightKg: Double, heightCm: Double): Double {
  val h = heightCm / 100
  return weightKg / (h * h)
}

/** key: severeUnder | under | normal | over | obese; tone: ok | low | high | danger */
data class Category(val key: String, val tone: String)

fun bmiCategory(v: Double) = when {
  v < 16 -> Category("severeUnder", "danger")
  v < 18.5 -> Category("under", "low")
  v < 25 -> Category("normal", "ok")
  v < 30 -> Category("over", "high")
  else -> Category("obese", "danger")
}

data class HealthyWeight(val min: Double, val max: Double, val devine: Double)

/** Healthy weight band from BMI 18.5–24.9 and Devine (1974) ideal body weight. */
fun healthyWeight(heightCm: Double, sex: Sex): HealthyWeight {
  val h = heightCm / 100
  val inchesOver5ft = max(0.0, heightCm / 2.54 - 60)
  val devine = (if (sex == Sex.Male) 50.0 else 45.5) + 2.3 * inchesOver5ft
  return HealthyWeight(18.5 * h * h, 24.9 * h * h, devine)
}

/**
 * Per-100 g tags. "High" salt/fat/sugar follow the UK FSA front-of-pack criteria (salt > 1.5 g =
 * sodium > 600 mg, fat > 17.5 g, total sugars > 22.5 g); "high fibre" (≥ 6 g) and "low energy"
 * (≤ 40 kcal) follow EU Regulation 1924/2006. Protein: ≥ 20 % of energy (EU "high protein") and
 * at least 10 g. Added sugar ≥ 5 g and cholesterol ≥ 100 mg are app-level cut-offs.
 */
fun foodTags(n: Per100?): List<String> {
  if (n == null) return emptyList()
  val kcal = n.kcal ?: 0.0
  val tags = mutableListOf<String>()
  if ((n.protein ?: 0.0) >= 10 && kcal > 0 && (n.protein ?: 0.0) * 4 / kcal >= 0.2) tags += "protein"
  if ((n.sodium ?: 0.0) > 600) tags += "salty"
  if ((n.addedSugar ?: 0.0) >= 5) tags += "addedSugar" else if ((n.sugar ?: 0.0) > 22.5) tags += "sweet"
  if ((n.cholesterol ?: 0.0) >= 100) tags += "cholesterol"
  if ((n.fat ?: 0.0) > 17.5) tags += "fat"
  if ((n.fiber ?: 0.0) >= 6) tags += "fiber"
  if (n.kcal != null && kcal <= 40) tags += "lowKcal"
  return tags
}

/** US Navy circumference method body-fat estimate (Hodgdon & Beckett, 1984). */
fun navyBodyFat(sex: Sex, heightCm: Double, waistCm: Double, neckCm: Double, hipCm: Double?): Double? {
  if (sex == Sex.Male) {
    if (waistCm <= neckCm) return null
    return 495 / (1.0324 - 0.19077 * log10(waistCm - neckCm) + 0.15456 * log10(heightCm)) - 450
  }
  if (hipCm == null || hipCm == 0.0 || waistCm + hipCm <= neckCm) return null
  return 495 / (1.29579 - 0.35004 * log10(waistCm + hipCm - neckCm) + 0.221 * log10(heightCm)) - 450
}

/**
 * Rough body-fat estimate from BMI, age and sex (Deurenberg, Weststrate & Seidell, 1991, Br J Nutr
 * 65:105–114): BF% = 1.2·BMI + 0.23·age − 10.8·sex − 5.4. Error about ±4 %.
 */
fun bmiBodyFat(sex: Sex, bmiValue: Double, age: Int): Double =
  max(3.0, 1.2 * bmiValue + 0.23 * age - 10.8 * (if (sex == Sex.Male) 1 else 0) - 5.4)

/** Healthy body-fat bands for adults (ACE): essential | athletic | fit | average | high. */
fun bodyFatCategory(sex: Sex, pct: Double): String {
  val b = if (sex == Sex.Male) listOf(6, 14, 18, 25) else listOf(14, 21, 25, 32)
  return when {
    pct < b[0] -> "essential"
    pct < b[1] -> "athletic"
    pct < b[2] -> "fit"
    pct < b[3] -> "average"
    else -> "high"
  }
}

// ---- heart -----------------------------------------------------------------------------

/** Blood pressure classification per ACC/AHA 2017, plus hypotension below 90/60. */
fun bpCategory(sys: Double, dia: Double) = when {
  sys > 180 || dia > 120 -> Category("crisis", "danger")
  sys >= 140 || dia >= 90 -> Category("stage2", "danger")
  sys >= 130 || dia >= 80 -> Category("stage1", "high")
  sys >= 120 -> Category("elevated", "high")
  sys < 90 || dia < 60 -> Category("low", "low")
  else -> Category("normal", "ok")
}

fun pulseCategory(bpm: Double) = when {
  bpm < 40 -> Category("veryLow", "danger")
  bpm < 60 -> Category("low", "low")
  bpm <= 100 -> Category("normal", "ok")
  bpm <= 120 -> Category("elevated", "high")
  else -> Category("tachy", "danger")
}

/** Tanaka (2001) estimate of maximum heart rate. */
fun maxHeartRate(p: Profile): Double = jsRound(208 - 0.7 * age(p))

/**
 * Time-weighted exponential moving average of weight ("trend weight", The Hacker's Diet,
 * J. Walker): α = 1 − 0.5^(Δdays / halfLife), so irregular weigh-ins are weighted by the time
 * between them and daily water swings are smoothed out.
 */
fun weightTrend(points: List<Pair<Long, Double>>, halfLifeDays: Double = 7.0): List<Double> {
  val out = ArrayList<Double>(points.size)
  points.forEachIndexed { i, (time, kg) ->
    if (i == 0) out += kg
    else {
      val days = max(0.0, (time - points[i - 1].first) / 86_400_000.0)
      // A same-day re-weigh still counts a little (as if ~2 h passed).
      val a = 1 - 0.5.pow(max(days, 1.0 / 12) / halfLifeDays)
      out += out[i - 1] + a * (kg - out[i - 1])
    }
  }
  return out
}

// ---- advice (rule-based tips from the day totals) -------------------------------------

data class Advice(val tone: String, val key: String)

/** [dayProgress]: fraction of the waking day elapsed; "too little" warnings only show late. */
fun nutritionAdvice(n: Nutrients, t: Targets, dayProgress: Double): List<Advice> {
  val out = mutableListOf<Advice>()
  val late = dayProgress > 0.75
  fun over(k: Nutrient) = n[k] > t[k]!!.max
  fun under(k: Nutrient) = late && n[k] < t[k]!!.min
  if (over(Nutrient.Kcal)) out += Advice("high", "kcalOver")
  if (under(Nutrient.Kcal)) out += Advice("low", "kcalUnder")
  if (under(Nutrient.Protein)) out += Advice("low", "proteinUnder")
  if (over(Nutrient.Protein)) out += Advice("high", "proteinOver")
  if (over(Nutrient.AddedSugar)) out += Advice("danger", "addedSugarOver")
  if (over(Nutrient.Sodium)) out += Advice("danger", "sodiumOver")
  if (over(Nutrient.SatFat)) out += Advice("high", "satFatOver")
  if (n[Nutrient.TransFat] > t[Nutrient.TransFat]!!.max) out += Advice("danger", "transFatOver")
  if (under(Nutrient.Fiber)) out += Advice("low", "fiberUnder")
  if (under(Nutrient.Potassium)) out += Advice("low", "potassiumUnder")
  if (over(Nutrient.Cholesterol)) out += Advice("high", "cholesterolOver")
  if (under(Nutrient.Fat)) out += Advice("low", "fatUnder")
  if (out.isEmpty() && n.kcal > 0) out += Advice("ok", if (late) "allGoodLate" else "allGoodSoFar")
  return out
}

/** 0 at 7:00, 1 at 23:00. */
fun wakingDayProgress(): Double {
  val now = Calendar.getInstance()
  val h = now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60.0
  return ((h - 7) / 16).coerceIn(0.0, 1.0)
}

// ---- donation milestones --------------------------------------------------------------

/** The prompt appears when distinct usage days reach 3, 7, 14, 30, then every 30 days. */
fun isMilestone(days: Int) = days in listOf(3, 7, 14) || (days >= 30 && days % 30 == 0)

/** The milestone to show now, or null. Only the latest reached milestone counts. */
fun pendingMilestone(usageDays: Int, shown: List<Int>): Int? {
  for (d in usageDays downTo 3) if (isMilestone(d)) return if (d in shown) null else d
  return null
}

/**
 * Weight lost while actively using the app (food logged on ≥ 70 % of days since the first entry,
 * 7+ days), or null. Uses the weigh-in trend when available, else the logged deficit (7700 kcal/kg).
 */
fun weightLost(s: AppData): Double? {
  val days = s.foodLog.map { it.date }.distinct().sorted()
  if (days.isEmpty()) return null
  val span = daysBetween(days[0], todayKey()) + 1
  if (span < 7 || days.size.toDouble() / span < 0.7) return null
  var balance = 0.0
  for (d in days) balance += sumEntries(s.foodLog.filter { it.date == d }).kcal - expenditureFor(s, d)
  if (balance >= 0) return null
  val since = startOfDay(days[0])
  val weights = s.body.filter { it.weightKg != null && it.time >= since - 86_400_000 }.sortedBy { it.time }.map { it.time to it.weightKg!! }
  val trend = weightTrend(weights)
  val kg = if (trend.size >= 2) weights[0].second - trend.last() else -balance / KCAL_PER_KG_FAT
  return if (kg >= 0.5) kg else null
}

/** Rounds half up (like JavaScript Math.round) before showing integers. */
fun Double.roundInt(): Long = jsRound(this).roundToLong()
