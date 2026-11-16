/** Pure pantry helpers: dish ideas from what is at home, and merging duplicate products. */
package io.github.timmylich.wellness.lib

import io.github.timmylich.wellness.data.PantryItem
import kotlinx.serialization.Serializable

/** One ingredient of a dish: names for display and name fragments matched at word starts. */
@Serializable
data class Ingredient(val ru: String, val en: String, val m: List<String>)

/** A simple dish (assets/recipes.json); salt, oil, water and spices are assumed at home. */
@Serializable
data class Recipe(val id: String, val ru: String, val en: String, val min: Int, val need: List<Ingredient>, val opt: List<Ingredient> = emptyList())

/** [used]: pantry products the dish would use; [missing]: required ingredients not at home. */
data class DishIdea(val recipe: Recipe, val used: List<PantryItem>, val missing: List<Ingredient>, val urgency: Long)

/** Lower case with ё = е, for forgiving name comparison. */
fun normName(s: String): String = s.trim().lowercase().replace('ё', 'е')

private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

/** A product name split into words once, for matching many fragments against it. */
private class Words(val item: PantryItem) {
  val text = normName(item.name)
  val words = text.split(NON_WORD)

  /** A word starts with [f] (normalized); a fragment with a space matches anywhere. */
  fun has(f: String) = if (' ' in f) text.contains(f) else words.any { it.startsWith(f) }
}

/** True if a word of [name] starts with [fragment] (a fragment with a space matches anywhere). */
fun nameMatches(name: String, fragment: String): Boolean = Words(PantryItem("", name, boughtAt = "", expiresAt = "")).has(normName(fragment))

private fun findFor(ing: Ingredient, items: List<Words>) = items.firstOrNull { w -> ing.m.any { w.has(it) } }?.item

/**
 * Dishes that can be cooked from [items] (left > 0), plus ones missing at most [maxMissing]
 * required ingredients. Complete dishes first; then the ones using soon-to-expire products.
 */
fun dishIdeas(recipes: List<Recipe>, items: List<PantryItem>, today: String = todayKey(), maxMissing: Int = 1): List<DishIdea> {
  // Soonest expiry first, so each ingredient uses the product that needs eating first.
  val home = items.filter { it.left > 0 }.sortedBy { it.expiresAt }.map { Words(it) }
  return recipes.mapNotNull { r ->
    val needed = r.need.map { it to findFor(it, home) }
    val missing = needed.filter { it.second == null }.map { it.first }
    if (missing.size > maxMissing || missing.size == r.need.size) return@mapNotNull null
    val used = (needed.mapNotNull { it.second } + r.opt.mapNotNull { findFor(it, home) }).distinctBy { it.id }
    DishIdea(r, used, missing, used.minOf { daysBetween(today, it.expiresAt) })
  }.sortedWith(compareBy<DishIdea> { it.missing.size }.thenBy { it.urgency }.thenByDescending { it.used.size })
}

/** Groups of products at home with the same name (two or more each). */
fun duplicateGroups(items: List<PantryItem>): List<List<PantryItem>> =
  items.filter { it.left > 0 }.groupBy { normName(it.name) }.values.filter { it.size > 1 }

/**
 * One product from several with the same name: the earliest expiry and purchase (the safe side),
 * amounts added up when every package has one in the same unit, otherwise the fractions left.
 */
fun mergeItems(items: List<PantryItem>): PantryItem {
  val first = items.minBy { it.expiresAt }
  val sameUnit = items.all { it.amountG != null && (it.unit ?: "g") == (first.unit ?: "g") }
  val (amount, left) = if (sameUnit) {
    val total = items.sumOf { it.amountG!! }
    total to (items.sumOf { it.amountG!! * it.left } / total)
  } else first.amountG to items.sumOf { it.left }.coerceAtMost(1.0)
  return first.copy(
    boughtAt = items.minOf { it.boughtAt },
    amountG = amount,
    left = left,
    openedAt = items.mapNotNull { it.openedAt }.minOrNull(),
    per100g = first.per100g ?: items.firstNotNullOfOrNull { it.per100g },
  )
}
