/**
 * Platform services: local notifications (no push server, no Firebase/Play services), the
 * GitHub Releases update check, Health Connect step sync, and small intents.
 */
package io.github.timmylich.wellness.lib

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.github.timmylich.wellness.BuildConfig
import io.github.timmylich.wellness.R
import io.github.timmylich.wellness.data.Actions
import io.github.timmylich.wellness.data.Store
import io.github.timmylich.wellness.data.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.Period
import kotlin.math.abs
import kotlin.math.roundToLong

fun openUrl(c: Context, url: String) {
  try {
    c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  } catch (_: ActivityNotFoundException) {}
}

// ---- notifications ---------------------------------------------------------------------

/**
 * Shown directly, or scheduled with an inexact AlarmManager alarm (no exact-alarm permission;
 * Android may delay it a little).
 */
object Reminders {
  private const val CHANNEL_ID = "wellness-reminders"

  /** Stable positive id from a string key (same as 0.5.x, so old alarms can be cancelled). */
  fun id(key: String): Int = abs(key.hashCode()).takeIf { it > 0 } ?: 1

  fun show(c: Context, key: String, title: String, body: String, url: String? = null) = show(c, id(key), title, body, url)

  private fun show(c: Context, id: Int, title: String, body: String, url: String?) {
    val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT))
    val open = if (url != null) Intent(Intent.ACTION_VIEW, Uri.parse(url)) else c.packageManager.getLaunchIntentForPackage(c.packageName)
    val pi = open?.let { PendingIntent.getActivity(c, id, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
    val n = Notification.Builder(c, CHANNEL_ID)
      .setSmallIcon(R.drawable.wellness_notification)
      .setContentTitle(title)
      .setContentText(body)
      .setStyle(Notification.BigTextStyle().bigText(body))
      .setAutoCancel(true)
      .setContentIntent(pi)
      .build()
    try {
      nm.notify(id, n)
    } catch (e: SecurityException) {
      Log.w("WellnessReminders", "notification permission missing", e)
    }
  }

  private fun pending(c: Context, id: Int, title: String?, body: String?, create: Boolean): PendingIntent? {
    val i = Intent(c, ReminderReceiver::class.java).putExtra("id", id)
    if (title != null) i.putExtra("title", title).putExtra("body", body)
    val flags = PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
    return PendingIntent.getBroadcast(c, id, i, flags)
  }

  fun schedule(c: Context, key: String, atMs: Long, title: String, body: String) {
    if (atMs <= System.currentTimeMillis()) return
    val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending(c, id(key), title, body, true)!!)
  }

  fun cancel(c: Context, key: String) {
    val pi = pending(c, id(key), null, null, false) ?: return
    (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)
    pi.cancel()
  }

  internal fun fire(c: Context, i: Intent) {
    show(c, i.getIntExtra("id", 0), i.getStringExtra("title") ?: return, i.getStringExtra("body") ?: "", null)
  }
}

class ReminderReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) = Reminders.fire(context, intent)
}

// ---- update check (GitHub Releases; no account, no server of our own) -------------------

object Updates {
  private const val API = "https://api.github.com/repos/timmylich/wellness/releases/latest"
  const val REPO_URL = "https://github.com/timmylich/wellness"
  private const val DAY_MS = 86_400_000L

  /** F-Droid builds turn this off: F-Droid updates the app itself. */
  val builtIn = BuildConfig.UPDATE_CHECK

  val current: String = BuildConfig.VERSION_NAME

  /** Compare dotted versions ("v1.2.10" > "1.2.9"). */
  fun isNewer(latest: String, current: String): Boolean {
    fun p(v: String) = v.removePrefix("v").removePrefix("V").split('.', '-').map { it.toIntOrNull() ?: 0 }
    val a = p(latest)
    val b = p(current)
    for (i in 0 until maxOf(a.size, b.size)) {
      val x = a.getOrElse(i) { 0 }
      val y = b.getOrElse(i) { 0 }
      if (x != y) return x > y
    }
    return false
  }

  data class Release(val version: String, val url: String)

  /** Latest published release, or null on network errors. */
  suspend fun fetchLatest(): Release? = withContext(Dispatchers.IO) {
    runCatching {
      val c = URL(API).openConnection() as HttpURLConnection
      c.setRequestProperty("Accept", "application/vnd.github+json")
      c.connectTimeout = 10_000
      c.readTimeout = 10_000
      try {
        if (c.responseCode != 200) return@runCatching null
        val j = json.parseToJsonElement(c.inputStream.bufferedReader().use { it.readText() }) as JsonObject
        val tag = (j["tag_name"] as? JsonPrimitive)?.contentOrNull ?: return@runCatching null
        if ((j["draft"] as? JsonPrimitive)?.booleanOrNull == true || (j["prerelease"] as? JsonPrimitive)?.booleanOrNull == true) return@runCatching null
        Release(tag.removePrefix("v").removePrefix("V"), (j["html_url"] as? JsonPrimitive)?.contentOrNull ?: "$REPO_URL/releases/latest")
      } finally {
        c.disconnect()
      }
    }.getOrNull()
  }

  /** At most once a day, when enabled; notifies once per new version. */
  suspend fun maybeCheck(c: Context, title: String, body: (String) -> String) {
    val s = Store.data
    val n = s.settings.notifications
    if (!builtIn || !n.enabled || !n.updates) return
    val checked = s.meta.updateCheckedAt
    if (checked != null && System.currentTimeMillis() - checked < DAY_MS) return
    val latest = fetchLatest()
    Actions.updateMeta { it.copy(updateCheckedAt = System.currentTimeMillis()) }
    if (latest == null || !isNewer(latest.version, current) || s.meta.updateNotified == latest.version) return
    Reminders.show(c, "update-${latest.version}", title, body(latest.version), latest.url)
    Actions.updateMeta { it.copy(updateNotified = latest.version) }
  }
}

// ---- Health Connect (read-only daily step totals written by any app) --------------------

object HealthConnect {
  /** unavailable | update | noPermission | connected */
  val STEPS_READ = HealthPermission.getReadPermission(StepsRecord::class)

  suspend fun status(c: Context): String = try {
    when (HealthConnectClient.getSdkStatus(c)) {
      HealthConnectClient.SDK_UNAVAILABLE -> "unavailable"
      HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "update"
      else -> {
        val granted = HealthConnectClient.getOrCreate(c).permissionController.getGrantedPermissions()
        if (STEPS_READ in granted) "connected" else "noPermission"
      }
    }
  } catch (e: Exception) {
    Log.w("WellnessHC", "status failed", e)
    "unavailable"
  }

  fun install(c: Context) {
    try {
      c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
      openUrl(c, "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
    }
  }

  fun openSettings(c: Context) {
    try {
      c.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
      install(c)
    }
  }

  /**
   * Pull daily step totals for the last [days] days (today included) into the store.
   * Health Connect de-duplicates overlapping sources itself in aggregate queries.
   */
  suspend fun syncSteps(c: Context, days: Int = 14): Boolean {
    if (status(c) != "connected") return false
    val today = LocalDate.now()
    val result = try {
      withContext(Dispatchers.IO) {
        HealthConnectClient.getOrCreate(c).aggregateGroupByPeriod(
          AggregateGroupByPeriodRequest(
            metrics = setOf(StepsRecord.COUNT_TOTAL),
            timeRangeFilter = TimeRangeFilter.between(today.minusDays(days - 1L).atStartOfDay(), today.plusDays(1).atStartOfDay()),
            timeRangeSlicer = Period.ofDays(1),
          ),
        )
      }
    } catch (e: Exception) {
      Log.w("WellnessHC", "step read failed", e)
      return false
    }
    val byDay = HashMap<String, Double>()
    // Days without any record are absent from the result: they count as 0.
    for (i in 0 until days) byDay[today.minusDays(i.toLong()).toString()] = 0.0
    for (r in result) byDay[r.startTime.toLocalDate().toString()] = (r.result[StepsRecord.COUNT_TOTAL] ?: 0L).toDouble().roundToLong().toDouble()
    withContext(Dispatchers.Main) { Actions.setStepsBulk(byDay) }
    return true
  }
}
