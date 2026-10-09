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

  override fun definition() = ModuleDefinition {
    Name("WellnessNative")

    Events("onStepCounter")

    Function("hasStepCounter") {
      sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
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
      val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
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
      val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return@Function false
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
