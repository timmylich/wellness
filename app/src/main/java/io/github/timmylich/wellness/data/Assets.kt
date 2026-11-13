/**
 * Bundled read-only data in assets/: built-in foods (USDA FoodData Central and Skurikhin's tables,
 * rounded), ANSES-CIQUAL 2020 (Etalab Open Licence 2.0, regenerate with scripts/build-ciqual.py),
 * health tips, shelf-life bases (USDA FSIS FoodKeeper) and workout templates (Compendium of
 * Physical Activities, Ainsworth et al.). Each file is parsed on first use.
 */
package io.github.timmylich.wellness.data

import android.content.Context
import io.github.timmylich.wellness.i18n.Lang
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
private data class BuiltinRow(val id: String, val ru: String, val en: String, val servingG: Double? = null, val per100g: Per100)

@Serializable
data class Tip(val id: String, val title: String, val body: List<String>, val source: String? = null)

@Serializable
data class TipCategory(val id: String, val title: String, val icon: String, val tips: List<Tip>)

@Serializable
data class ShelfBase(
  val id: String,
  val ru: String,
  val en: String,
  val days: Int,
  val match: List<String>,
  /** Days in the fridge after opening (lower bound of the USDA FoodKeeper range); null: opening does not matter. */
  val opened: Int? = null,
  /** Typical package: the default amount in [unit] (g | ml | pcs). */
  val amount: Double? = null,
  val unit: String? = null,
)

@Serializable
private data class WorkoutsFile(val builtin: List<WorkoutTemplate>, val en: Map<String, String>)

object Assets {
  private lateinit var app: Context

  fun init(context: Context) {
    app = context.applicationContext
  }

  private fun read(name: String, c: Context = app) = c.assets.open(name).bufferedReader().use { it.readText() }

  private val builtinRows by lazy { json.decodeFromString<List<BuiltinRow>>(read("foods.json")) }
  private val builtinByLang = HashMap<Lang, List<Food>>()

  /** Built-in foods named in [lang]; the other language is kept as a search alias. */
  fun builtinFoods(lang: Lang): List<Food> = synchronized(builtinByLang) {
    builtinByLang.getOrPut(lang) {
      builtinRows.map {
        val (name, alias) = if (lang == Lang.Ru) it.ru to it.en else it.en to it.ru
        Food(id = it.id, name = name, aliases = listOf(alias), source = "builtin", servingG = it.servingG, per100g = it.per100g)
      }
    }
  }

  /** ~2300 generic European foods with English names. */
  val ciqual: List<Food> by lazy {
    val rows = json.parseToJsonElement(read("ciqual.json")).jsonObject["rows"]!!.jsonArray
    rows.map { r ->
      val a = r as JsonArray
      fun opt(i: Int) = a[i].takeIf { it !is JsonNull }?.jsonPrimitive?.double
      Food(
        id = "ciqual-${a[0].jsonPrimitive.int}",
        name = a[1].jsonPrimitive.content,
        brand = "CIQUAL",
        source = "ciqual",
        per100g = Per100(
          kcal = opt(2), protein = opt(3), fat = opt(4), carbs = opt(5), sugar = opt(6), fiber = opt(7),
          satFat = opt(8), cholesterol = opt(9), sodium = opt(10), potassium = opt(11),
        ),
      )
    }
  }

  private val tipsByLang = HashMap<Lang, List<TipCategory>>()

  fun tips(lang: Lang): List<TipCategory> = synchronized(tipsByLang) {
    tipsByLang.getOrPut(lang) { json.decodeFromString(read("tips_${lang.code}.json")) }
  }

  val shelfBases: List<ShelfBase> by lazy { json.decodeFromString(read("shelf.json")) }

  fun shelfBase(id: String?): ShelfBase? = shelfBases.firstOrNull { it.id == id }

  /** Best-guess base for a product name (first base with a matching fragment). */
  fun guessBase(name: String): ShelfBase? {
    val n = name.lowercase()
    if (n.isBlank()) return null
    return shelfBases.firstOrNull { b -> b.match.any { n.contains(it) } }
  }

  private var workouts: WorkoutsFile? = null

  private fun workoutsFile(c: Context): WorkoutsFile = workouts ?: json.decodeFromString<WorkoutsFile>(read("workouts.json", c)).also { workouts = it }

  fun builtinWorkouts(c: Context): List<WorkoutTemplate> = workoutsFile(c).builtin

  /** Built-in templates are stored with the Russian name; show the English one unless renamed. */
  fun workoutName(t: WorkoutTemplate, lang: Lang): String {
    val f = workoutsFile(app)
    val ruDefault = f.builtin.firstOrNull { it.id == t.id }?.name
    return if (lang == Lang.En && t.builtin == true && t.name == ruDefault) f.en[t.id] ?: t.name else t.name
  }
}
