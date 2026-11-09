/**
 * Global state: one immutable [AppData] in a Compose state, so any composable that reads it
 * recomposes on change. Saved as JSON to the app's files folder, debounced and written on a
 * background thread (atomic: temp file + rename). Mutate only through Actions.kt, on the main thread.
 */
package io.github.timmylich.wellness.data

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import io.github.timmylich.wellness.lib.todayKey
import java.io.File
import kotlin.random.Random

object Store {
  var data: AppData by mutableStateOf(AppData(), referentialEqualityPolicy())
    private set

  /** False until the saved file has been read (the UI waits for it). */
  var ready by mutableStateOf(false)
    private set

  private lateinit var file: File
  private val io = Handler(HandlerThread("wellness-store").apply { start() }.looper)
  private val main = Handler(android.os.Looper.getMainLooper())
  private val saveTask = Runnable { write(data) }

  /** Reads saved data off the main thread; [ready] flips when done. */
  fun init(context: Context) {
    file = File(context.filesDir, "data.json")
    io.post {
      // First start after updating from the React Native version: take over its data.
      val migrated = if (file.exists()) null else readReactNativeData(context)
      val loaded = try {
        val text = if (file.exists()) file.readText() else migrated
        text?.let { json.decodeFromString<AppData>(it).normalized(Assets.builtinWorkouts(context)) }
      } catch (e: Exception) {
        Log.w(TAG, "could not read data, starting fresh", e)
        // Keep the unreadable file for recovery instead of overwriting it.
        file.copyTo(File(context.filesDir, "data.broken.json"), overwrite = true)
        null
      }
      val start = loaded ?: createDefaultData(context)
      main.post {
        data = start
        ready = true
        recordUsageDay()
        if (migrated != null) flush()
      }
    }
  }

  /**
   * The React Native app kept everything as one JSON string in AsyncStorage
   * (SQLite database "RKStorage", key "wellness/data"), in the same format as data.json.
   */
  private fun readReactNativeData(c: Context): String? = try {
    val db = c.getDatabasePath("RKStorage")
    if (!db.exists()) null
    else android.database.sqlite.SQLiteDatabase.openDatabase(db.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use {
      it.rawQuery("SELECT value FROM catalystLocalStorage WHERE key = ?", arrayOf("wellness/data")).use { cur -> if (cur.moveToFirst()) cur.getString(0) else null }
    }
  } catch (e: Exception) {
    Log.w(TAG, "could not read React Native data", e)
    null
  }

  fun createDefaultData(context: Context) = AppData(workoutTemplates = Assets.builtinWorkouts(context))

  /** Apply an immutable update; `fn` returns a new object (or the same one for a no-op). */
  fun update(fn: (AppData) -> AppData) {
    val next = fn(data)
    if (next === data) return
    data = next
    io.removeCallbacks(saveTask)
    io.postDelayed(saveTask, 300)
  }

  fun replace(next: AppData) = update { next }

  /** Write pending changes now (the app is going to the background and may be killed). */
  fun flush() {
    io.removeCallbacks(saveTask)
    val snapshot = data
    if (ready) io.post { write(snapshot) }
  }

  private fun write(d: AppData) {
    if (!ready) return
    try {
      val tmp = File(file.parentFile, "data.json.tmp")
      tmp.writeText(json.encodeToString(AppData.serializer(), d))
      if (!tmp.renameTo(file)) {
        file.delete()
        tmp.renameTo(file)
      }
    } catch (e: Exception) {
      Log.w(TAG, "failed to persist data", e)
    }
  }

  private fun recordUsageDay() {
    val today = todayKey()
    if (today !in data.meta.usageDays) update { it.copy(meta = it.meta.copy(usageDays = it.meta.usageDays + today)) }
  }

  private const val TAG = "WellnessStore"
}

/** Short random id; collision-safe enough for local single-user data. */
fun uid(): String = System.currentTimeMillis().toString(36) + Random.nextLong(36L * 36 * 36 * 36 * 36 * 36).toString(36).padStart(6, '0')
