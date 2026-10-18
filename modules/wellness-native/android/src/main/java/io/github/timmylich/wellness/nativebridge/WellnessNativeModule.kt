package io.github.timmylich.wellness.nativebridge

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import expo.modules.kotlin.Promise
import expo.modules.kotlin.exception.Exceptions
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

/**
 * Android helpers that Expo's SDK does not expose:
 *  - the hardware TYPE_STEP_COUNTER (cumulative steps since boot, counted by the sensor hub
 *    even while the app is closed): one-shot reads and a live subscription;
 *  - the system "animator duration scale" developer setting, so JS animations can follow it.
 *
 * Note: the step counter is an on-change sensor. Many devices only report when a new step is
 * taken, so a one-shot read may time out while the phone is still; we call flush() to ask the
 * sensor hub to deliver its current value and wait a few seconds.
 */
class WellnessNativeModule : Module() {
  private val context: Context
    get() = appContext.reactContext ?: throw Exceptions.ReactContextLost()

  private val sensorManager: SensorManager
    get() = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

  private var watcher: SensorEventListener? = null

  /**
   * Prefer the wake-up variant of the step counter. On some devices (seen on Xiaomi/Qualcomm)
   * the non-wake-up variant keeps re-delivering a stale cached value and never updates.
   */
  private fun stepSensor(sm: SensorManager): Sensor? =
    sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER, true) ?: sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

  override fun definition() = ModuleDefinition {
    Name("WellnessNative")

    Events("onStepCounter")

    View(NestedScrollHost::class) {
      Prop("mode") { view: NestedScrollHost, mode: String -> view.mode = mode }
    }

    Function("hasStepCounter") {
      stepSensor(sensorManager) != null
    }

    /** Milliseconds since boot, to detect reboots (the counter resets on reboot). */
    Function("elapsedRealtime") {
      SystemClock.elapsedRealtime().toDouble()
    }

    Function("animatorDurationScale") {
      Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f).toDouble()
    }

    /**
     * Resolves with the current cumulative step count since boot, or null if the sensor is
     * missing or does not report within the timeout. Requires ACTIVITY_RECOGNITION (Android 10+).
     */
    AsyncFunction("readStepCounter") { timeoutMs: Int, promise: Promise ->
      val sm = sensorManager
      val sensor = stepSensor(sm)
      if (sensor == null) {
        promise.resolve(null)
        return@AsyncFunction
      }
      val handler = Handler(Looper.getMainLooper())
      var done = false
      lateinit var listener: SensorEventListener
      val finish = { value: Double? ->
        if (!done) {
          done = true
          sm.unregisterListener(listener)
          promise.resolve(value)
        }
      }
      listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
          finish(event.values[0].toDouble())
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
      }
      handler.post {
        // maxReportLatency 0: ask for immediate reports instead of batched ones.
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, 0, handler)
        sm.flush(listener)
      }
      handler.postDelayed({ finish(null) }, timeoutMs.toLong())
    }

    /** Live updates while the app is open: emits `onStepCounter` with { steps } on every change. */
    Function("startWatching") {
      if (watcher != null) return@Function true
      val sm = sensorManager
      val sensor = stepSensor(sm) ?: return@Function false
      val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
          sendEvent("onStepCounter", mapOf("steps" to event.values[0].toDouble()))
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
      }
      watcher = listener
      Handler(Looper.getMainLooper()).post {
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, 0)
        sm.flush(listener)
      }
      true
    }

    /** Start the foreground step service (see StepService) and remember to restart it on boot. */
    Function("startStepService") { title: String, text: String ->
      StepStore.setEnabled(context, true)
      runCatching { StepService.start(context, title, text) }.isSuccess
    }

    Function("stopStepService") {
      StepStore.setEnabled(context, false)
      StepService.stop(context)
    }

    /** Steps per local day recorded by the service since the previous call; clears them. */
    /** Opens the system settings of the step-counter notification channel (to hide it). */
    Function("openStepNotificationSettings") {
      context.startActivity(StepService.channelSettingsIntent(context))
    }

    Function("drainSteps") {
      StepStore.drain(context)
    }

    /** Local notifications (see Reminders). `url` opens a link instead of the app. */
    Function("notify") { id: Int, title: String, body: String, url: String? ->
      Reminders.show(context, id, title, body, url)
    }

    Function("scheduleNotification") { id: Int, atMs: Double, title: String, body: String ->
      Reminders.schedule(context, id, atMs.toLong(), title, body)
    }

    Function("cancelNotification") { id: Int ->
      Reminders.cancel(context, id)
    }

    Function("stopWatching") {
      watcher?.let { sensorManager.unregisterListener(it) }
      watcher = null
    }

    OnDestroy {
      watcher?.let { runCatching { sensorManager.unregisterListener(it) } }
      watcher = null
    }
  }
}
