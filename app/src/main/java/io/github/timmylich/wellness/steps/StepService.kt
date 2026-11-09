package io.github.timmylich.wellness.steps

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
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
import io.github.timmylich.wellness.R
import org.json.JSONObject
import java.time.LocalDate

/**
 * Foreground service that keeps the hardware step counter registered while the app is closed
 * (moved from modules/wellness-native). On many phones (seen on Xiaomi/Qualcomm) the counter only
 * counts while some client holds a registration; a batched registration held by a low-priority
 * foreground service works without Play services and without battery exemptions.
 *
 * Steps are bucketed per local day in SharedPreferences and drained by the app (StepStore.drain),
 * so every step is credited exactly once.
 */
class StepService : Service(), SensorEventListener {
  private var registered = false

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    intent?.getStringExtra(EXTRA_TITLE)?.let { StepStore.setTexts(this, it, intent.getStringExtra(EXTRA_TEXT) ?: "") }
    try {
      val n = buildNotification()
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
      else startForeground(NOTIFICATION_ID, n)
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
    val sensor = stepSensor(sm) ?: run {
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
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, title, NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) })
    // Tapping opens the channel settings, where it can be switched off; the service keeps
    // counting (Android only requires the notification to exist, not to be shown).
    val pi = PendingIntent.getActivity(this, 0, channelSettingsIntent(this), PendingIntent.FLAG_IMMUTABLE)
    return Notification.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.wellness_notification)
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
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_TEXT = "text"

    /**
     * Prefer the wake-up variant: on some devices the non-wake-up one keeps re-delivering a stale
     * cached value and never updates.
     */
    fun stepSensor(sm: SensorManager): Sensor? =
      sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER, true) ?: sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    fun start(context: Context, title: String?, text: String?) {
      StepStore.setEnabled(context, true)
      val i = Intent(context, StepService::class.java)
      if (title != null) i.putExtra(EXTRA_TITLE, title).putExtra(EXTRA_TEXT, text ?: "")
      context.startForegroundService(i)
    }

    fun stop(context: Context) {
      StepStore.setEnabled(context, false)
      context.stopService(Intent(context, StepService::class.java))
    }

    fun channelSettingsIntent(c: Context): Intent =
      Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, c.packageName)
        .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  }
}

/** Persistent step bookkeeping shared by the service and the app. */
object StepStore {
  private const val PREFS = "wellness_steps"
  private const val SAME_BOOT_TOLERANCE_MS = 120_000L

  /** Called on every new step bucket (on the sensor thread); the app drains then. */
  @Volatile var onNewSteps: (() -> Unit)? = null

  private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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
    val added = delta >= 1 && delta <= 100_000
    if (added) {
      val pending = JSONObject(p.getString("pending", "{}")!!)
      val day = LocalDate.now().toString()
      pending.put(day, pending.optDouble(day, 0.0) + delta)
      e.putString("pending", pending.toString())
    }
    e.apply()
    if (added) onNewSteps?.invoke()
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

  fun setTexts(c: Context, title: String, text: String) = prefs(c).edit().putString("title", title).putString("text", text).apply()

  fun texts(c: Context): Pair<String, String> = Pair(prefs(c).getString("title", "Steps")!!, prefs(c).getString("text", "")!!)
}

/** Restarts the step service after a reboot or an app update, if the user has it enabled. */
class StepBootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
    if (!StepStore.enabled(context)) return
    try {
      StepService.start(context, null, null)
    } catch (e: Exception) {
      Log.w("WellnessSteps", "could not restart step service", e)
    }
  }
}
