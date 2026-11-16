package io.github.timmylich.wellness

import io.github.timmylich.wellness.data.PantryItem
import io.github.timmylich.wellness.lib.Ingredient
import io.github.timmylich.wellness.lib.Recipe
import io.github.timmylich.wellness.lib.dishIdeas
import io.github.timmylich.wellness.lib.duplicateGroups
import io.github.timmylich.wellness.lib.mergeItems
import io.github.timmylich.wellness.lib.nameMatches
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PantryLogicTest {
  private fun item(id: String, name: String, expires: String = "2026-01-10", amount: Double? = null, unit: String? = null, left: Double = 1.0) =
    PantryItem(id, name, boughtAt = "2026-01-01", expiresAt = expires, amountG = amount, unit = unit, left = left)

  private val eggs = Ingredient("яйца", "eggs", listOf("яйц", "egg"))
  private val milk = Ingredient("молоко", "milk", listOf("молок", "milk"))
  private val flour = Ingredient("мука", "flour", listOf("мук", "flour"))

  @Test
  fun matchesWordStarts() {
    assertTrue(nameMatches("Яйца куриные С0", "яйц"))
    assertTrue(nameMatches("Молоко 3,2%", "молок"))
    assertTrue(nameMatches("Масло сливочное", "масло слив"))
    assertFalse(nameMatches("Пастила", "паста"))
    assertTrue(nameMatches("Ёжики", "еж"))
  }

  @Test
  fun ideasPreferCompleteAndUrgent() {
    val omelette = Recipe("omelette", "Омлет", "Omelette", 10, listOf(eggs), listOf(milk))
    val pancakes = Recipe("pancakes", "Блины", "Crepes", 30, listOf(flour, milk, eggs))
    val items = listOf(item("1", "Яйца", "2026-01-20"), item("2", "Молоко", "2026-01-03"), item("3", "Сыр", left = 0.0))
    val ideas = dishIdeas(listOf(pancakes, omelette), items, "2026-01-01")
    assertEquals(listOf("omelette", "pancakes"), ideas.map { it.recipe.id })
    assertEquals(setOf("1", "2"), ideas[0].used.map { it.id }.toSet())
    assertEquals(2L, ideas[0].urgency)
    assertEquals(listOf(flour), ideas[1].missing)
    // Nothing from home at all: not an idea.
    assertTrue(dishIdeas(listOf(pancakes), emptyList(), "2026-01-01").isEmpty())
  }

  @Test
  fun mergesSameNames() {
    val a = item("a", "Молоко", "2026-01-08", 1000.0, "ml", 0.5)
    val b = item("b", " молоко ", "2026-01-05", 1000.0, "ml", 1.0)
    assertEquals(1, duplicateGroups(listOf(a, b, item("c", "Сыр"))).size)
    val m = mergeItems(listOf(a, b))
    assertEquals("b", m.id)
    assertEquals("2026-01-05", m.expiresAt)
    assertEquals(2000.0, m.amountG!!, 0.0)
    assertEquals(0.75, m.left, 1e-9)
    // Without comparable amounts: fractions add up, capped at a whole package.
    assertEquals(1.0, mergeItems(listOf(item("x", "Хлеб", left = 0.6), item("y", "Хлеб", left = 0.7))).left, 1e-9)
  }

  @Test
  fun bundledRecipesParse() {
    val f = File("src/main/assets/recipes.json").takeIf { it.exists() } ?: File("app/src/main/assets/recipes.json")
    val list = Json.decodeFromString<List<Recipe>>(f.readText())
    assertTrue(list.size >= 30)
    assertEquals(list.size, list.map { it.id }.toSet().size)
    assertTrue(list.all { r -> r.need.isNotEmpty() && (r.need + r.opt).all { it.m.isNotEmpty() } })
  }
}
