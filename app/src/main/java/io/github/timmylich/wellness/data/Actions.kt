/** Typed mutations. Screens call these instead of Store.update. */
package io.github.timmylich.wellness.data

import io.github.timmylich.wellness.lib.todayKey
import kotlin.math.max
import kotlin.math.roundToLong

object Actions {
  private inline fun set(crossinline fn: (AppData) -> AppData) = Store.update { fn(it) }

  // ---- profile & settings ----------------------------------------------------------------

  fun updateProfile(fn: (Profile) -> Profile) = set { it.copy(profile = fn(it.profile)) }

  fun updateSettings(fn: (Settings) -> Settings) = set { it.copy(settings = fn(it.settings)) }

  fun setSection(id: String, on: Boolean) = updateSettings { it.copy(sections = it.sections + (id to on)) }

  fun updateMeta(fn: (Meta) -> Meta) = set { it.copy(meta = fn(it.meta)) }

  /** Re-run the first-launch wizard (data is kept). */
  fun restartOnboarding() = updateMeta { it.copy(onboarded = false) }

  fun completeOnboarding() = updateMeta { it.copy(onboarded = true) }

  fun markDonationShown(milestone: Int) = updateMeta { it.copy(donationShown = it.donationShown + milestone) }

  fun resetDonationPrompts() = updateMeta { it.copy(donationShown = emptyList()) }

  // ---- nutrition ------------------------------------------------------------------------

  fun logFood(food: Food, grams: Double, meal: String, date: String = todayKey(), pieces: Int? = null) {
    val e = FoodEntry(uid(), date, System.currentTimeMillis(), meal, food, grams, pieces?.takeIf { it > 1 })
    set { it.copy(foodLog = it.foodLog + e) }
  }

  fun updateFoodEntry(id: String, fn: (FoodEntry) -> FoodEntry) =
    set { s -> s.copy(foodLog = s.foodLog.map { if (it.id == id) fn(it) else it }) }

  fun removeFoodEntry(id: String) = set { s -> s.copy(foodLog = s.foodLog.filterNot { it.id == id }) }

  /** Insert or replace a food in the user's local database. */
  fun saveFood(food: Food) = set { s ->
    s.copy(foods = if (s.foods.any { it.id == food.id }) s.foods.map { if (it.id == food.id) food else it } else s.foods + food)
  }

  fun deleteFood(id: String) = set { s -> s.copy(foods = s.foods.filterNot { it.id == id }, favorites = s.favorites - id) }

  fun toggleFavorite(id: String) = set { s -> s.copy(favorites = if (id in s.favorites) s.favorites - id else s.favorites + id) }

  // ---- water ----------------------------------------------------------------------------

  fun addWater(ml: Double, date: String = todayKey(), kind: String = "water") =
    set { it.copy(water = it.water + WaterEntry(uid(), date, System.currentTimeMillis(), ml, kind)) }

  fun updateWater(e: WaterEntry) = set { s -> s.copy(water = s.water.map { if (it.id == e.id) e else it }) }

  fun removeWater(id: String) = set { s -> s.copy(water = s.water.filterNot { it.id == id }) }

  // ---- activity -------------------------------------------------------------------------

  fun addWorkout(date: String, name: String, minutes: Double, kcal: Double, note: String?) =
    set { it.copy(workouts = it.workouts + WorkoutEntry(uid(), date, System.currentTimeMillis(), name, minutes, kcal, note)) }

  fun updateWorkout(e: WorkoutEntry) = set { s -> s.copy(workouts = s.workouts.map { if (it.id == e.id) e else it }) }

  fun removeWorkout(id: String) = set { s -> s.copy(workouts = s.workouts.filterNot { it.id == id }) }

  fun saveWorkoutTemplate(t: WorkoutTemplate) = set { s ->
    s.copy(workoutTemplates = if (s.workoutTemplates.any { it.id == t.id }) s.workoutTemplates.map { if (it.id == t.id) t else it } else s.workoutTemplates + t)
  }

  fun deleteWorkoutTemplate(id: String) = set { s -> s.copy(workoutTemplates = s.workoutTemplates.filterNot { it.id == id }) }

  fun setSteps(date: String, steps: Double) = set { it.copy(steps = it.steps + (date to max(0.0, steps.roundToLong().toDouble()))) }

  /** Overwrite several days at once (Health Connect sync); skips the write if nothing changed. */
  fun setStepsBulk(byDay: Map<String, Double>) = set { s ->
    if (byDay.all { (d, v) -> s.steps[d] == v }) s else s.copy(steps = s.steps + byDay)
  }

  fun addSteps(byDay: Map<String, Double>) = set { s ->
    s.copy(steps = s.steps + byDay.mapValues { (d, v) -> (s.steps[d] ?: 0.0) + v })
  }

  // ---- heart & body ---------------------------------------------------------------------

  fun addHeart(systolic: Double?, diastolic: Double?, pulse: Double?, note: String?) =
    set { it.copy(heart = it.heart + HeartEntry(uid(), todayKey(), System.currentTimeMillis(), systolic, diastolic, pulse, note)) }

  fun updateHeart(e: HeartEntry) = set { s -> s.copy(heart = s.heart.map { if (it.id == e.id) e else it }) }

  fun removeHeart(id: String) = set { s -> s.copy(heart = s.heart.filterNot { it.id == id }) }

  /** Log body measurements; a new weight or height also updates the profile used in calculations. */
  fun addBody(e: BodyEntry) = set { s ->
    s.copy(
      body = s.body + e.copy(id = uid(), time = System.currentTimeMillis(), date = todayKey()),
      profile = s.profile.copy(weightKg = e.weightKg ?: s.profile.weightKg, heightCm = e.heightCm ?: s.profile.heightCm),
    )
  }

  /** Edit a body entry; the profile weight and height follow the latest entry that has them. */
  fun updateBody(e: BodyEntry) = set { s ->
    val body = s.body.map { if (it.id == e.id) e else it }
    val w = body.filter { it.weightKg != null }.maxByOrNull { it.time }?.weightKg
    val h = body.filter { it.heightCm != null }.maxByOrNull { it.time }?.heightCm
    s.copy(body = body, profile = s.profile.copy(weightKg = w ?: s.profile.weightKg, heightCm = h ?: s.profile.heightCm))
  }

  fun removeBody(id: String) = set { s -> s.copy(body = s.body.filterNot { it.id == id }) }

  // ---- pantry & health records ----------------------------------------------------------

  fun savePantryItem(p: PantryItem) = set { s ->
    s.copy(pantry = if (s.pantry.any { it.id == p.id }) s.pantry.map { if (it.id == p.id) p else it } else s.pantry + p)
  }

  fun setPantryLeft(id: String, left: Double) = set { s -> s.copy(pantry = s.pantry.map { if (it.id == id) it.copy(left = left) else it }) }

  fun removePantryItem(id: String) = set { s -> s.copy(pantry = s.pantry.filterNot { it.id == id }) }

  fun saveHealthRecord(r: HealthRecord) = set { s ->
    s.copy(health = if (s.health.any { it.id == r.id }) s.health.map { if (it.id == r.id) r else it } else s.health + r)
  }

  fun removeHealthRecord(id: String) = set { s -> s.copy(health = s.health.filterNot { it.id == id }) }

  // ---- data -----------------------------------------------------------------------------

  /** Keys match the `wipeLabels` in the dictionaries. */
  fun wipeSection(key: String) = set { s ->
    when (key) {
      "foodLog" -> s.copy(foodLog = emptyList())
      "water" -> s.copy(water = emptyList())
      "workouts" -> s.copy(workouts = emptyList())
      "steps" -> s.copy(steps = emptyMap())
      "heart" -> s.copy(heart = emptyList())
      "body" -> s.copy(body = emptyList())
      "pantry" -> s.copy(pantry = emptyList())
      "health" -> s.copy(health = emptyList())
      else -> s
    }
  }
}
