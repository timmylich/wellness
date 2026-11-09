/**
 * UI strings from assets/i18n_{ru,en}.json (same keys in both). Plain keys: `L["common.save"]`;
 * templates: `L.t("onboarding.step", 1, 5)`, where {0} is inserted as is, {0:n2} as a locale
 * number with up to 2 decimals and {0:f1} with fixed decimals. The few strings with logic are
 * methods below.
 */
package io.github.timmylich.wellness.i18n

import android.content.Context
import io.github.timmylich.wellness.data.LanguageSetting
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

enum class Lang(val code: String, val locale: Locale) {
  Ru("ru", Locale.forLanguageTag("ru-RU")),
  En("en", Locale.US),
}

/** Languages whose speakers most likely prefer Russian over English among the ones we ship. */
private val RU_LIKE = setOf("ru", "uk", "be", "kk", "ky", "uz", "tg", "hy", "az", "ka")

fun systemLang(): Lang = if (Locale.getDefault().language in RU_LIKE) Lang.Ru else Lang.En

fun resolveLang(s: LanguageSetting): Lang = when (s) {
  LanguageSetting.Ru -> Lang.Ru
  LanguageSetting.En -> Lang.En
  LanguageSetting.System -> systemLang()
}

class Dict(val lang: Lang, private val root: JsonObject) {
  private val ru = lang == Lang.Ru

  fun node(path: String): JsonElement? {
    var cur: JsonElement = root
    for (part in path.split('.')) {
      cur = when (cur) {
        is JsonObject -> cur[part] ?: return null
        is JsonArray -> cur.getOrNull(part.toIntOrNull() ?: return null) ?: return null
        else -> return null
      }
    }
    return cur
  }

  private fun text(e: JsonElement?): String? = when (e) {
    is JsonPrimitive -> e.contentOrNull
    is JsonObject -> e["\$t"]?.jsonPrimitive?.contentOrNull
    else -> null
  }

  /** Missing keys show the key itself, so they are easy to spot. */
  operator fun get(path: String): String = text(node(path)) ?: path

  fun list(path: String): List<String> = (node(path) as? JsonArray)?.map { text(it) ?: "" } ?: emptyList()

  fun objects(path: String): List<JsonObject> = (node(path) as? JsonArray)?.map { it.jsonObject } ?: emptyList()

  /** Fills a template: {0} as text, {0:n2} locale number (≤ 2 decimals), {0:f1} fixed decimals. */
  fun t(path: String, vararg args: Any?): String = fill(get(path), args)

  private fun fill(template: String, args: Array<out Any?>): String =
    PLACEHOLDER.replace(template) { m ->
      val v = args.getOrNull(m.groupValues[1].toInt())
      val spec = m.groupValues[2]
      when {
        spec.startsWith("n") && v is Number -> num(v.toDouble(), spec.drop(1).toInt())
        spec.startsWith("f") && v is Number -> String.format(Locale.US, "%.${spec.drop(1)}f", v.toDouble())
        v is Double && v % 1.0 == 0.0 -> v.toLong().toString()
        else -> v.toString()
      }
    }

  /** Locale number with at most [digits] decimals (like JS toLocaleString). */
  fun num(v: Double, digits: Int = 0): String {
    val f = (if (ru) RU_FORMAT else EN_FORMAT).get()!!
    f.maximumFractionDigits = digits
    // Avoid "-0".
    val x = if (abs(v) < 0.5 * Math.pow(10.0, -digits.toDouble())) 0.0 else v
    return f.format(x)
  }

  // ---- strings with logic ---------------------------------------------------------------

  fun fatEq(g: Double, gained: Boolean) =
    if (ru) "≈ ${num(g)} г жира ${if (gained) "набрано" else "сожжено"}" else "≈ ${num(g)} g of fat ${if (gained) "gained" else "burned"}"

  fun adaptive(k: Double): String {
    val v = (if (k > 0) "+" else "") + num(k)
    return if (ru) "Адаптивная коррекция: $v ккал (компенсация последних 7 дней)" else "Adaptive correction: $v kcal (compensating the last 7 days)"
  }

  fun periodBalance(days: Int, kcal: Double, kg: Double) =
    if (ru) "За $days дн. с записями: ${if (kcal > 0) "профицит" else "дефицит"} ${num(abs(kcal))} ккал ≈ ${num(kg, 2)} кг жира"
    else "Over $days logged days: ${if (kcal > 0) "surplus" else "deficit"} ${num(abs(kcal))} kcal ≈ ${num(kg, 2)} kg of fat"

  fun share(p: Double) = "${p.roundToLong()} % " + if (ru) "от суммы" else "of total"
  fun ofTarget(p: Double) = "${p.roundToLong()} % " + if (ru) "от цели" else "of target"
  fun ofLimit(p: Double) = "${p.roundToLong()} % " + if (ru) "от ограничения" else "of the limit"

  /** [est]: "navy", "bmi" or null (measured). */
  fun fatLine(l: String, est: String?): String {
    val note = when (est) {
      "navy" -> if (ru) " (оценка по обхватам)" else " (estimated from measurements)"
      "bmi" -> if (ru) " (примерная оценка по ИМТ и возрасту, ±4 %; точнее — с обхватами талии и шеи)" else " (rough estimate from BMI and age, ±4 %; add waist and neck for a better one)"
      else -> ""
    }
    return (if (ru) "Процент жира: " else "Body fat: ") + l + note
  }

  fun avgLine(from: Double, to: Double?): String {
    val f1 = { v: Double -> String.format(Locale.US, "%.1f", v) }
    val head = if (ru) "Взвешенное среднее: " else "Weighted average: "
    val unit = if (ru) "кг" else "kg"
    return if (to == null) "$head${f1(from)} $unit" else "$head${f1(from)} → ${f1(to)} $unit"
  }

  fun change(d: Double, since: String): String {
    val v = (if (d > 0) "+" else "") + num(d, 1)
    return if (ru) "Изменение: $v кг с $since" else "Change: $v kg since $since"
  }

  fun daysLeft(d: Long): String = when {
    d == 0L -> if (ru) "Истекает сегодня" else "Expires today"
    d > 0 -> if (ru) "Ещё $d дн." else "$d days left"
    else -> if (ru) "Истёк ${-d} дн. назад" else "Expired ${-d} days ago"
  }

  fun usage(d: Int, s: Int, pending: Boolean) =
    if (ru) "Дней использования: $d. Показано напоминаний: $s.${if (pending) " Есть непоказанное." else ""}"
    else "Days of use: $d. Reminders shown: $s.${if (pending) " One is pending." else ""}"

  companion object {
    private val PLACEHOLDER = Regex("""\{(\d+)(?::(\w+))?\}""")
    private val RU_FORMAT = ThreadLocal.withInitial { NumberFormat.getNumberInstance(Lang.Ru.locale).apply { minimumFractionDigits = 0; roundingMode = java.math.RoundingMode.HALF_UP } }
    private val EN_FORMAT = ThreadLocal.withInitial { NumberFormat.getNumberInstance(Lang.En.locale).apply { minimumFractionDigits = 0; roundingMode = java.math.RoundingMode.HALF_UP } }

    private val cache = HashMap<Lang, Dict>()

    fun of(context: Context, lang: Lang): Dict = synchronized(cache) {
      cache.getOrPut(lang) {
        val text = context.assets.open("i18n_${lang.code}.json").bufferedReader().use { it.readText() }
        Dict(lang, Json.parseToJsonElement(text).jsonObject)
      }
    }
  }
}
