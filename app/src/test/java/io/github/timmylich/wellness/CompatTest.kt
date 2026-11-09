/** Backups from the React Native app must import unchanged, and every UI string key must exist. */
package io.github.timmylich.wellness

import io.github.timmylich.wellness.data.AppData
import io.github.timmylich.wellness.data.Sex
import io.github.timmylich.wellness.data.json
import io.github.timmylich.wellness.data.normalized
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CompatTest {
  /** A backup as `exportData()` in src/lib/backup.ts writes it (JSON.stringify drops undefined). */
  private val rnBackup = """
    {"schemaVersion":1,
     "profile":{"name":"Вика","sex":"female","birthYear":1994,"heightCm":168,"weightKg":61.5,"targetWeightKg":58,"weeklyChangeKg":-0.25,"lifestyleFactor":1.45},
     "settings":{"language":"ru","theme":"dark","animations":true,"stepsSource":"healthConnect","accent":"#7A5AF8","amoled":true,
       "sections":{"nutrition":true,"water":true,"activity":true,"heart":false,"body":true,"pantry":true,"health":true,"tips":true},
       "adaptiveTargets":true,"onlineFoodSearch":false,"waterPresets":[200,300],"waterAmountMl":300,"waterDangerMl":6000,"stepGoal":10000,
       "showHints":false,"donationPrompts":true,"stepsInfoDismissed":true,"usdaApiKey":"",
       "notifications":{"enabled":true,"expiry":true,"updates":false},"nutrientOverrides":{"sodium":{"max":1500}}},
     "meta":{"onboarded":true,"usageDays":["2026-10-01","2026-10-02"],"donationShown":[3],"stepCounter":{"value":1234,"bootAt":1,"at":2,"sensor":"wakeup"},"createdAt":1759000000000},
     "foods":[{"id":"custom-a","name":"Сырники","source":"custom","per100g":{"kcal":220,"protein":15.5},"servingG":120}],
     "favorites":["custom-a"],
     "foodLog":[{"id":"e1","date":"2026-10-02","time":1759390000000,"meal":"breakfast","food":{"id":"builtin-buckwheat","name":"Гречка варёная","aliases":["Buckwheat, boiled"],"source":"builtin","servingG":200,"per100g":{"kcal":110,"protein":4.2,"fat":1.1,"carbs":21.3,"sugar":0.9,"addedSugar":0,"sodium":4,"fiber":2.7,"satFat":0.2,"transFat":0,"cholesterol":0,"potassium":88}},"grams":250,"pieces":2}],
     "water":[{"id":"w1","date":"2026-10-02","time":1759391000000,"ml":250},{"id":"w2","date":"2026-10-02","time":1759392000000,"ml":330,"kind":"other"}],
     "workouts":[{"id":"k1","date":"2026-10-02","time":1759393000000,"name":"Бег (8 км/ч)","minutes":30,"kcal":256,"note":"парк"}],
     "workoutTemplates":[{"id":"builtin-run","name":"Бег (8 км/ч)","met":8.3,"defaultMinutes":30,"builtin":true}],
     "steps":{"2026-10-01":8123,"2026-10-02":4500},
     "heart":[{"id":"h1","date":"2026-10-02","time":1759394000000,"systolic":121,"diastolic":79,"pulse":64}],
     "body":[{"id":"b1","date":"2026-10-02","time":1759395000000,"weightKg":61.5,"waistCm":70}],
     "pantry":[{"id":"p1","name":"Молоко","baseId":"milk","boughtAt":"2026-10-01","expiresAt":"2026-10-08","amountG":930,"unit":"ml","left":0.5,"createdAt":1759300000000}],
     "health":[{"id":"r1","date":"2026-09-20","kind":"test","title":"ОАК","photos":["file:///data/user/0/io.github.timmylich.wellness/files/health/abc.jpg"],"createdAt":1758300000000}],
     "photoFiles":{"abc.jpg":"/9j/4AAQ"}}
  """.trimIndent()

  @Test fun importsReactNativeBackup() {
    val root = json.parseToJsonElement(rnBackup).jsonObject
    val d = json.decodeFromJsonElement(AppData.serializer(), JsonObject(root - "photoFiles")).normalized(emptyList())
    assertEquals(Sex.Female, d.profile.sex)
    assertEquals(61.5, d.profile.weightKg, 0.0)
    assertEquals("healthConnect", d.settings.stepsSource)
    assertEquals(false, d.settings.sections["heart"])
    assertEquals(1500.0, d.settings.nutrientOverrides["sodium"]!!.max!!, 0.0)
    assertNull(d.settings.nutrientOverrides["sodium"]!!.min)
    assertEquals(2, d.foodLog[0].pieces)
    assertEquals(4.2, d.foodLog[0].food.per100g.protein!!, 0.0)
    assertNull(d.water[0].kind)
    assertTrue(d.water[1].isOther)
    assertEquals(8123.0, d.steps["2026-10-01"]!!, 0.0)
    assertEquals(0.5, d.pantry[0].left, 0.0)
    assertEquals(1, d.health[0].photos.size)
    assertNotNull(d.meta.stepCounter)
  }

  @Test fun roundTripKeepsEveryField() {
    val root = json.parseToJsonElement(rnBackup).jsonObject
    val src = JsonObject(root - "photoFiles")
    val d = json.decodeFromJsonElement(AppData.serializer(), src).normalized(emptyList())
    val out = json.encodeToJsonElement(AppData.serializer(), d).jsonObject
    // Everything the RN app wrote comes back out (the native app may add defaults, never drop).
    fun missing(a: JsonElement, b: JsonElement, path: String): List<String> = when {
      a is JsonObject && b is JsonObject -> a.flatMap { (k, v) -> b[k]?.let { missing(v, it, "$path.$k") } ?: listOf("$path.$k") }
      a is JsonArray && b is JsonArray -> a.indices.flatMap { missing(a[it], b.getOrNull(it) ?: return@flatMap listOf("$path[$it]"), "$path[$it]") }
      else -> emptyList()
    }
    assertEquals(emptyList<String>(), missing(src, out, ""))
    assertEquals(d, json.decodeFromJsonElement(AppData.serializer(), out).normalized(emptyList()))
  }

  @Test fun everyStringKeyExists() {
    val dicts = listOf("ru", "en").associateWith { Json.parseToJsonElement(File("src/main/assets/i18n_$it.json").readText()).jsonObject }
    fun has(root: JsonObject, path: String): Boolean {
      var cur: JsonElement = root
      for (p in path.split('.')) cur = when (cur) {
        is JsonObject -> cur[p] ?: return false
        is JsonArray -> cur.getOrNull(p.toIntOrNull() ?: return false) ?: return false
        else -> return false
      }
      return true
    }
    // Static keys used as dict["…"], L["…"], .t("…", …), .list("…") and .objects("…").
    val key = Regex("""(?:\b(?:L|dict)\[|\.t\(|\.list\(|\.objects\()"([a-zA-Z0-9.]+)"""")
    val used = File("src/main/java").walk().filter { it.extension == "kt" }.flatMap { f -> key.findAll(f.readText()).map { it.groupValues[1] } }.toSet()
    assertTrue("found only ${used.size} keys", used.size > 300)
    // Keys built at runtime from data values ("heart.bp.$key.label" and so on).
    val nutrients = io.github.timmylich.wellness.data.Nutrient.entries.flatMap { n -> listOf("label", "short", "hint", "rec").map { "nutrients.${n.key}.$it" } }
    val sections = io.github.timmylich.wellness.data.SECTION_IDS
    val dynamic = nutrients +
      listOf("crisis", "stage2", "stage1", "elevated", "low", "normal").flatMap { listOf("heart.bp.$it.label", "heart.bp.$it.advice") } +
      listOf("veryLow", "low", "normal", "elevated", "tachy").map { "heart.pulseCat.$it" } +
      listOf("severeUnder", "under", "normal", "over", "obese").map { "body.bmiCat.$it" } +
      listOf("essential", "athletic", "fit", "average", "high").map { "body.fatCat.$it" } +
      listOf("danger", "high", "good", "ok", "low").map { "water.status.$it" } + listOf("water", "other").map { "water.kinds.$it" } +
      listOf("protein", "salty", "addedSugar", "cholesterol", "fat", "sweet", "fiber", "lowKcal").map { "pantry.tags.$it" } +
      listOf("g", "ml", "pcs").map { "pantry.units.$it" } + listOf("visit", "test", "other").map { "health.kinds.$it" } +
      listOf("builtin", "custom", "off", "usda", "ciqual", "pack").map { "food.sources.$it" } +
      listOf("checking", "unavailable", "update", "noPermission", "connected").map { "settings.hcStatus.$it" } +
      listOf("system", "ru", "en").map { "settings.languages.$it" } + listOf("system", "light", "dark").map { "settings.themes.$it" } +
      listOf("lossFast", "lossVeryFast", "gainFast").map { "pace.warnings.$it" } +
      listOf("kcalOver", "kcalUnder", "proteinUnder", "proteinOver", "addedSugarOver", "sodiumOver", "satFatOver", "transFatOver", "fiberUnder", "potassiumUnder", "cholesterolOver", "fatUnder", "allGoodLate", "allGoodSoFar").map { "advice.$it" } +
      listOf("food", "water", "workout", "steps", "heart", "body", "add").map { "fab.$it" } +
      listOf("tips", "pantry", "overview", "health", "more").map { "tabs.$it" } +
      (sections + "summary").map { "sections.$it" } + sections.map { "sectionsLong.$it" } +
      listOf("evm", "sol", "tron", "ton").map { "donation.wallets.$it" } +
      listOf("profile", "goal", "appearance", "sections", "nutrition", "waterActivity", "steps", "notifications", "data", "support", "onboarding", "about").flatMap { listOf("more.$it", "more.subtitles.$it") } +
      listOf("data", "settings", "app").map { "more.groups.$it" } +
      listOf("notBackup", "noFoods").map { "settings.$it" }
    val missing = dicts.flatMap { (lang, root) -> (used + dynamic).filterNot { has(root, it) }.map { "$lang: $it" } }
    assertEquals(emptyList<String>(), missing)
  }
}
