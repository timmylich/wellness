/** First-run wizard: collects the profile used for every recommendation. */
package io.github.timmylich.wellness.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.timmylich.wellness.data.Actions
import io.github.timmylich.wellness.data.LanguageSetting
import io.github.timmylich.wellness.data.SECTION_IDS
import io.github.timmylich.wellness.data.Sex
import io.github.timmylich.wellness.data.Store
import io.github.timmylich.wellness.data.ThemeMode
import io.github.timmylich.wellness.lib.TargetContext
import io.github.timmylich.wellness.lib.baseExpenditure
import io.github.timmylich.wellness.lib.bmi
import io.github.timmylich.wellness.lib.bmiCategory
import io.github.timmylich.wellness.lib.bmr
import io.github.timmylich.wellness.lib.dailyCalorieTarget
import io.github.timmylich.wellness.lib.healthyWeight
import io.github.timmylich.wellness.ui.Button
import io.github.timmylich.wellness.ui.Card
import io.github.timmylich.wellness.ui.Field
import io.github.timmylich.wellness.ui.HRow
import io.github.timmylich.wellness.ui.Hint
import io.github.timmylich.wellness.ui.HeightWheel
import io.github.timmylich.wellness.ui.KgWheels
import io.github.timmylich.wellness.ui.Labeled
import io.github.timmylich.wellness.ui.YearWheel
import io.github.timmylich.wellness.ui.Wrap
import io.github.timmylich.wellness.ui.L
import io.github.timmylich.wellness.ui.Logo
import io.github.timmylich.wellness.ui.Nav
import io.github.timmylich.wellness.ui.PaceSlider
import io.github.timmylich.wellness.ui.Screen
import io.github.timmylich.wellness.ui.Segmented
import io.github.timmylich.wellness.ui.Stat
import io.github.timmylich.wellness.ui.Stats
import io.github.timmylich.wellness.ui.T
import io.github.timmylich.wellness.ui.Toggle
import io.github.timmylich.wellness.ui.c
import io.github.timmylich.wellness.ui.fmt
import io.github.timmylich.wellness.ui.anim
import io.github.timmylich.wellness.ui.numStr
import io.github.timmylich.wellness.ui.parseNum

private const val STEPS = 5

@Composable
fun Onboarding() {
  val dict = L
  val d = Store.data
  val saved = d.profile
  val s = d.settings
  var step by rememberSaveable { mutableIntStateOf(0) }
  // Text fields are kept as strings so the user can type freely.
  var name by rememberSaveable { mutableStateOf(saved.name) }
  var birthYear by rememberSaveable { mutableIntStateOf(saved.birthYear) }
  var height by rememberSaveable { mutableStateOf(saved.heightCm) }
  var weight by rememberSaveable { mutableStateOf(saved.weightKg) }
  var target by rememberSaveable { mutableStateOf(saved.targetWeightKg) }
  var sex by rememberSaveable { mutableStateOf(saved.sex) }
  var pace by rememberSaveable { mutableStateOf(saved.weeklyChangeKg) }
  var lifestyle by rememberSaveable { mutableStateOf(saved.lifestyleFactor) }
  val profile = saved.copy(
    name = name.trim(), sex = sex, birthYear = birthYear, heightCm = height, weightKg = weight,
    targetWeightKg = target, weeklyChangeKg = pace, lifestyleFactor = lifestyle,
  )
  val hw = healthyWeight(profile.heightCm, sex)
  val b = bmi(profile.weightKg, profile.heightCm)
  val ls = lifestyles()

  Screen(null) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Logo(64.dp)
      T(dict.t("onboarding.step", step + 1, STEPS), size = 13, dim = true)
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(STEPS) { i -> Box(Modifier.size(if (i == step) 40.dp else 24.dp, 4.dp).background(if (i <= step) c.accent else c.border, RoundedCornerShape(2.dp))) }
      }
    }
    val motionOn = io.github.timmylich.wellness.ui.LocalMotion.current
    AnimatedContent(step, transitionSpec = { fadeIn(anim(motionOn, tween(220))) togetherWith fadeOut(anim(motionOn, tween(120))) }, label = "step") { st ->
      Card {
        when (st) {
          0 -> {
            T(dict["onboarding.welcomeTitle"], size = 24, bold = true)
            T(dict["onboarding.welcomeText"], dim = true)
            T(dict["onboarding.language"], dim = true, size = 13)
            Segmented(s.language, LanguageSetting.entries.map { it to dict["settings.languages.${it.name.lowercase()}"] }) { v -> Actions.updateSettings { it.copy(language = v) } }
            Field(name, { name = it }, Modifier.fillMaxWidth(), label = dict["onboarding.name"], numeric = false)
            T(dict["onboarding.sex"], dim = true, size = 13)
            Segmented(sex, listOf(Sex.Male to dict["onboarding.male"], Sex.Female to dict["onboarding.female"])) { sex = it }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Labeled(dict["onboarding.birthYear"]) { YearWheel(birthYear) { birthYear = it } } }
          }
          1 -> {
            T(dict["onboarding.bodyTitle"], size = 22, bold = true)
            Wrap(16.dp, center = true) {
              Labeled(dict["onboarding.height"]) { HeightWheel(height) { height = it } }
              Labeled(dict["onboarding.weight"]) {
                KgWheels(weight) {
                  // The goal follows the weight until the user sets it.
                  if (target == weight) target = it
                  weight = it
                }
              }
            }
            Stats {
              Stat(dict["onboarding.bmi"], fmt(b, 1), Modifier.weight(1f))
              Stat(dict["onboarding.category"], dict["body.bmiCat.${bmiCategory(b).key}"], Modifier.weight(1f))
            }
            T(dict.t("onboarding.healthyRange", hw.min, hw.max), dim = true, size = 13)
            Hint(dict["onboarding.bmiHintTitle"], dict["onboarding.bmiHint"])
          }
          2 -> {
            T(dict["onboarding.goalTitle"], size = 22, bold = true)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Labeled(dict["onboarding.targetWeight"]) { KgWheels(target) { target = it } } }
            T(dict["pace.title"], bold = true)
            PaceSlider(profile) { pace = it }
          }
          3 -> {
            T(dict["onboarding.activityTitle"], size = 22, bold = true)
            T(dict["onboarding.activityText"], dim = true, size = 13)
            for ((v, label, hint) in ls) Button("$label — $hint", Modifier.fillMaxWidth(), kind = if (lifestyle == v) "primary" else "secondary") { lifestyle = v }
            Stats {
              Stat(dict["onboarding.bmrStat"], fmt(bmr(profile)), Modifier.weight(1f), dict["units.kcal"])
              Stat(dict["onboarding.baseStat"], fmt(baseExpenditure(profile)), Modifier.weight(1f), dict["units.kcal"])
            }
            Hint(dict["onboarding.bmrHintTitle"], dict["onboarding.bmrHint"])
          }
          else -> {
            T(dict["onboarding.finishTitle"], size = 22, bold = true)
            T(dict["onboarding.finishText"], dim = true, size = 13)
            for (id in SECTION_IDS) Toggle(dict["sectionsLong.$id"], s.section(id)) { Actions.setSection(id, it) }
            T(dict["settings.theme"], bold = true)
            Segmented(s.theme, ThemeMode.entries.map { it to dict["settings.themes.${it.name.lowercase()}"] }) { v -> Actions.updateSettings { it.copy(theme = v) } }
            Toggle(dict["settings.amoled"], s.amoled, dict["settings.amoledHint"]) { v -> Actions.updateSettings { it.copy(amoled = v) } }
            Toggle(dict["settings.adaptive"], s.adaptiveTargets, dict["settings.adaptiveHint"]) { v -> Actions.updateSettings { it.copy(adaptiveTargets = v) } }
            Column(Modifier.fillMaxWidth().background(c.accentSoft, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              T(dict["onboarding.yourNorm"], bold = true)
              val r = dailyCalorieTarget(profile, TargetContext(0.0, 0.0))
              T(dict.t("onboarding.normLine", r.target, r.min, r.max))
              T(dict["onboarding.normNote"], dim = true, size = 12)
            }
          }
        }
      }
    }
    HRow {
      if (step > 0) Button(dict["common.back"], Modifier.weight(1f), kind = "secondary") { step-- }
      Button(if (step == STEPS - 1) dict["common.start"] else dict["common.next"], Modifier.weight(2f)) {
        Actions.updateProfile { profile }
        if (step < STEPS - 1) step++
        else {
          Actions.completeOnboarding()
          Nav.reset()
        }
      }
    }
    T(dict["common.disclaimer"], Modifier.fillMaxWidth(), dim = true, size = 11, center = true)
    Box(Modifier.height(8.dp))
  }
}
