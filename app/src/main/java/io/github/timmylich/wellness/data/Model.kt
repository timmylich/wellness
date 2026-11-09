/**
 * Persisted data. The JSON shape is the backup format, unchanged since the React Native versions
 * (0.5.x), so backups move between versions in both directions. Missing fields take
 * the defaults below; unknown fields are ignored.
 */
package io.github.timmylich.wellness.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.util.Calendar

val json = Json {
  ignoreUnknownKeys = true
  // Like JS `undefined`: absent optional fields are not written.
  explicitNulls = false
  encodeDefaults = true
  // null / unknown enum values in old files fall back to the field default.
  coerceInputValues = true
  isLenient = true
}

const val SCHEMA_VERSION = 1

@Serializable enum class Sex { @SerialName("male") Male, @SerialName("female") Female }

@Serializable enum class ThemeMode { @SerialName("system") System, @SerialName("light") Light, @SerialName("dark") Dark }

@Serializable enum class LanguageSetting { @SerialName("system") System, @SerialName("ru") Ru, @SerialName("en") En }

@Serializable
data class Profile(
  val name: String = "",
  val sex: Sex = Sex.Male,
  val birthYear: Int = Calendar.getInstance().get(Calendar.YEAR) - 30,
  val heightCm: Double = 175.0,
  val weightKg: Double = 75.0,
  /** Goal weight; informational (ETA) and used to scale weight-based norms for high body weight. */
  val targetWeightKg: Double = 75.0,
  /** kg per week: negative = lose, 0 = maintain, positive = gain. Range −1.5…+1.5. */
  val weeklyChangeKg: Double = 0.0,
  /** Everyday activity multiplier without logged workouts (1.2 desk job … 1.725 physical work). */
  val lifestyleFactor: Double = 1.3,
  val bmrOverride: Double? = null,
  /** v0.1 field, converted to [weeklyChangeKg] on load. */
  val paceKgPerWeek: Double? = null,
)

/** Nutrient values per 100 g; any of them may be unknown. */
@Serializable
data class Per100(
  val kcal: Double? = null,
  val protein: Double? = null,
  val fat: Double? = null,
  val carbs: Double? = null,
  val sugar: Double? = null,
  val addedSugar: Double? = null,
  val sodium: Double? = null,
  val fiber: Double? = null,
  val satFat: Double? = null,
  val transFat: Double? = null,
  val cholesterol: Double? = null,
  val potassium: Double? = null,
) {
  operator fun get(n: Nutrient): Double? = when (n) {
    Nutrient.Kcal -> kcal
    Nutrient.Protein -> protein
    Nutrient.Fat -> fat
    Nutrient.Carbs -> carbs
    Nutrient.Sugar -> sugar
    Nutrient.AddedSugar -> addedSugar
    Nutrient.Sodium -> sodium
    Nutrient.Fiber -> fiber
    Nutrient.SatFat -> satFat
    Nutrient.TransFat -> transFat
    Nutrient.Cholesterol -> cholesterol
    Nutrient.Potassium -> potassium
  }

  companion object {
    fun of(values: Map<Nutrient, Double?>) = Per100(
      values[Nutrient.Kcal], values[Nutrient.Protein], values[Nutrient.Fat], values[Nutrient.Carbs],
      values[Nutrient.Sugar], values[Nutrient.AddedSugar], values[Nutrient.Sodium], values[Nutrient.Fiber],
      values[Nutrient.SatFat], values[Nutrient.TransFat], values[Nutrient.Cholesterol], values[Nutrient.Potassium],
    )
  }
}

/** Nutrient keys in display order; [key] is the JSON / i18n name. */
enum class Nutrient(val key: String) {
  Kcal("kcal"), Protein("protein"), Fat("fat"), Carbs("carbs"), Sugar("sugar"), AddedSugar("addedSugar"),
  Sodium("sodium"), Fiber("fiber"), SatFat("satFat"), TransFat("transFat"), Cholesterol("cholesterol"), Potassium("potassium");

  companion object {
    fun of(key: String) = entries.firstOrNull { it.key == key }
  }
}

@Serializable
data class Food(
  val id: String,
  val name: String,
  val brand: String? = null,
  /** Extra searchable names (e.g. the name in another language). */
  val aliases: List<String>? = null,
  /** builtin | custom | off | usda | ciqual | pack */
  val source: String = "custom",
  val per100g: Per100 = Per100(),
  /** Typical serving size in grams, used as default amount. */
  val servingG: Double? = null,
  val barcode: String? = null,
)

@Serializable
data class FoodEntry(
  val id: String,
  val date: String,
  val time: Long,
  /** breakfast | lunch | dinner | snack (inferred from the time of day). */
  val meal: String = "snack",
  /** Snapshot of the food so history stays correct if the food is edited later. */
  val food: Food,
  val grams: Double,
  /** How many pieces the grams were entered as. */
  val pieces: Int? = null,
)

@Serializable
data class WaterEntry(
  val id: String,
  val date: String,
  val time: Long,
  val ml: Double,
  /** water | other; missing in v0.2 data, meaning water. */
  val kind: String? = null,
) {
  val isOther get() = kind == "other"
}

@Serializable
data class WorkoutTemplate(
  val id: String,
  val name: String,
  /** Metabolic equivalent of task. */
  val met: Double,
  val defaultMinutes: Double = 30.0,
  val description: String? = null,
  val builtin: Boolean? = null,
)

@Serializable
data class WorkoutEntry(
  val id: String,
  val date: String,
  val time: Long,
  val name: String,
  val minutes: Double = 0.0,
  val kcal: Double = 0.0,
  val note: String? = null,
)

@Serializable
data class HeartEntry(
  val id: String,
  val date: String,
  val time: Long,
  val systolic: Double? = null,
  val diastolic: Double? = null,
  val pulse: Double? = null,
  val note: String? = null,
)

@Serializable
data class BodyEntry(
  val id: String,
  val date: String,
  val time: Long,
  val weightKg: Double? = null,
  val bodyFatPct: Double? = null,
  val waistCm: Double? = null,
  val neckCm: Double? = null,
  val hipCm: Double? = null,
  val heightCm: Double? = null,
)

@Serializable
data class NotificationSettings(
  val enabled: Boolean = false,
  /** Remind before pantry products expire. */
  val expiry: Boolean = true,
  /** Check GitHub Releases for a newer version. */
  val updates: Boolean = true,
)

@Serializable
data class RangeOverride(val min: Double? = null, val max: Double? = null)

val SECTION_IDS = listOf("nutrition", "water", "activity", "heart", "body", "pantry", "health", "tips")
val DEFAULT_SECTIONS = SECTION_IDS.associateWith { it != "pantry" }

@Serializable
data class Settings(
  val language: LanguageSetting = LanguageSetting.System,
  val theme: ThemeMode = ThemeMode.System,
  val animations: Boolean = true,
  /** device | healthConnect */
  val stepsSource: String = "device",
  val accent: String = "#2E9E6A",
  val amoled: Boolean = false,
  val sections: Map<String, Boolean> = DEFAULT_SECTIONS,
  val adaptiveTargets: Boolean = false,
  val onlineFoodSearch: Boolean = true,
  val waterPresets: List<Double> = listOf(150.0, 250.0, 330.0, 500.0),
  val waterAmountMl: Double = 250.0,
  val waterDangerMl: Double = 6000.0,
  val stepGoal: Double = 8000.0,
  val showHints: Boolean = true,
  val donationPrompts: Boolean = true,
  val stepsInfoDismissed: Boolean = false,
  val usdaApiKey: String = "",
  val notifications: NotificationSettings = NotificationSettings(),
  val nutrientOverrides: Map<String, RangeOverride> = emptyMap(),
) {
  fun section(id: String) = sections[id] ?: DEFAULT_SECTIONS[id] ?: false
}

@Serializable
data class Meta(
  val onboarded: Boolean = false,
  /** Distinct local days on which the app was opened. */
  val usageDays: List<String> = emptyList(),
  /** Usage-day milestones for which the donation prompt was already shown. */
  val donationShown: List<Int> = emptyList(),
  /** Step-counter baseline written by 0.5.x; kept so backups round-trip. */
  val stepCounter: JsonElement? = null,
  val updateCheckedAt: Long? = null,
  val updateNotified: String? = null,
  val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class PantryItem(
  val id: String,
  val name: String,
  val baseId: String? = null,
  val per100g: Per100? = null,
  val boughtAt: String,
  val expiresAt: String,
  /** Package size in [unit] (grams by default). */
  val amountG: Double? = null,
  /** g | ml | pcs */
  val unit: String? = null,
  /** Fraction left, 0…1. */
  val left: Double = 1.0,
  val createdAt: Long = 0,
)

@Serializable
data class HealthRecord(
  val id: String,
  val date: String,
  /** visit | test | other */
  val kind: String = "other",
  val title: String,
  val place: String? = null,
  val notes: String? = null,
  /** file:// URIs of photos copied into the app's files folder. */
  val photos: List<String> = emptyList(),
  val createdAt: Long = 0,
)

@Serializable
data class AppData(
  val schemaVersion: Int = SCHEMA_VERSION,
  val profile: Profile = Profile(),
  val settings: Settings = Settings(),
  val meta: Meta = Meta(),
  val foods: List<Food> = emptyList(),
  val favorites: List<String> = emptyList(),
  val foodLog: List<FoodEntry> = emptyList(),
  val water: List<WaterEntry> = emptyList(),
  val workouts: List<WorkoutEntry> = emptyList(),
  val workoutTemplates: List<WorkoutTemplate> = emptyList(),
  val steps: Map<String, Double> = emptyMap(),
  val heart: List<HeartEntry> = emptyList(),
  val body: List<BodyEntry> = emptyList(),
  val pantry: List<PantryItem> = emptyList(),
  val health: List<HealthRecord> = emptyList(),
)

/**
 * Fill in what older or partial data lacks: nested maps merge with defaults, legacy fields are migrated, built-in workouts come back if missing.
 */
fun AppData.normalized(builtinWorkouts: List<WorkoutTemplate>): AppData {
  val p = profile
  val migrated = if (p.paceKgPerWeek == null) p else {
    val diff = p.targetWeightKg - p.weightKg
    p.copy(weeklyChangeKg = if (kotlin.math.abs(diff) < 0.3) 0.0 else Math.signum(diff) * p.paceKgPerWeek, paceKgPerWeek = null)
  }
  return copy(
    schemaVersion = SCHEMA_VERSION,
    profile = migrated,
    settings = settings.copy(
      sections = DEFAULT_SECTIONS + settings.sections,
      // v0.2 called the live pedometer source 'sensor'; the device counter replaces it.
      stepsSource = if (settings.stepsSource == "healthConnect") "healthConnect" else "device",
    ),
    workoutTemplates = workoutTemplates.ifEmpty { builtinWorkouts },
  )
}
