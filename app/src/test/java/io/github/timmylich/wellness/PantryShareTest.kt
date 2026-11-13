package io.github.timmylich.wellness

import io.github.timmylich.wellness.data.Meta
import io.github.timmylich.wellness.data.PantryItem
import io.github.timmylich.wellness.data.PantryOutcome
import io.github.timmylich.wellness.data.ShelfBase
import io.github.timmylich.wellness.data.ShoppingItem
import io.github.timmylich.wellness.lib.Backup
import io.github.timmylich.wellness.lib.PantryShare
import io.github.timmylich.wellness.lib.ShareError
import io.github.timmylich.wellness.lib.Shared
import io.github.timmylich.wellness.screens.openedExpiry
import io.github.timmylich.wellness.screens.pantryStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PantryShareTest {
  private fun item(id: String, left: Double = 1.0) = PantryItem(id, "p$id", boughtAt = "2026-01-01", expiresAt = "2026-01-10", left = left)
  private fun ids(l: List<PantryItem>) = l.associate { it.id to it.left }
  private fun merge(b: List<PantryItem>, l: List<PantryItem>, r: List<PantryItem>) = PantryShare.mergeList(b, l, r) { it.id }

  @Test fun mergeTakesOneSidedChanges() {
    val base = listOf(item("a"), item("b"), item("c"))
    val local = listOf(item("a", 0.5), item("b"), item("d")) // edited a, deleted c, added d
    val remote = listOf(item("a"), item("b", 0.2), item("c"), item("e")) // edited b, added e
    assertEquals(mapOf("a" to 0.5, "b" to 0.2, "d" to 1.0, "e" to 1.0), ids(merge(base, local, remote)))
  }

  @Test fun mergeConflicts() {
    val base = listOf(item("a"), item("b"), item("c"))
    // a: both edited → this phone wins; b: deleted here, edited there → edit wins; c: deleted there, edited here → kept.
    val local = listOf(item("a", 0.5), item("c", 0.3))
    val remote = listOf(item("a", 0.7), item("b", 0.4))
    assertEquals(mapOf("a" to 0.5, "b" to 0.4, "c" to 0.3), ids(merge(base, local, remote)))
  }

  @Test fun joinMergeIsUnion() {
    assertEquals(setOf("a", "b"), ids(merge(emptyList(), listOf(item("a")), listOf(item("b")))).keys)
  }

  @Test fun sealRoundTripAndTamper() {
    val code = PantryShare.newCode()
    val items = Shared(listOf(item("a", 0.25), item("b")), listOf(ShoppingItem("s", "Milk")))
    val sealed = PantryShare.seal(code, items)
    assertEquals(items, PantryShare.open(code, sealed))
    assertNotEquals(sealed, PantryShare.seal(code, items)) // fresh IV each time
    assertThrows(ShareError::class.java) { PantryShare.open(PantryShare.newCode(), sealed) }
    val bytes = java.util.Base64.getDecoder().decode(sealed).also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
    assertThrows(ShareError::class.java) { PantryShare.open(code, java.util.Base64.getEncoder().encodeToString(bytes)) }
  }

  @Test fun codes() {
    val code = PantryShare.newCode()
    assertEquals(16, code.length)
    assertEquals(code, PantryShare.normalize(PantryShare.pretty(code).lowercase()))
    assertEquals("0123456789ABCDEF", PantryShare.normalize("o123-4567 89ab-cdef"))
    assertEquals("1111ABCDEFGHJKMN", PantryShare.normalize("iIlL-ABCD-EFGH-JKMN"))
    assertNull(PantryShare.normalize("UUUU-ABCD-EFGH-JKMN")) // U is not in the alphabet
    assertNull(PantryShare.normalize("ABCD-1234"))
    assertEquals(64, PantryShare.idOf(code).length)
  }

  @Test fun backupDue() {
    val day = 86_400_000L
    assertEquals(30 * day, Backup.dueAt(Meta(createdAt = 0)))
    assertEquals(40 * day, Backup.dueAt(Meta(createdAt = 0, backupAt = 10 * day)))
    assertEquals(50 * day, Backup.dueAt(Meta(createdAt = 0, backupAt = 10 * day, backupRemindedAt = 20 * day)))
  }

  @Test fun legacyArrayPayload() {
    assertEquals(Shared(listOf(item("a"))), PantryShare.decode(io.github.timmylich.wellness.data.json.encodeToString(kotlinx.serialization.builtins.ListSerializer(PantryItem.serializer()), listOf(item("a")))))
  }

  @Test fun sharedMergeCoversShopping() {
    val m = PantryShare.merge(Shared(s = listOf(ShoppingItem("x", "Bread"))), Shared(s = listOf(ShoppingItem("x", "Bread"), ShoppingItem("y", "Eggs"))), Shared())
    // Bread was bought (removed) on the other phone, Eggs was added here.
    assertEquals(listOf("y"), m.s.map { it.id })
  }

  @Test fun openingShortensExpiry() {
    val milk = ShelfBase("milk", "", "", 7, emptyList(), opened = 7)
    assertEquals("2026-01-08", openedExpiry("2026-01-20", milk, "2026-01-01"))
    assertEquals("2026-01-05", openedExpiry("2026-01-05", milk, "2026-01-01"))
    assertEquals("2026-01-20", openedExpiry("2026-01-20", ShelfBase("eggs", "", "", 28, emptyList()), "2026-01-01"))
  }

  @Test fun stats() {
    val log = listOf(
      PantryOutcome("1", "2026-01-30", "Milk", "eaten"),
      PantryOutcome("2", "2026-01-29", "Bread", "wasted", 0.5),
      PantryOutcome("3", "2026-01-20", "bread ", "wasted", 1.0),
      PantryOutcome("4", "2025-12-01", "Old", "wasted", 1.0),
    )
    val s = pantryStats(log, "2026-01-30")
    assertEquals(1, s.eaten)
    assertEquals(2, s.wasted)
    assertEquals(50, s.lossPct) // (0.5 + 1) / 3
    assertEquals(listOf("Bread" to 2), s.top)
  }

  @Test fun changeSummary() {
    val b = Shared(listOf(item("a"), item("b")), listOf(ShoppingItem("s", "Eggs")))
    val l = Shared(listOf(item("a", 0.5), item("c")), listOf(ShoppingItem("s", "Eggs", boughtAt = 1), ShoppingItem("t", "Tea")))
    assertEquals("✎ pa, + pc, − pb, ✓ Eggs …", PantryShare.summary(b, l))
    assertEquals("", PantryShare.summary(b, b))
  }

  @Test fun historyIsSharedAndSummarized() {
    val o = PantryOutcome("o1", "2026-01-02", "pb", "wasted", 0.5, at = 5)
    val b = Shared(listOf(item("a"), item("b")))
    val l = Shared(listOf(item("a")), l = listOf(o))
    // Thrown away is reported once, not also as "removed".
    assertEquals("🗑 pb", PantryShare.summary(b, l))
    val m = PantryShare.merge(b, l, Shared(listOf(item("a"), item("b")), l = listOf(PantryOutcome("o2", "2026-01-02", "x", "eaten"))))
    assertEquals(setOf("o1", "o2"), m.l.map { it.id }.toSet())
  }
}
