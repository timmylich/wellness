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

  fun markBackup() = updateMeta { it.copy(backupAt = System.currentTimeMillis()) }

  fun postponeBackup() = updateMeta { it.copy(backupRemindedAt = System.currentTimeMillis()) }

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

  /** Whole lists at once (shared pantry sync). */
  fun setShared(pantry: List<PantryItem>, shopping: List<ShoppingItem>, log: List<PantryOutcome>) =
    set { it.copy(pantry = pantry, shopping = shopping, pantryLog = log.sortedBy { o -> o.at }.takeLast(1000)) }

  /**
   * Remove a used-up ([kind] eaten) or thrown-away (wasted) product and count it in the statistics
   * under [outcomeId] (so it can be undone).
   */
  fun finishPantryItem(p: PantryItem, kind: String, outcomeId: String = uid(), by: String? = null) = set { s ->
    val o = PantryOutcome(outcomeId, todayKey(), p.name, kind, if (kind == "wasted") p.left else 1.0, System.currentTimeMillis(), by)
    s.copy(pantry = s.pantry.filterNot { it.id == p.id }, pantryLog = (s.pantryLog + o).takeLast(1000))
  }

  /** Undo [finishPantryItem]: the product comes back, the history entry goes. */
  fun undoFinish(p: PantryItem, outcomeId: String) = set { s ->
    s.copy(pantry = s.pantry.filterNot { it.id == p.id } + p, pantryLog = s.pantryLog.filterNot { it.id == outcomeId })
  }

  fun updateOutcome(o: PantryOutcome) = set { s -> s.copy(pantryLog = s.pantryLog.map { if (it.id == o.id) o else it }) }

  fun removeOutcome(id: String) = set { s -> s.copy(pantryLog = s.pantryLog.filterNot { it.id == id }) }

  fun restoreOutcome(o: PantryOutcome) = set { s -> if (s.pantryLog.any { it.id == o.id }) s else s.copy(pantryLog = s.pantryLog + o) }

  /** Adds to the shopping list unless the same name is already waiting to be bought. */
  fun addShopping(name: String) = set { s ->
    val n = name.trim()
    if (n.isEmpty() || s.shopping.any { it.boughtAt == null && it.name.equals(n, true) }) s
    else s.copy(shopping = s.shopping + ShoppingItem(uid(), n, System.currentTimeMillis()))
  }

  /** Tick or untick as bought. */
  fun toggleShopping(id: String) = set { s ->
    s.copy(shopping = s.shopping.map { if (it.id == id) it.copy(boughtAt = if (it.boughtAt == null) System.currentTimeMillis() else null, hidden = false) else it })
  }

  /** Hide ticked items from the list; they are still offered for the pantry. */
  fun clearBought() = set { s -> s.copy(shopping = s.shopping.map { if (it.boughtAt != null) it.copy(hidden = true) else it }) }

  fun removeShopping(id: String) = set { s -> s.copy(shopping = s.shopping.filterNot { it.id == id }) }

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
      "pantry" -> s.copy(pantry = emptyList(), shopping = emptyList(), pantryLog = emptyList())
      "health" -> s.copy(health = emptyList())
      else -> s
    }
  }
}
