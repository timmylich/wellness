/**
 * Food picker and editor. Favourites and
 * recent foods are one tap away; search shows local and online results in one list (local first).
 */
package io.github.timmylich.wellness.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.timmylich.wellness.data.Actions
import io.github.timmylich.wellness.data.Food
import io.github.timmylich.wellness.data.Nutrient
import io.github.timmylich.wellness.data.Per100
import io.github.timmylich.wellness.data.Store
import io.github.timmylich.wellness.data.uid
import io.github.timmylich.wellness.lib.FoodCache
import io.github.timmylich.wellness.lib.filterOnline
import io.github.timmylich.wellness.lib.lookupBarcode
import io.github.timmylich.wellness.lib.mergeResults
import io.github.timmylich.wellness.lib.searchLocal
import io.github.timmylich.wellness.lib.searchOnline
import io.github.timmylich.wellness.lib.todayKey
import io.github.timmylich.wellness.ui.AmountForm
import io.github.timmylich.wellness.ui.AppSheet
import io.github.timmylich.wellness.ui.Button
import io.github.timmylich.wellness.ui.Card
import io.github.timmylich.wellness.ui.Empty
import io.github.timmylich.wellness.ui.Field
import io.github.timmylich.wellness.ui.Grid2
import io.github.timmylich.wellness.ui.H
import io.github.timmylich.wellness.ui.HRow
import io.github.timmylich.wellness.ui.Hint
import io.github.timmylich.wellness.ui.Icon
import io.github.timmylich.wellness.ui.L
import io.github.timmylich.wellness.ui.Nav
import io.github.timmylich.wellness.ui.Route
import io.github.timmylich.wellness.ui.Screen
import io.github.timmylich.wellness.ui.Segmented
import io.github.timmylich.wellness.ui.T
import io.github.timmylich.wellness.ui.a
import io.github.timmylich.wellness.ui.c
import io.github.timmylich.wellness.ui.confirmDialog
import io.github.timmylich.wellness.ui.defaultMeal
import io.github.timmylich.wellness.ui.fmt
import io.github.timmylich.wellness.ui.numStr
import io.github.timmylich.wellness.ui.nutrientUnit
import io.github.timmylich.wellness.ui.parseNum
import io.github.timmylich.wellness.ui.showDialog
import io.github.timmylich.wellness.ui.tap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Hands a just-created food from the editor back to the picker, which opens its amount sheet. */
object FoodHandoff {
  var pending by mutableStateOf<Food?>(null)
}

/** How many recent foods the picker lists. */
private const val RECENT_MAX = 15

@Composable
private fun FoodRow(food: Food, fav: Boolean, onClick: () -> Unit) {
  val dict = L
  val p = food.per100g
  Row(Modifier.fillMaxWidth().tap(scaleTo = 0.98f, onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Column(Modifier.weight(1f)) {
      T(food.name)
      T(
        "${food.brand?.let { "$it · " } ?: ""}${fmt(p.kcal ?: 0.0)} ${dict["units.kcal"]} · ${dict["nutrients.protein.short"]} ${fmt(p.protein ?: 0.0, 1)} " +
          "${dict["nutrients.fat.short"]} ${fmt(p.fat ?: 0.0, 1)} ${dict["nutrients.carbs.short"]} ${fmt(p.carbs ?: 0.0, 1)} ${dict["food.per100"]}",
        dim = true, size = 12,
      )
    }
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
      T(dict["food.sources.${food.source}"], Modifier.background(c.cardAlt, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp), size = 10, dim = true)
      // Zero/missing salt is usually missing data, not a salt-free product.
      if (p.sodium == null || p.sodium == 0.0) T(dict["food.noSalt"], Modifier.background(c.high.a(0.15f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp), size = 10, color = c.high)
    }
    Icon("star", 20.dp, if (fav) Color(0xFFF5B301) else c.textDim, modifier = Modifier.tap { Actions.toggleFavorite(food.id) })
  }
  androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.border))
}

@Composable
fun FoodSearch(dateArg: String?) {
  val dict = L
  val ctx = LocalContext.current
  val scope = rememberCoroutineScope()
  val d = Store.data
  val date = dateArg ?: todayKey()
  val online = d.settings.onlineFoodSearch
  var query by rememberSaveable { mutableStateOf("") }
  var tab by rememberSaveable { mutableStateOf("recent") }
  var barcode by rememberSaveable { mutableStateOf("") }
  var barcodeBusy by remember { mutableStateOf(false) }
  var barcodeError by remember { mutableStateOf(false) }
  var selected by remember { mutableStateOf<Food?>(null) }
  // Back from "Create product": open the amount sheet for the new food.
  LaunchedEffect(FoodHandoff.pending) {
    FoodHandoff.pending?.let {
      selected = it
      FoodHandoff.pending = null
    }
  }
  val q = query.trim()
  // Previously seen online foods are searchable offline.
  val cached by produceState(emptyList<Food>()) { value = FoodCache.all(ctx) }
  val local by produceState(emptyList<Food>(), q, d.foods, dict.lang, cached) {
    value = withContext(Dispatchers.Default) { searchLocal(q, d.foods, dict.lang, cached) }
  }
  // Online results are kept per query, so stale ones are ignored.
  val remote = remember { mutableStateMapOf<String, Pair<List<Food>, Boolean>>() }
  val canOnline = online && q.length >= 3
  LaunchedEffect(q, canOnline, d.settings.usdaApiKey) {
    if (!canOnline) return@LaunchedEffect
    // Show cached results for this exact query instantly, then refresh from the network.
    FoodCache.query(ctx, q)?.let { if (q !in remote) remote[q] = it to false }
    delay(700)
    try {
      remote[q] = searchOnline(ctx, q, d.settings.usdaApiKey) to false
    } catch (e: Exception) {
      if (remote[q]?.first.isNullOrEmpty()) remote[q] = emptyList<Food>() to true
    }
  }
  val current = remote[q]
  val loading = canOnline && current == null
  val results = mergeResults(local, filterOnline(current?.first ?: emptyList(), q))
  val recent = remember(d.foodLog) {
    val seen = HashSet<String>()
    d.foodLog.asReversed().asSequence().map { it.food }.filter { seen.add(it.id) }.take(RECENT_MAX).toList()
  }
  val known = remember(recent, d.foods) { (recent + d.foods).associateBy { it.id } }
  val favFoods = d.favorites.mapNotNull { known[it] }
  val list: @Composable (List<Food>) -> Unit = { foods -> for (f in foods) androidx.compose.runtime.key(f.id) { FoodRow(f, f.id in d.favorites) { selected = f } } }

  Screen(dict["nutrition.addFood"]) {
    Field(query, { query = it }, Modifier.fillMaxWidth(), numeric = false, placeholder = dict["food.searchPlaceholder"], search = true)
    HRow {
      Button(dict["food.create"], Modifier.weight(1f), kind = "secondary", icon = "plus") { Nav.push(Route.FoodEdit()) }
      Button(dict["food.quick"], Modifier.weight(1f), kind = "secondary", icon = "flame") { Nav.push(Route.FoodEdit(quick = true, date = date)) }
    }
    if (online) Card {
      H(dict["food.barcode"], "barcode")
      HRow {
        Field(barcode, { barcode = it }, Modifier.weight(1f), placeholder = "4601234567890")
        Button(dict["food.find"], enabled = !barcodeBusy) {
          val code = barcode.trim()
          if (code.isEmpty()) return@Button
          d.foods.firstOrNull { it.barcode == code }?.let { selected = it; return@Button }
          barcodeBusy = true
          barcodeError = false
          scope.launch {
            val f = runCatching { lookupBarcode(ctx, code) }.getOrNull()
            barcodeBusy = false
            if (f != null) selected = f else barcodeError = true
          }
        }
      }
      if (barcodeBusy) Spinner()
      if (barcodeError) T(dict["food.barcodeMissing"], color = c.danger, size = 13)
    }
    if (q.isNotEmpty()) Card(gap = 0.dp) {
      list(results)
      if (loading) Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        Spinner()
        T(dict["food.searchingOnline"], dim = true, size = 13)
      }
      if (current?.second == true) T(dict["food.onlineError"], color = c.danger, size = 13)
      if (online && q.length < 3) T(dict["food.minChars"], dim = true, size = 12)
      if (!loading && results.isEmpty()) Empty(dict["food.nothing"])
    } else Card(gap = 0.dp) {
      Segmented(tab, listOf("recent" to dict["food.recent"], "fav" to dict["food.favorites"], "mine" to dict["food.mine"])) { tab = it }
      when (tab) {
        "recent" -> if (recent.isEmpty()) Empty(dict["food.recentEmpty"]) else list(recent)
        "fav" -> if (favFoods.isEmpty()) Empty(dict["food.favEmpty"]) else list(favFoods)
        else -> if (d.foods.isEmpty()) Empty(dict["food.mineEmpty"]) else for (f in d.foods) androidx.compose.runtime.key(f.id) {
          FoodRow(f, f.id in d.favorites) { selected = f }
          T(dict["common.edit"], Modifier.tap { Nav.push(Route.FoodEdit(id = f.id)) }.padding(vertical = 4.dp), size = 12, color = c.accent)
        }
      }
    }
  }
  selected?.let { food ->
    AppSheet({ selected = null }) {
      AmountForm(food) { grams, pieces ->
        // Keep online foods locally so they work offline and show up in "My foods".
        if (food.source == "off" || food.source == "usda") Actions.saveFood(food)
        Actions.logFood(food, grams, defaultMeal(), date, pieces)
        selected = null
        Nav.pop()
      }
    }
  }
}

@Composable
private fun Spinner() = CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, strokeWidth = 2.5.dp)

/**
 * Create or edit a food. Quick mode: values are per portion and logged at once without
 * touching the database. With an entry id: edits that log entry's food snapshot only.
 * A new food can start from a "base" product whose values fill the form.
 */
@Composable
fun FoodEdit(r: Route.FoodEdit) {
  val dict = L
  val d = Store.data
  val entry = r.entry?.let { id -> d.foodLog.firstOrNull { it.id == id } }
  val saved = r.id?.let { id -> d.foods.firstOrNull { it.id == id } }
  val existing = entry?.food ?: saved
  var name by rememberSaveable { mutableStateOf(existing?.name ?: if (r.quick) dict["food.edit.defaultQuickName"] else "") }
  var brand by rememberSaveable { mutableStateOf(existing?.brand ?: "") }
  var serving by rememberSaveable { mutableStateOf(numStr(existing?.servingG)) }
  val values = remember { mutableStateMapOf<Nutrient, String>().apply { Nutrient.entries.forEach { put(it, numStr(existing?.per100g?.get(it))) } } }
  var baseQuery by remember { mutableStateOf("") }
  var baseName by remember { mutableStateOf<String?>(null) }
  val baseResults = remember(baseQuery, d.foods, dict.lang) { searchLocal(baseQuery.trim(), d.foods, dict.lang).take(6) }
  val title = when {
    r.quick -> dict["food.edit.quickTitle"]
    existing != null -> dict["food.edit.editTitle"]
    else -> dict["food.edit.newTitle"]
  }
  Screen(title) {
    if (entry != null) T(dict["food.entryEditNote"], dim = true, size = 13)
    if (existing == null && !r.quick) Card {
      T(dict["food.base"], size = 16, bold = true)
      T(baseName?.let { "✓ $it" } ?: dict["food.baseHint"], dim = true, size = 13)
      Field(baseQuery, { baseQuery = it }, Modifier.fillMaxWidth(), numeric = false, placeholder = dict["food.basePick"], search = true)
      for (f in baseResults) Column(Modifier.fillMaxWidth().tap(scaleTo = 1f) {
        Nutrient.entries.forEach { values[it] = numStr(f.per100g[it]) }
        if (name.isBlank()) name = f.name
        if (serving.isEmpty() && f.servingG != null) serving = numStr(f.servingG)
        baseName = f.name
        baseQuery = ""
      }.padding(vertical = 8.dp)) {
        T(f.name)
        T("${f.brand?.let { "$it · " } ?: ""}${fmt(f.per100g.kcal ?: 0.0)} ${dict["units.kcal"]}${dict["food.per100"]}", dim = true, size = 12)
      }
    }
    Card {
      Field(name, { name = it }, Modifier.fillMaxWidth(), label = dict["food.edit.name"], numeric = false)
      if (!r.quick) HRow {
        Field(brand, { brand = it }, Modifier.weight(1f), label = dict["food.edit.brand"], numeric = false)
        Field(serving, { serving = it }, Modifier.weight(1f), label = dict["food.edit.serving"], suffix = dict["units.g"])
      }
    }
    Card {
      T(if (r.quick) dict["food.edit.perPortion"] else dict["food.edit.per100"], size = 18, bold = true)
      Hint(dict["food.edit.hintTitle"], dict["food.edit.hint"])
      val keys = Nutrient.entries
      Grid2(keys.size) { i, m ->
        val k = keys[i]
        Field(values[k] ?: "", { values[k] = it }, m, label = dict["nutrients.${k.key}.label"], suffix = nutrientUnit(k, dict))
      }
      if (values[Nutrient.Sodium].isNullOrEmpty()) T(dict["food.edit.saltNote"], dim = true, size = 12)
    }
    Button(if (r.quick) dict["food.edit.addToLog"] else dict["common.save"], Modifier.fillMaxWidth(), big = true) {
      if (name.isBlank()) return@Button showDialog(dict["food.edit.needName"])
      val per100 = Per100.of(Nutrient.entries.associateWith { parseNum(values[it] ?: "") })
      if (per100.kcal == null) return@Button showDialog(dict["food.edit.needKcal"])
      val food = Food(
        id = existing?.id ?: "custom-${uid()}", name = name.trim(), brand = brand.trim().ifEmpty { null }, source = existing?.source ?: "custom",
        barcode = existing?.barcode, servingG = parseNum(serving), per100g = per100,
      )
      when {
        entry != null -> Actions.updateFoodEntry(entry.id) { it.copy(food = food.copy(id = entry.food.id, source = entry.food.source)) }
        // Quick mode: per-portion values → log 100 "grams" of a food whose per-100 g equals the portion.
        r.quick -> Actions.logFood(food.copy(servingG = null), 100.0, defaultMeal(), r.date ?: todayKey())
        else -> {
          Actions.saveFood(food)
          // New product: the picker opens its amount sheet right away.
          if (existing == null) FoodHandoff.pending = food
        }
      }
      Nav.pop()
    }
    if (saved != null && entry == null) Button(dict["food.edit.deleteFood"], Modifier.fillMaxWidth(), kind = "danger") {
      confirmDialog(dict["food.edit.deleteTitle"], dict["food.edit.deleteText"], dict["common.cancel"], dict["common.delete"]) {
        Actions.deleteFood(saved.id)
        Nav.pop()
      }
    }
  }
}
