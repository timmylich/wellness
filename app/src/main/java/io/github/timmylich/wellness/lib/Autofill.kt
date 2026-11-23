/**
 * "Fill in the day" for a day the user most likely just did not log: the calorie target times the
 * preset's factor, minus what is logged, split into macros by the preset's energy shares.
 * Energy per gram: Atwater general factors, protein 4, fat 9, carbohydrate 4 kcal/g
 * (FAO, "Food energy – methods of analysis and conversion factors", 2003, ch. 3).
 */
package io.github.timmylich.wellness.lib

import io.github.timmylich.wellness.data.Food
import io.github.timmylich.wellness.data.Per100
import kotlin.math.roundToLong

/** Energy shares of protein / fat / carbs (sum 1) and of sugar (part of carbs). */
data class FillPreset(val key: String, val factor: Double, val protein: Double, val fat: Double, val carbs: Double, val sugar: Double)

val FILL_PRESETS = listOf(
  FillPreset("usual", 1.0, 0.20, 0.30, 0.50, 0.10),
  FillPreset("little", 0.7, 0.20, 0.30, 0.50, 0.10),
  FillPreset("much", 1.3, 0.18, 0.34, 0.48, 0.12),
  FillPreset("carbs", 1.0, 0.13, 0.25, 0.62, 0.15),
  FillPreset("sweet", 1.05, 0.10, 0.32, 0.58, 0.30),
  FillPreset("fatty", 1.1, 0.15, 0.45, 0.40, 0.10),
  FillPreset("protein", 1.0, 0.35, 0.30, 0.35, 0.07),
)

/** Under a quarter of the calorie target logged: probably just not written down. */
fun underLogged(loggedKcal: Double, targetKcal: Double) = loggedKcal < targetKcal * 0.25

/** Typical energy density of a mixed diet, kcal per gram; only gives the entry a plausible weight. */
private const val KCAL_PER_G = 1.5

/** The missing part of the day as one food and its grams, or null if nothing is missing. */
fun fillFood(p: FillPreset, targetKcal: Double, loggedKcal: Double, name: String): Pair<Food, Double>? {
  val kcal = targetKcal * p.factor - loggedKcal
  if (kcal < 50) return null
  val grams = (kcal / KCAL_PER_G).roundToLong().toDouble()
  val k = 100 / grams
  val per = Per100(
    kcal = kcal * k,
    protein = kcal * p.protein / 4 * k,
    fat = kcal * p.fat / 9 * k,
    carbs = kcal * p.carbs / 4 * k,
    sugar = kcal * p.sugar / 4 * k,
  )
  return Food("autofill-${p.key}", name, source = "estimate", per100g = per) to grams
}
