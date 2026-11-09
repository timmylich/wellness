/** Port of src/lib/__tests__/calc.test.ts: the native formulas must give the same numbers. */
package io.github.timmylich.wellness

import io.github.timmylich.wellness.data.AppData
import io.github.timmylich.wellness.data.Food
import io.github.timmylich.wellness.data.FoodEntry
import io.github.timmylich.wellness.data.LanguageSetting
import io.github.timmylich.wellness.data.Nutrient
import io.github.timmylich.wellness.data.Per100
import io.github.timmylich.wellness.data.Profile
import io.github.timmylich.wellness.data.RangeOverride
import io.github.timmylich.wellness.data.Sex
import io.github.timmylich.wellness.data.ThemeMode
import io.github.timmylich.wellness.data.json
import io.github.timmylich.wellness.data.normalized
import io.github.timmylich.wellness.lib.*
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CalcTest {
  private val year = Calendar.getInstance().get(Calendar.YEAR)
  private val man = Profile(name = "", sex = Sex.Male, birthYear = year - 30, heightCm = 180.0, weightKg = 80.0, targetWeightKg = 75.0, weeklyChangeKg = -0.5, lifestyleFactor = 1.2)
  private val zero = TargetContext(0.0, 0.0)

  @Test fun bmrMifflin() {
    assertEquals(1780.0, bmr(man), 0.0)
    assertEquals(1614.0, bmr(man.copy(sex = Sex.Female)), 0.0)
    assertEquals(1500.0, bmr(man.copy(bmrOverride = 1500.0)), 0.0)
  }

  @Test fun goalDeltaSigned() {
    assertEquals(-550.0, goalDelta(man), 0.0)
    assertEquals(550.0, goalDelta(man.copy(weeklyChangeKg = 0.5)), 0.0)
    assertEquals(0.0, goalDelta(man.copy(weeklyChangeKg = 0.0)), 0.0)
  }

  @Test fun paceWarnings() {
    assertNull(paceWarning(man))
    assertEquals("lossFast", paceWarning(man.copy(weeklyChangeKg = -0.9)))
    assertEquals("lossVeryFast", paceWarning(man.copy(weeklyChangeKg = -1.5)))
    assertEquals("gainFast", paceWarning(man.copy(weeklyChangeKg = 0.75)))
    assertEquals(10, weeksToTarget(man))
    assertNull(weeksToTarget(man.copy(weeklyChangeKg = 0.5)))
  }

  @Test fun referenceWeightForHighBodyWeight() {
    assertEquals(80.0, referenceWeight(man), 0.0)
    val heavy = man.copy(weightKg = 130.0, targetWeightKg = 90.0)
    assertEquals(100.0, referenceWeight(heavy), 1e-5)
    assertEquals(160.0, computeTargets(heavy, zero)[Nutrient.Protein]!!.target, 0.0)
    assertEquals(3300.0, waterRange(heavy, 0.0, 6000.0).target, 0.0)
  }

  @Test fun safetyFloor() {
    val r = dailyCalorieTarget(man.copy(weeklyChangeKg = -1.5), zero)
    assertTrue(r.target >= r.min)
    assertTrue(r.min >= 1200)
  }

  @Test fun targetInTheMiddle() {
    val heavy = man.copy(weightKg = 130.0, heightCm = 180.0, targetWeightKg = 90.0, weeklyChangeKg = -1.0, lifestyleFactor = 1.3)
    val r = dailyCalorieTarget(heavy, zero)
    assertEquals(r.max - r.target, r.target - r.min, 0.0)
    assertTrue(r.min < r.target)
    assertTrue(r.min >= macroMinimumKcal(heavy))
  }

  @Test fun losingSpansMacroMinimum() {
    val heavy = man.copy(weightKg = 130.0, heightCm = 187.0, targetWeightKg = 90.0, weeklyChangeKg = -0.6, lifestyleFactor = 1.2)
    val r = dailyCalorieTarget(heavy, zero)
    assertEquals(maxOf(1200.0, macroMinimumKcal(heavy)), r.min, 0.0)
    assertEquals(r.target - r.min, r.max - r.target, 0.0)
  }

  @Test fun activityAddsToTarget() {
    val p = man.copy(lifestyleFactor = 1.45)
    val base = dailyCalorieTarget(p, zero).target
    val active = dailyCalorieTarget(p, TargetContext(300.0, 0.0)).target
    assertEquals(300.0, active - base, 0.0)
  }

  @Test fun weightTrendHalfLife() {
    val day = 86_400_000L
    val t = weightTrend(listOf(0L to 100.0, 7 * day to 98.0))
    assertEquals(100.0, t[0], 0.0)
    assertEquals(99.0, t[1], 1e-5)
  }

  @Test fun stepsAndMet() {
    assertEquals(0.0, stepsKcal(2000.0, 80.0), 0.0)
    assertEquals(40.0, stepsKcal(4300.0, 80.0), 0.0)
    assertEquals(0.0, metKcal(1.0, 80.0, 60.0), 0.0)
    assertEquals(280.0, metKcal(8.0, 80.0, 30.0), 0.0)
  }

  @Test fun rangesOrderedAndWho() {
    val t = computeTargets(man, zero)
    for (r in t.values) {
      assertTrue(r.min <= r.target)
      assertTrue(r.target <= r.max)
    }
    assertEquals(2000.0, t[Nutrient.Sodium]!!.max, 0.0)
    assertEquals(t[Nutrient.Kcal]!!.target * 0.1 / 4, t[Nutrient.AddedSugar]!!.max, 0.5)
    assertEquals(130.0, t[Nutrient.Carbs]!!.min, 0.0)
    val heavy = computeTargets(man.copy(weightKg = 130.0, heightCm = 187.0, targetWeightKg = 90.0, weeklyChangeKg = -1.0), zero)
    assertTrue(heavy[Nutrient.Fat]!!.target > heavy[Nutrient.Fat]!!.min)
    assertTrue(heavy[Nutrient.Carbs]!!.target > heavy[Nutrient.Carbs]!!.min)
  }

  @Test fun overrides() {
    val t = computeTargets(man, zero)
    val o = computeTargets(man, zero, mapOf("sodium" to RangeOverride(max = 1500.0)))
    assertEquals(1500.0, o[Nutrient.Sodium]!!.max, 0.0)
    assertEquals(t[Nutrient.Sodium]!!.min, o[Nutrient.Sodium]!!.min, 0.0)
  }

  @Test fun rangeStatuses() {
    val r = Range(10.0, 20.0, 30.0)
    assertEquals("low", rangeStatus(5.0, r))
    assertEquals("good", rangeStatus(20.0, r))
    assertEquals("high", rangeStatus(31.0, r))
    assertEquals("good", rangeStatus(0.0, r.copy(min = 0.0, lowerIsBetter = true)))
  }

  @Test fun nutrientSums() {
    val e = FoodEntry("e", "2026-01-01", 0, food = Food("x", "x", per100g = Per100(kcal = 100.0, protein = 10.0)), grams = 150.0)
    val n = sumEntries(listOf(e, e))
    assertEquals(300.0, n.kcal, 1e-9)
    assertEquals(30.0, n[Nutrient.Protein], 1e-9)
    assertEquals(0.0, n[Nutrient.Fat], 0.0)
  }

  @Test fun openFoodFacts() {
    val p = json.parseToJsonElement("""{"code":"1","product_name":"Test","brands":["Додо"],"nutriments":{"energy-kcal_100g":250,"salt_100g":1}}""").jsonObject
    val f = fromOff(p)!!
    assertEquals(250.0, f.per100g.kcal!!, 0.0)
    assertEquals(400.0, f.per100g.sodium!!, 0.0)
    assertEquals("Додо", f.brand)
    assertNull(fromOff(json.parseToJsonElement("""{"product_name":""}""").jsonObject))
  }

  @Test fun forgivingSearch() {
    fun m(name: String, q: String) = matchScore(Food(name, name, source = "builtin"), queryWords(q)) > 0
    assertTrue(m("Спагетти варёные", "варёные спагетти"))
    assertTrue(m("Спагетти варёные", "вареных спагети"))
    assertFalse(m("Спагетти сухие", "варёные спагетти"))
    assertFalse(m("Макароны варёные", "варёные спагетти"))
  }

  @Test fun tags() {
    assertEquals(listOf("protein"), foodTags(Per100(kcal = 120.0, protein = 25.0, fat = 2.0)))
    assertEquals(listOf("addedSugar", "fat"), foodTags(Per100(kcal = 500.0, sugar = 50.0, addedSugar = 40.0, fat = 30.0)))
    assertEquals(listOf("lowKcal"), foodTags(Per100(kcal = 30.0, sugar = 3.0)))
    assertEquals(listOf("salty"), foodTags(Per100(kcal = 250.0, sodium = 1200.0)))
  }

  @Test fun bodyAndHeart() {
    assertEquals(24.69, bmi(80.0, 180.0), 0.01)
    val bf = navyBodyFat(Sex.Male, 180.0, 85.0, 38.0, null)!!
    assertTrue(bf > 10 && bf < 25)
    assertNull(navyBodyFat(Sex.Male, 180.0, 30.0, 38.0, null))
    assertEquals(23.0, bmiBodyFat(Sex.Male, 25.0, 40), 1e-5)
    assertEquals("ok", bpCategory(115.0, 75.0).tone)
    assertEquals("elevated", bpCategory(125.0, 75.0).key)
    assertEquals("stage1", bpCategory(135.0, 85.0).key)
    assertEquals("stage2", bpCategory(150.0, 95.0).key)
    assertEquals("crisis", bpCategory(190.0, 100.0).key)
    assertEquals("low", bpCategory(85.0, 55.0).key)
    val w = waterRange(man, 60.0, 3000.0)
    assertTrue(w.min < w.target && w.target < w.max && w.danger > w.target)
  }

  @Test fun donationMilestones() {
    assertTrue(listOf(3, 7, 14, 30, 60, 90, 120, 150).all(::isMilestone))
    assertFalse(isMilestone(4))
    assertFalse(isMilestone(45))
    assertNull(pendingMilestone(2, emptyList()))
    assertEquals(7, pendingMilestone(8, emptyList()))
    assertNull(pendingMilestone(8, listOf(7)))
  }

  @Test fun migrationOfOldData() {
    val old = json.decodeFromString<AppData>("""{"profile":{"sex":"male","birthYear":1990,"heightCm":180,"weightKg":80,"targetWeightKg":70,"paceKgPerWeek":0.75,"lifestyleFactor":1.2},"settings":{"theme":"dark","stepsSource":"sensor"}}""")
      .normalized(emptyList())
    assertEquals(-0.75, old.profile.weeklyChangeKg, 0.0)
    assertNull(old.profile.paceKgPerWeek)
    assertEquals(ThemeMode.Dark, old.settings.theme)
    assertEquals(LanguageSetting.System, old.settings.language)
    assertEquals("device", old.settings.stepsSource)
    // Sections missing from old data come back with their defaults.
    assertEquals(false, old.settings.sections["pantry"])
    assertEquals(true, old.settings.sections["health"])
  }

  @Test fun localTimestamp() {
    val t = localTime(atTime("2026-03-05", 7, 45))
    assertEquals(listOf(2026, 3, 5, 7, 45), listOf(t.year, t.monthValue, t.dayOfMonth, t.hour, t.minute))
  }
}
