package io.github.timmylich.wellness

import io.github.timmylich.wellness.data.ActivityReminder
import io.github.timmylich.wellness.data.AppData
import io.github.timmylich.wellness.data.PantryOutcome
import io.github.timmylich.wellness.data.WorkoutEntry
import io.github.timmylich.wellness.lib.FILL_PRESETS
import io.github.timmylich.wellness.lib.entryNutrients
import io.github.timmylich.wellness.lib.fillFood
import io.github.timmylich.wellness.lib.nextDue
import io.github.timmylich.wellness.lib.reminderDone
import io.github.timmylich.wellness.lib.underLogged
import io.github.timmylich.wellness.screens.pantryStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class RemindersAutofillTest {
  private val every2 = ActivityReminder("r", everyDays = 2, hour = 18, minute = 30, start = "2026-03-01")

  @Test fun nextDueEveryNDays() {
    // Due on the start day and every second day after it.
    assertEquals(LocalDateTime.of(2026, 3, 1, 18, 30), nextDue(every2, LocalDateTime.of(2026, 2, 20, 9, 0)))
    assertEquals(LocalDateTime.of(2026, 3, 3, 18, 30), nextDue(every2, LocalDateTime.of(2026, 3, 1, 18, 30)))
    assertEquals(LocalDateTime.of(2026, 3, 3, 18, 30), nextDue(every2, LocalDateTime.of(2026, 3, 2, 23, 0)))
    assertEquals(LocalDateTime.of(2026, 3, 5, 18, 30), nextDue(every2, LocalDateTime.of(2026, 3, 3, 19, 0)))
    val daily = every2.copy(everyDays = 1)
    assertEquals(LocalDateTime.of(2026, 3, 10, 18, 30), nextDue(daily, LocalDateTime.of(2026, 3, 10, 8, 0)))
  }

  @Test fun doneSkipsTheReminder() {
    val steps = every2.copy(kind = "steps", steps = 5000.0)
    assertTrue(reminderDone(steps, AppData(), "2026-03-03", 5200.0))
    assertFalse(reminderDone(steps, AppData(), "2026-03-03", 4000.0))
    val task = every2.copy(kind = "task", text = "Warm-up")
    val d = AppData(workouts = listOf(WorkoutEntry("w", "2026-03-02", 0, "warm-up ")))
    assertTrue(reminderDone(task, d, "2026-03-03", 0.0))
    // Logged before the period (two days back) does not count.
    assertFalse(reminderDone(task, d, "2026-03-04", 0.0))
  }

  @Test fun autofillCoversTheMissingPart() {
    assertTrue(underLogged(400.0, 2000.0))
    assertFalse(underLogged(600.0, 2000.0))
    val usual = FILL_PRESETS.first { it.key == "usual" }
    val (food, grams) = fillFood(usual, 2000.0, 300.0, "x")!!
    val n = entryNutrients(food.per100g, grams)
    assertEquals(1700.0, n.kcal, 0.01)
    // Macro energy adds up to the calories (Atwater 4 / 9 / 4).
    val p = n[io.github.timmylich.wellness.data.Nutrient.Protein]
    val f = n[io.github.timmylich.wellness.data.Nutrient.Fat]
    val c = n[io.github.timmylich.wellness.data.Nutrient.Carbs]
    assertEquals(1700.0, p * 4 + f * 9 + c * 4, 0.01)
    assertNull(fillFood(FILL_PRESETS.first { it.key == "little" }, 2000.0, 1450.0, "x"))
  }

  @Test fun partialOutcomesCountOnce() {
    val log = listOf(
      PantryOutcome("1", "2026-01-30", "Milk", "wasted", 0.25, partial = true),
      PantryOutcome("2", "2026-01-30", "Milk", "eaten", 0.25, partial = true),
      PantryOutcome("3", "2026-01-30", "Milk", "eaten"),
      PantryOutcome("4", "2026-01-30", "Bread", "eaten"),
    )
    val s = pantryStats(log, "2026-01-30")
    assertEquals(2, s.eaten)
    assertEquals(0, s.wasted)
    assertEquals(13, s.lossPct) // 0.25 of one of two products
  }

  @Test fun splitFinishCountsOneProduct() {
    // What finishSplit writes: a partial wasted share plus one whole eaten product.
    val log = listOf(
      PantryOutcome("w", "2026-01-30", "Soup", "wasted", 0.3, partial = true),
      PantryOutcome("e", "2026-01-30", "Soup", "eaten"),
    )
    val s = pantryStats(log, "2026-01-30")
    assertEquals(1, s.eaten)
    assertEquals(30, s.lossPct)
  }
}
