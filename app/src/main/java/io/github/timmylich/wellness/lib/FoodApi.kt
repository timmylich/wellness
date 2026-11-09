/**
 * Food search: local database first (user foods,
 * bundled foods, cached online results, CIQUAL), then the online sources Open Food Facts (open,
 * community-maintained, good CIS coverage) and USDA FoodData Central (public domain).
 * No accounts, no tracking. Online lookups can be disabled in settings.
 */
package io.github.timmylich.wellness.lib

import android.content.Context
import android.util.Log
import io.github.timmylich.wellness.data.Assets
import io.github.timmylich.wellness.data.Food
import io.github.timmylich.wellness.data.Per100
import io.github.timmylich.wellness.data.json
import io.github.timmylich.wellness.i18n.Lang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val USER_AGENT = "Wellness/1.0 (open-source health tracker)"
private const val SEARCH_URL = "https://search.openfoodfacts.org/search"
private const val PRODUCT_URL = "https://world.openfoodfacts.org/api/v2/product"
private const val USDA_URL = "https://api.nal.usda.gov/fdc/v1/foods/search"

/** Names in the languages of our main audience (CIS, Caucasus, Europe). */
private val NAME_LANGS = listOf("ru", "uk", "be", "kk", "ka", "hy", "en", "de", "fr", "pl")
private val FIELDS = "code,product_name," + NAME_LANGS.joinToString(",") { "product_name_$it" } + ",brands,nutriments,serving_quantity"

/** Case/ё-insensitive normalization for local matching. */
private fun norm(s: String) = s.lowercase().replace('ё', 'е').trim()

private val SPLIT = Regex("""[\s,.;()/-]+""")

fun queryWords(query: String): List<String> = norm(query).split(SPLIT).filter { it.isNotEmpty() }

// Normalised words per food object, computed once (thousands of foods per keystroke).
private val tokenCache = WeakHashMap<Food, List<String>>()

private fun tokensOf(f: Food): List<String> = synchronized(tokenCache) {
  tokenCache.getOrPut(f) { queryWords("${f.name} ${f.brand ?: ""} ${(f.aliases ?: emptyList()).joinToString(" ")}") }
}

/**
 * Forgiving word matcher: every query word must match some word of the food (name, brand,
 * aliases), in any order, by its stem ("варёные" finds "варёных") or with a small typo
 * (edit distance 1, or 2 for long words). Returns a score (higher = better) or 0.
 */
fun matchScore(f: Food, words: List<String>): Int {
  if (words.isEmpty()) return 0
  val tokens = tokensOf(f)
  var score = 0
  for (w in words) {
    var best = 0
    for (t in tokens) {
      val s = wordScore(w, t)
      if (s > best) best = s
      if (best == 3) break
    }
    if (best == 0) return 0
    score += best
  }
  // Prefer names where the first query word starts the name.
  if (tokens.isNotEmpty() && wordScore(words[0], tokens[0]) >= 2) score += 1
  return score
}

/** 3: exact/prefix, 2: same stem, 1: typo, 0: no match. */
private fun wordScore(w: String, t: String): Int {
  if (t.startsWith(w)) return 3
  val stem = if (w.length >= 5) w.substring(0, max(4, w.length - 2)) else w
  if (t.startsWith(stem)) return 2
  if (w.length >= 4) {
    val m = if (w.length >= 7) 2 else 1
    // Compare with the token's prefix of similar length, so endings do not count as typos.
    if (levenshtein(w, t.take(w.length), m) <= m) return 1
    if (stem != w && levenshtein(stem, t.take(stem.length), m) <= m) return 1
  }
  return 0
}

/** Edit distance, stopping early once it exceeds [max]. */
private fun levenshtein(a: String, b: String, max: Int): Int {
  if (abs(a.length - b.length) > max) return max + 1
  var prev = IntArray(b.length + 1) { it }
  for (i in 1..a.length) {
    val cur = IntArray(b.length + 1)
    cur[0] = i
    var rowMin = i
    for (j in 1..b.length) {
      cur[j] = min(min(prev[j] + 1, cur[j - 1] + 1), prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
      if (cur[j] < rowMin) rowMin = cur[j]
    }
    if (rowMin > max) return max + 1
    prev = cur
  }
  return prev[b.length]
}

/** Local search across user, built-in, cached and CIQUAL foods, best matches first. */
fun searchLocal(query: String, userFoods: List<Food>, lang: Lang, cached: List<Food> = emptyList(), limit: Int = 60): List<Food> {
  val words = queryWords(query)
  if (words.isEmpty()) return emptyList()
  val userIds = userFoods.mapTo(HashSet()) { it.id }
  val all = userFoods + Assets.builtinFoods(lang) + cached.filter { it.id !in userIds } + Assets.ciqual
  data class Hit(val f: Food, val s: Int, val i: Int)
  val hits = ArrayList<Hit>()
  all.forEachIndexed { i, f ->
    val s = matchScore(f, words)
    if (s > 0) hits += Hit(f, s, i)
  }
  // Best score first; among equals keep source order (user foods, built-in, cache, CIQUAL).
  hits.sortWith(compareByDescending<Hit> { it.s }.thenBy { it.i })
  return hits.take(limit).map { it.f }
}

/** Keep only online results that match every query word (APIs match any word). */
fun filterOnline(foods: List<Food>, query: String): List<Food> {
  val words = queryWords(query)
  return foods.filter { matchScore(it, words) > 0 }
}

/** Local first, online duplicates (same id or same name + brand) dropped. */
fun mergeResults(local: List<Food>, online: List<Food>): List<Food> {
  val seen = local.mapTo(HashSet()) { it.id }
  val names = local.mapTo(HashSet()) { norm("${it.name}|${it.brand ?: ""}") }
  return local + online.filter { it.id !in seen && norm("${it.name}|${it.brand ?: ""}") !in names }
}

// ---- Open Food Facts --------------------------------------------------------------------

private fun JsonElement?.num(): Double? = (this as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }?.takeIf { it.isFinite() }

private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull

/** Map an Open Food Facts product into [Food]. OFF stores sodium etc. in grams. */
fun fromOff(p: JsonObject): Food? {
  val n = p["nutriments"] as? JsonObject ?: JsonObject(emptyMap())
  val kcal = n["energy-kcal_100g"].num() ?: (n["energy_100g"].num() ?: Double.NaN) / 4.184
  // Prefer Russian, then other regional names, then the generic name.
  val name = (NAME_LANGS.map { p["product_name_$it"].str() }.firstOrNull { !it.isNullOrBlank() } ?: p["product_name"].str() ?: "").trim()
  if (name.isEmpty() || !kcal.isFinite()) return null
  fun g(k: String) = n["${k}_100g"].num()
  fun mg(k: String) = g(k)?.let { jsRound(it * 1000) }
  val brands = p["brands"]
  val brand = if (brands is JsonArray) brands.mapNotNull { it.str() }.joinToString(", ") else brands.str()
  val code = p["code"].str()
  return Food(
    id = "off-${code ?: name}",
    name = name,
    brand = brand?.takeIf { it.isNotEmpty() },
    source = "off",
    barcode = code,
    servingG = p["serving_quantity"].num(),
    per100g = Per100(
      kcal = jsRound(kcal), protein = g("proteins"), fat = g("fat"), carbs = g("carbohydrates"), sugar = g("sugars"),
      addedSugar = g("added-sugars"),
      // Prefer explicit sodium; otherwise derive from salt (salt = sodium × 2.5).
      sodium = mg("sodium") ?: g("salt")?.let { jsRound(it * 1000 / 2.5) },
      fiber = g("fiber"), satFat = g("saturated-fat"), transFat = g("trans-fat"), cholesterol = mg("cholesterol"), potassium = mg("potassium"),
    ),
  )
}

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

private fun get(url: String, ua: Boolean = true): JsonElement {
  val c = URL(url).openConnection() as HttpURLConnection
  c.connectTimeout = 10_000
  c.readTimeout = 15_000
  if (ua) c.setRequestProperty("User-Agent", USER_AGENT)
  c.setRequestProperty("Accept", "application/json")
  try {
    if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
    return json.parseToJsonElement(c.inputStream.bufferedReader().use { it.readText() })
  } finally {
    c.disconnect()
  }
}

private fun searchOff(query: String): List<Food> {
  val j = get("$SEARCH_URL?q=${enc(query)}&page_size=40&fields=$FIELDS").jsonObject
  return (j["hits"] as? JsonArray ?: JsonArray(emptyList())).mapNotNull { (it as? JsonObject)?.let(::fromOff) }
}

// ---- USDA FoodData Central (English names only, so only for Latin-script queries) ----------

private val USDA_IDS = mapOf(
  1008 to "kcal", 1003 to "protein", 1004 to "fat", 1005 to "carbs", 2000 to "sugar", 1235 to "addedSugar",
  1093 to "sodium", 1079 to "fiber", 1258 to "satFat", 1257 to "transFat", 1253 to "cholesterol", 1092 to "potassium",
)

/** "CHICKEN, BREAST, ROASTED" → "Chicken, breast, roasted". */
private fun tidyName(s: String): String {
  val lower = if (s == s.uppercase()) s.lowercase() else s
  return lower.replaceFirstChar { it.uppercase() }
}

fun fromUsda(f: JsonObject): Food? {
  val v = HashMap<String, Double>()
  for (n in (f["foodNutrients"] as? JsonArray).orEmpty()) {
    val o = n as? JsonObject ?: continue
    val key = (o["nutrientId"] as? JsonPrimitive)?.intOrNull?.let { USDA_IDS[it] } ?: continue
    o["value"].num()?.let { v[key] = jsRound(it * 10) / 10 }
  }
  val desc = f["description"].str()
  if (v["kcal"] == null || desc.isNullOrEmpty()) return null
  return Food(
    id = "usda-${f["fdcId"].str()}",
    name = tidyName(desc),
    brand = f["brandName"].str()?.takeIf { it.isNotEmpty() } ?: f["brandOwner"].str()?.takeIf { it.isNotEmpty() },
    source = "usda",
    barcode = f["gtinUpc"].str()?.takeIf { it.isNotEmpty() },
    servingG = if (f["servingSizeUnit"].str()?.lowercase() == "g") f["servingSize"].num() else null,
    per100g = Per100(
      v["kcal"], v["protein"], v["fat"], v["carbs"], v["sugar"], v["addedSugar"],
      v["sodium"], v["fiber"], v["satFat"], v["transFat"], v["cholesterol"], v["potassium"],
    ),
  )
}

private fun searchUsda(query: String, apiKey: String): List<Food> {
  val key = apiKey.ifEmpty { "DEMO_KEY" }
  val j = get("$USDA_URL?api_key=${enc(key)}&query=${enc(query)}&pageSize=25&dataType=Foundation,SR%20Legacy,Branded", ua = false).jsonObject
  return (j["foods"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::fromUsda) }
}

private fun isLatin(s: String) = s.any { it in 'a'..'z' || it in 'A'..'Z' } && s.none { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }

/** Search every online source in parallel and merge; fails only if all sources fail. */
suspend fun searchOnline(context: Context, query: String, usdaKey: String): List<Food> = coroutineScope {
  val jobs = buildList {
    add(async(Dispatchers.IO) { runCatching { searchOff(query) } })
    if (isLatin(query)) add(async(Dispatchers.IO) { runCatching { searchUsda(query, usdaKey) } })
  }
  val results = jobs.map { it.await() }
  val ok = results.mapNotNull { it.getOrNull() }
  if (ok.isEmpty()) throw results.first().exceptionOrNull()!!
  // Interleave sources so one database does not bury the other.
  val merged = ArrayList<Food>()
  var i = 0
  while (ok.any { i < it.size }) {
    for (l in ok) if (i < l.size) merged += l[i]
    i++
  }
  FoodCache.storeQuery(context, query, merged)
  merged
}

suspend fun lookupBarcode(context: Context, code: String): Food? {
  FoodCache.barcode(context, code)?.let { return it }
  return withContext(Dispatchers.IO) {
    val j = runCatching { get("$PRODUCT_URL/${enc(code)}?fields=$FIELDS").jsonObject }.getOrNull() ?: return@withContext null
    val product = j["product"] as? JsonObject
    val food = if ((j["status"] as? JsonPrimitive)?.intOrNull == 1 && product != null) fromOff(JsonObject(product + ("code" to JsonPrimitive(code)))) else null
    food?.also { FoodCache.storeFood(context, it) }
  }
}

/**
 * On-device cache for online lookups: query results for 14 days, and every food ever returned
 * (up to 3000, least recently seen dropped first) so the offline database grows with use.
 * Stored separately from AppData: it is a cache, not user data, and is not part of backups.
 */
object FoodCache {
  private const val MAX_FOODS = 3000
  private const val MAX_QUERIES = 300
  private const val QUERY_TTL_MS = 14L * 24 * 3600 * 1000

  @Serializable private data class Seen(val food: Food, val seenAt: Long)
  @Serializable private data class Query(val ids: List<String>, val at: Long)
  @Serializable private data class Data(
    val foods: MutableMap<String, Seen> = HashMap(),
    val queries: MutableMap<String, Query> = HashMap(),
    val barcodes: MutableMap<String, String> = HashMap(),
  )

  private var cache: Data? = null

  private fun file(c: Context) = File(c.filesDir, "foodCache.json")

  private fun normQuery(q: String) = norm(q).replace(Regex("""\s+"""), " ")

  private suspend fun load(c: Context): Data = withContext(Dispatchers.IO) {
    synchronized(this@FoodCache) {
      cache ?: (runCatching { json.decodeFromString<Data>(file(c).readText()) }.getOrNull() ?: Data()).also { cache = it }
    }
  }

  private fun save(c: Context) {
    val d = cache ?: return
    runCatching { synchronized(this) { file(c).writeText(json.encodeToString(Data.serializer(), d)) } }
      .onFailure { Log.w("WellnessFoodCache", "save failed", it) }
  }

  private fun prune(d: Data) {
    if (d.foods.size > MAX_FOODS) d.foods.values.sortedBy { it.seenAt }.take(d.foods.size - MAX_FOODS).forEach { d.foods.remove(it.food.id) }
    if (d.queries.size > MAX_QUERIES) d.queries.entries.sortedBy { it.value.at }.take(d.queries.size - MAX_QUERIES).map { it.key }.forEach { d.queries.remove(it) }
  }

  /** Cached results for a query, or null if missing or expired. */
  suspend fun query(c: Context, q: String): List<Food>? {
    val d = load(c)
    val hit = synchronized(this) { d.queries[normQuery(q)] } ?: return null
    if (System.currentTimeMillis() - hit.at > QUERY_TTL_MS) return null
    return synchronized(this) { hit.ids.mapNotNull { d.foods[it]?.food } }
  }

  suspend fun storeQuery(c: Context, q: String, foods: List<Food>) {
    val d = load(c)
    val now = System.currentTimeMillis()
    withContext(Dispatchers.IO) {
      synchronized(this@FoodCache) {
        for (f in foods) {
          d.foods[f.id] = Seen(f, now)
          f.barcode?.let { d.barcodes[it] = f.id }
        }
        d.queries[normQuery(q)] = Query(foods.map { it.id }, now)
        prune(d)
      }
      save(c)
    }
  }

  suspend fun barcode(c: Context, code: String): Food? {
    val d = load(c)
    return synchronized(this) { d.barcodes[code]?.let { d.foods[it]?.food } }
  }

  suspend fun storeFood(c: Context, f: Food) {
    val d = load(c)
    withContext(Dispatchers.IO) {
      synchronized(this@FoodCache) {
        d.foods[f.id] = Seen(f, System.currentTimeMillis())
        f.barcode?.let { d.barcodes[it] = f.id }
        prune(d)
      }
      save(c)
    }
  }

  /** All cached foods, for offline local search. */
  suspend fun all(c: Context): List<Food> {
    val d = load(c)
    return synchronized(this) { d.foods.values.map { it.food } }
  }

  suspend fun size(c: Context): Int = load(c).let { d -> synchronized(this) { d.foods.size } }

  suspend fun clear(c: Context) = withContext(Dispatchers.IO) {
    synchronized(this@FoodCache) {
      cache = Data()
      file(c).delete()
    }
  }
}
