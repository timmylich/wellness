/**
 * Repeating activity reminders ("walk 5000 steps", "warm-up every 2 days"). One inexact alarm for
 * the nearest due reminder; when it fires, due reminders that are not done yet notify and the
 * next alarm is set. No exact-alarm permission: Android may delay it a little.
 */
package io.github.timmylich.wellness.lib

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.timmylich.wellness.data.ActivityReminder
import io.github.timmylich.wellness.data.AppData
import io.github.timmylich.wellness.data.Store
import io.github.timmylich.wellness.i18n.Dict
import io.github.timmylich.wellness.i18n.resolveLang
import io.github.timmylich.wellness.steps.StepStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** First moment after [now] when [r] is due: [ActivityReminder.start] + k × N days at its time. */
fun nextDue(r: ActivityReminder, now: LocalDateTime): LocalDateTime {
  val start = LocalDate.parse(r.start)
  val n = r.everyDays.coerceAtLeast(1).toLong()
  val passed = ChronoUnit.DAYS.between(start, now.toLocalDate()).coerceAtLeast(0)
  val t = start.plusDays((passed + n - 1) / n * n).atTime(LocalTime.of(r.hour, r.minute))
  return if (t.isAfter(now)) t else t.plusDays(n)
}

/** Already done on [date]: the steps are walked, or a workout with the task's name is logged within its period. */
fun reminderDone(r: ActivityReminder, d: AppData, date: String, stepsToday: Double): Boolean = when (r.kind) {
  "steps" -> stepsToday >= r.steps
  else -> {
    val name = r.text.trim()
    val from = addDays(date, -r.everyDays.coerceAtLeast(1))
    name.isNotEmpty() && d.workouts.any { it.date > from && it.date <= date && it.name.trim().equals(name, true) }
  }
}

object ActivityReminders {
  private const val ALARM_ID = 7302
  private const val PREFS = "wellness_activity_reminders"

  private fun pending(c: Context) = PendingIntent.getBroadcast(
    c, ALARM_ID, Intent(c, ActivityAlarm::class.java),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
  )

  /** Sets the alarm for the nearest reminder, or cancels it (none on, or notifications off). */
  fun schedule(c: Context, d: AppData = Store.data) {
    val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val now = LocalDateTime.now()
    val next = if (d.settings.notifications.enabled) d.activityReminders.filter { it.on }.minOfOrNull { nextDue(it, now) } else null
    if (next == null) am.cancel(pending(c))
    else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pending(c))
  }

  internal fun fire(c: Context) {
    val d = Store.data
    val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val now = LocalDateTime.now()
    // Due since the previous alarm (at most a day back), so a late alarm still notifies once.
    val firedAt = prefs.getLong("firedAt", 0L).takeIf { it > 0 }?.let(::localTime) ?: now.minusHours(1)
    val since = maxOf(firedAt, now.minusDays(1))
    prefs.edit().putLong("firedAt", System.currentTimeMillis()).apply()
    if (d.settings.notifications.enabled) {
      val today = todayKey()
      val steps = (d.steps[today] ?: 0.0) + StepStore.pending(c, today)
      val dict = Dict.of(c, resolveLang(d.settings.language))
      for (r in d.activityReminders) {
        if (!r.on || nextDue(r, since).isAfter(now) || reminderDone(r, d, today, steps)) continue
        if (r.kind == "steps") Reminders.show(c, "activity-${r.id}", dict["activity.remind.stepsTitle"], dict.t("activity.remind.stepsBody", Math.round(steps), Math.round(r.steps)))
        else Reminders.show(c, "activity-${r.id}", r.text.ifBlank { dict["activity.remind.taskTitle"] }, dict["activity.remind.taskBody"])
      }
    }
    schedule(c, d)
  }
}

/** The reminder alarm; also re-arms it after a reboot or an app update (alarms are gone then). */
class ActivityAlarm : BroadcastReceiver() {
  override fun onReceive(c: Context, intent: Intent) {
    val done = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
      try {
        // The data file is read when the process starts; wait for it a few seconds.
        repeat(80) { if (!Store.ready) delay(100) }
        if (!Store.ready) return@launch
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) ActivityReminders.schedule(c)
        else ActivityReminders.fire(c)
      } finally {
        done.finish()
      }
    }
  }
}
