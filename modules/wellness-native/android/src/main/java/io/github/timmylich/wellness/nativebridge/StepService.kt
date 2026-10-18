package io.github.timmylich.wellness.nativebridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground service that keeps the hardware step counter registered while the app is closed.
 *
 * Why: on many phones (seen on Xiaomi/Qualcomm) the step counter only counts while some client
 * holds a registration; Google Fit gets one for free through Play services. A one-shot read
 * from a periodic background task therefore sees no steps. A batched registration held by a
 * low-priority foreground service works without Play services and without battery exemptions.
 *
 * Steps are bucketed by local day in SharedPreferences and drained by JS (StepStore.drain), so
 * every step is credited exactly once.
 */
class StepService : Service(), SensorEventListener {
  private var registered = false

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    intent?.getStringExtra(EXTRA_TITLE)?.let { StepStore.setTexts(this, it, intent.getStringExtra(EXTRA_TEXT) ?: "") }
    try {
      val n = buildNotification()
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
      } else {
        startForeground(NOTIFICATION_ID, n)
      }
    } catch (e: Exception) {
      // Missing ACTIVITY_RECOGNITION or a background-start restriction: give up quietly.
      Log.w(TAG, "could not start foreground", e)
      stopSelf()
      return START_NOT_STICKY
    }
    register()
    return START_STICKY
  }

  private fun register() {
    if (registered) return
    val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER, true) ?: sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    if (sensor == null) {
      stopSelf()
      return
    }
    // Batched: the sensor hub buffers steps (FIFO) and wakes us at most every few minutes.
    registered = sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, MAX_LATENCY_US)
    Log.i(TAG, "step counter registered: $registered (wakeUp=${sensor.isWakeUpSensor})")
  }

  override fun onSensorChanged(event: SensorEvent) {
    StepStore.onCounter(this, event.values[0].toDouble())
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

  override fun onDestroy() {
    if (registered) (getSystemService(Context.SENSOR_SERVICE) as SensorManager).unregisterListener(this)
    registered = false
    super.onDestroy()
  }

  private fun buildNotification(): Notification {
    val (title, text) = StepStore.texts(this)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val ch = NotificationChannel(CHANNEL_ID, title, NotificationManager.IMPORTANCE_MIN)
      ch.setShowBadge(false)
      nm.createNotificationChannel(ch)
    }
    // Tapping the notification opens its channel settings, where it can be switched off; the
    // service keeps counting (Android only requires the notification to exist, not to be shown).
    val pi = PendingIntent.getActivity(this, 0, channelSettingsIntent(this), PendingIntent.FLAG_IMMUTABLE)
    @Suppress("DEPRECATION")
    val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
    return b.setSmallIcon(R.drawable.wellness_notification)
      .setContentTitle(title)
      .setContentText(text)
      .setOngoing(true)
      .setContentIntent(pi)
      .build()
  }

  companion object {
    private const val TAG = "WellnessSteps"
    private const val CHANNEL_ID = "wellness-steps"
    private const val NOTIFICATION_ID = 7301
    private const val MAX_LATENCY_US = 5 * 60 * 1_000_000
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"

    fun start(context: Context, title: String?, text: String?) {
      val i = Intent(context, StepService::class.java)
      if (title != null) i.putExtra(EXTRA_TITLE, title).putExtra(EXTRA_TEXT, text ?: "")
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i) else context.startService(i)
    }

    fun channelSettingsIntent(c: Context): Intent =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
          .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, c.packageName)
          .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      } else {
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${c.packageName}"))
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }

    fun stop(context: Context) {
      context.stopService(Intent(context, StepService::class.java))
    }
  }
}

/** Persistent step bookkeeping shared by the service and the JS module. */
object StepStore {
  private const val PREFS = "wellness_steps"
  private const val SAME_BOOT_TOLERANCE_MS = 120_000L

  private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

  @Synchronized
  fun onCounter(c: Context, value: Double) {
    val p = prefs(c)
    val last = p.getFloat("last", -1f).toDouble()
    val bootAt = System.currentTimeMillis() - SystemClock.elapsedRealtime()
    val sameBoot = Math.abs(bootAt - p.getLong("bootAt", 0)) < SAME_BOOT_TOLERANCE_MS
    val delta = when {
      last < 0 -> 0.0 // first reading: baseline only
      value < last -> value // the counter restarted (reboot or re-registration)
      !sameBoot && value < 1000 -> value
      else -> value - last
    }
    val e = p.edit().putFloat("last", value.toFloat()).putLong("bootAt", bootAt)
    if (delta >= 1 && delta <= 100_000) {
      val pending = JSONObject(p.getString("pending", "{}")!!)
      val day = today()
      pending.put(day, pending.optDouble(day, 0.0) + delta)
      e.putString("pending", pending.toString())
    }
    e.apply()
  }

  /** Returns { "YYYY-MM-DD": steps } accumulated since the previous drain and clears it. */
  @Synchronized
  fun drain(c: Context): Map<String, Double> {
    val p = prefs(c)
    val pending = JSONObject(p.getString("pending", "{}")!!)
    p.edit().putString("pending", "{}").apply()
    return pending.keys().asSequence().associateWith { pending.getDouble(it) }
  }

  fun setEnabled(c: Context, on: Boolean) = prefs(c).edit().putBoolean("enabled", on).apply()

  fun enabled(c: Context) = prefs(c).getBoolean("enabled", false)

  fun setTexts(c: Context, title: String, text: String) =
    prefs(c).edit().putString("title", title).putString("text", text).apply()

  fun texts(c: Context): Pair<String, String> =
    Pair(prefs(c).getString("title", "Steps")!!, prefs(c).getString("text", "")!!)
}
