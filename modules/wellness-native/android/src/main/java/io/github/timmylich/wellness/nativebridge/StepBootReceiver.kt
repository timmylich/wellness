package io.github.timmylich.wellness.nativebridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Restarts the step service after a reboot or an app update, if the user has it enabled. */
class StepBootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (!StepStore.enabled(context)) return
    try {
      StepService.start(context, null, null)
    } catch (e: Exception) {
      Log.w("WellnessSteps", "could not restart step service", e)
    }
  }
}
