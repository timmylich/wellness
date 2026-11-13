package io.github.timmylich.wellness

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.PausableMonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.timmylich.wellness.data.Actions
import io.github.timmylich.wellness.data.Assets
import io.github.timmylich.wellness.data.Store
import io.github.timmylich.wellness.data.ThemeMode
import io.github.timmylich.wellness.i18n.Dict
import io.github.timmylich.wellness.i18n.resolveLang
import io.github.timmylich.wellness.lib.HealthConnect
import io.github.timmylich.wellness.lib.PantryShare
import io.github.timmylich.wellness.lib.Updates
import io.github.timmylich.wellness.screens.AppScreen
import io.github.timmylich.wellness.screens.BackupPrompt
import io.github.timmylich.wellness.screens.DonationPrompt
import io.github.timmylich.wellness.screens.Onboarding
import io.github.timmylich.wellness.steps.StepService
import io.github.timmylich.wellness.steps.StepStore
import io.github.timmylich.wellness.ui.DialogHost
import io.github.timmylich.wellness.ui.LocalDict
import io.github.timmylich.wellness.ui.LocalMotion
import io.github.timmylich.wellness.ui.LocalPalette
import io.github.timmylich.wellness.ui.NavHost
import io.github.timmylich.wellness.ui.currentDict
import io.github.timmylich.wellness.ui.palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.lifecycle.repeatOnLifecycle

class WellnessApp : Application() {
  override fun onCreate() {
    super.onCreate()
    Assets.init(this)
    // Read saved data off the main thread while the first frame is prepared.
    Store.init(this)
    PantryShare.init(this)
  }
}

/**
 * App-wide animation speed. Compose scales every animation (springs and pager flings too) by the
 * system "Animator duration scale", so at 0.5 the app felt twice as fast as the React Native
 * version, whose native springs and pagers ignored the setting. Here the setting can only slow
 * animations down (×1.5, ×2…) or turn them off (×0), never speed them up.
 */
private object AppMotionScale : MotionDurationScale {
  @Volatile var factor = 1f
  override val scaleFactor get() = factor

  fun refresh(c: android.content.Context) {
    val system = android.provider.Settings.Global.getFloat(c.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    factor = if (system == 0f) 0f else maxOf(1f, system)
  }
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    AppMotionScale.refresh(this)
    // Own recomposer, so all animations in the app run with AppMotionScale.
    val clock = PausableMonotonicFrameClock(AndroidUiDispatcher.CurrentThread[MonotonicFrameClock]!!)
    val context = AndroidUiDispatcher.CurrentThread + clock + AppMotionScale
    val recomposer = Recomposer(context)
    lifecycleScope.launch(context) { recomposer.runRecomposeAndApplyChanges() }
    lifecycle.addObserver(
      LifecycleEventObserver { _, e ->
        when (e) {
          Lifecycle.Event.ON_START -> clock.resume()
          Lifecycle.Event.ON_STOP -> clock.pause()
          Lifecycle.Event.ON_DESTROY -> recomposer.cancel()
          else -> {}
        }
      },
    )
    setContent(recomposer) { Root() }
  }

  override fun onResume() {
    super.onResume()
    AppMotionScale.refresh(this)
  }

  override fun onStop() {
    super.onStop()
    // The process may be killed in the background: write pending changes now.
    Store.flush()
  }
}

@Composable
private fun Root() {
  val ctx = LocalContext.current
  val s = Store.data.settings
  val dark = when (s.theme) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Dark -> true
    ThemeMode.Light -> false
  }
  val pal = remember(dark, s.amoled, s.accent) { palette(dark, s.amoled, s.accent) }
  val dict = remember(s.language) { Dict.of(ctx, resolveLang(s.language)) }
  currentDict = dict
  val activity = ctx as ComponentActivity
  // Only when the colours change (Root recomposes on every data change).
  LaunchedEffect(dark, pal.bg) {
    val bar = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
    activity.enableEdgeToEdge(bar, bar)
    activity.window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(pal.bg.toArgb()))
  }
  CompositionLocalProvider(LocalPalette provides pal, LocalDict provides dict, LocalMotion provides s.animations) {
    Box(Modifier.fillMaxSize().background(pal.bg)) {
      if (!Store.ready) return@Box
      val meta = Store.data.meta
      StepTracking(meta.onboarded && s.section("activity"), s.stepsSource)
      LaunchedEffect(Unit) { Updates.maybeCheck(ctx, dict["settings.updateTitle"]) { dict.t("settings.updateBody", it) } }
      if (!meta.onboarded) Onboarding() else NavHost { AppScreen(it) }
      DonationPrompt()
      BackupPrompt()
      DialogHost()
    }
  }
}

/**
 * Step tracking while the app runs (port of useStepTracking): the foreground service counts
 * while the app is closed; here we drain its buckets on resume, on new sensor events and every
 * minute. With Health Connect the daily totals are pulled on resume and every 5 minutes; while
 * Health Connect is missing or not connected the device counter is used instead.
 */
@Composable
private fun StepTracking(enabled: Boolean, source: String) {
  val ctx = LocalContext.current
  val dict = io.github.timmylich.wellness.ui.L
  val drain = remember {
    {
      val days = StepStore.drain(ctx).mapValues { Math.round(it.value).toDouble() }.filterValues { it > 0 }
      if (days.isNotEmpty()) Actions.addSteps(days)
    }
  }
  val start = remember(dict) {
    { StepService.start(ctx, dict["activity.serviceTitle"], dict["activity.serviceText"]) }
  }
  val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) start() }
  LaunchedEffect(enabled) {
    if (!enabled) {
      drain()
      StepService.stop(ctx)
      return@LaunchedEffect
    }
    if (android.os.Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) start()
    else permission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
  }
  LifecycleResumeEffect(enabled, source) {
    if (!enabled) return@LifecycleResumeEffect onPauseOrDispose {}
    val main = android.os.Handler(android.os.Looper.getMainLooper())
    // New buckets arrive on the sensor thread; drain on the main thread, batched.
    val pendingDrain = Runnable { drain() }
    StepStore.onNewSteps = { main.removeCallbacks(pendingDrain); main.postDelayed(pendingDrain, 500) }
    // A live, unbatched registration while the app is open, so the service's listener (and the
    // screen) gets steps without the batching delay.
    val sm = ctx.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager
    val live = object : android.hardware.SensorEventListener {
      override fun onSensorChanged(event: android.hardware.SensorEvent) {}
      override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
    }
    StepService.stepSensor(sm)?.let { sm.registerListener(live, it, android.hardware.SensorManager.SENSOR_DELAY_NORMAL, 0); sm.flush(live) }
    drain()
    onPauseOrDispose {
      StepStore.onNewSteps = null
      main.removeCallbacks(pendingDrain)
      sm.unregisterListener(live)
    }
  }
  val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
  LaunchedEffect(enabled, source) {
    if (!enabled) return@LaunchedEffect
    // Only while on screen; the service keeps counting meanwhile.
    lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
      while (isActive) {
        if (source != "healthConnect" || !HealthConnect.syncSteps(ctx)) drain()
        delay(if (source == "healthConnect") 5 * 60_000L else 60_000L)
      }
    }
  }
}
