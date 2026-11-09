/** Local-time date helpers. All day keys are "YYYY-MM-DD" in the device timezone. */
package io.github.timmylich.wellness.lib

import io.github.timmylich.wellness.i18n.Dict
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

fun todayKey(): String = LocalDate.now().toString()

fun parseKey(key: String): LocalDate = LocalDate.parse(key)

fun addDays(key: String, n: Int): String = parseKey(key).plusDays(n.toLong()).toString()

/** Keys of the last [n] days ending with [end] (inclusive), oldest first. */
fun lastDays(n: Int, end: String = todayKey()): List<String> = (0 until n).map { addDays(end, it - n + 1) }

fun daysBetween(from: String, to: String): Long = ChronoUnit.DAYS.between(parseKey(from), parseKey(to))

fun keyOf(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString()

private fun two(n: Int) = n.toString().padStart(2, '0')

fun shortLabel(key: String): String = parseKey(key).let { "${it.dayOfMonth}.${two(it.monthValue)}" }

/** Weekday index as in JS (0 = Sunday). */
private fun jsDay(d: LocalDate) = d.dayOfWeek.value % 7

fun weekdayLabel(key: String, L: Dict): String = L.list("dates.weekdays")[jsDay(parseKey(key))]

fun humanDate(key: String, L: Dict): String {
  val t = todayKey()
  if (key == t) return L["dates.today"]
  if (key == addDays(t, -1)) return L["dates.yesterday"]
  val d = parseKey(key)
  // Dates a year or more away (e.g. long best-before dates) also show the year.
  val far = abs(ChronoUnit.DAYS.between(parseKey(t), d)) >= 365
  return "${d.dayOfMonth} ${L.list("dates.months")[d.monthValue - 1]}${if (far) " ${d.year}" else ""}, ${L.list("dates.weekdays")[jsDay(d)]}"
}

fun localTime(ms: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())

fun timeLabel(ms: Long): String = localTime(ms).let { "${two(it.hour)}:${two(it.minute)}" }

/** Timestamp of [key] at hours:minutes, local time. */
fun atTime(key: String, hours: Int, minutes: Int): Long =
  parseKey(key).atTime(hours, minutes).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Epoch ms of local midnight starting [key]. */
fun startOfDay(key: String): Long = atTime(key, 0, 0)
