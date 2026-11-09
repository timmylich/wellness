/**
 * Export / import of all data as one JSON file: AppData plus
 * health-record photos as base64 under `photoFiles`, keyed by file name. Backups made by the
 * React Native versions (0.5.x) import unchanged (and back). Also imports "food packs" (docs/food-packs.md).
 * Errors are thrown with i18n keys from `settings` (notBackup, noFoods).
 */
package io.github.timmylich.wellness.lib

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.timmylich.wellness.data.AppData
import io.github.timmylich.wellness.data.Assets
import io.github.timmylich.wellness.data.Food
import io.github.timmylich.wellness.data.HealthRecord
import io.github.timmylich.wellness.data.Store
import io.github.timmylich.wellness.data.json
import io.github.timmylich.wellness.data.normalized
import io.github.timmylich.wellness.data.uid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File

class BackupError(val key: String) : Exception(key)

object Backup {
  fun fileName() = "wellness-backup-${todayKey()}.json"

  /** Writes the backup to a document the user picked (CreateDocument). */
  suspend fun export(c: Context, target: Uri) = withContext(Dispatchers.IO) {
    val data = Store.data
    val photos = HashMap<String, JsonPrimitive>()
    for (uri in data.health.flatMap { it.photos }) {
      val f = Photos.file(uri) ?: continue
      if (f.exists()) photos[Photos.name(uri)] = JsonPrimitive(Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
    }
    val obj = json.encodeToJsonElement(AppData.serializer(), data).jsonObject
    val text = JsonObject(obj + ("photoFiles" to JsonObject(photos))).toString()
    c.contentResolver.openOutputStream(target, "wt")!!.use { it.write(text.toByteArray()) }
  }

  private fun readJson(c: Context, uri: Uri) =
    json.parseToJsonElement(c.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })

  /** Parses a backup and returns the data to replace the current state with (photos restored). */
  suspend fun import(c: Context, uri: Uri): AppData = withContext(Dispatchers.IO) {
    val root = runCatching { readJson(c, uri) }.getOrNull() as? JsonObject ?: throw BackupError("notBackup")
    if ("profile" !in root || "settings" !in root) throw BackupError("notBackup")
    val files = (root["photoFiles"] as? JsonObject).orEmpty()
    val data = runCatching { json.decodeFromJsonElement(AppData.serializer(), JsonObject(root - "photoFiles")) }
      .getOrElse { throw BackupError("notBackup") }
      .normalized(Assets.builtinWorkouts(c))
    data.copy(health = Photos.restore(c, data.health, files))
  }

  /** Merges a food pack into the local database; returns the number of foods added or updated. */
  suspend fun importFoodPack(c: Context, uri: Uri): List<Food> = withContext(Dispatchers.IO) {
    val root = runCatching { readJson(c, uri) }.getOrNull() as? JsonObject ?: throw BackupError("noFoods")
    val foods = root["foods"] as? JsonArray ?: throw BackupError("noFoods")
    val pack = (root["name"] as? JsonPrimitive)?.contentOrNull ?: "pack"
    foods.mapIndexedNotNull { i, e ->
      val o = e as? JsonObject ?: return@mapIndexedNotNull null
      if (o["name"] == null || o["per100g"] == null) return@mapIndexedNotNull null
      val id = (o["id"] as? JsonPrimitive)?.contentOrNull ?: i.toString()
      runCatching {
        json.decodeFromJsonElement(Food.serializer(), JsonObject(o + mapOf("id" to JsonPrimitive("pack-$pack-$id"), "source" to JsonPrimitive("pack"))))
      }.getOrNull()
    }
  }
}

/**
 * Photos attached to health records, copied into the app's private files folder (files/health)
 * so they survive the user deleting the original from the gallery.
 */
object Photos {
  fun dir(c: Context) = File(c.filesDir, "health").apply { mkdirs() }

  /** File name of a photo URI (the part after the last slash, without a query). */
  fun name(uri: String) = uri.substringBefore('?').substringAfterLast('/')

  fun file(uri: String): File? = Uri.parse(uri).path?.let(::File)

  /** Copy picked images into the app folder; returns their file:// URIs. */
  suspend fun keep(c: Context, uris: List<Uri>): List<String> = withContext(Dispatchers.IO) {
    uris.mapNotNull { src ->
      runCatching {
        val dest = File(dir(c), "${uid()}.jpg")
        c.contentResolver.openInputStream(src)!!.use { input -> dest.outputStream().use { input.copyTo(it) } }
        Uri.fromFile(dest).toString()
      }.getOrNull()
    }
  }

  fun delete(uris: List<String>) {
    for (u in uris) runCatching { file(u)?.delete() }
  }

  /**
   * Write a backup's photos into the folder, point the records at them (backups from another
   * phone carry old paths) and delete photos nothing refers to any more.
   */
  fun restore(c: Context, records: List<HealthRecord>, files: Map<String, kotlinx.serialization.json.JsonElement>): List<HealthRecord> {
    val dir = dir(c)
    for ((name, b64) in files) {
      // Names are generated ids; refuse anything that could leave the folder.
      if (!Regex("""^[\w.-]+$""").matches(name)) continue
      val text = (b64 as? JsonPrimitive)?.contentOrNull ?: continue
      runCatching { File(dir, name).writeBytes(Base64.decode(text, Base64.DEFAULT)) }
    }
    val relinked = records.map { r ->
      r.copy(photos = r.photos.mapNotNull { p -> File(dir, name(p)).takeIf { it.exists() }?.let { Uri.fromFile(it).toString() } })
    }
    val used = relinked.flatMap { r -> r.photos.map(::name) }.toSet()
    dir.listFiles()?.forEach { if (it.name !in used) it.delete() }
    return relinked
  }

  private val thumbs = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
  }

  /** Decodes a photo scaled down to about [maxPx] on its long side (cached). */
  suspend fun load(uri: String, maxPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
    val key = "$uri@$maxPx"
    thumbs.get(key)?.let { return@withContext it }
    val f = file(uri) ?: return@withContext null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, bounds)
    if (bounds.outWidth <= 0) return@withContext null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
    var bmp: Bitmap = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@withContext null
    // Camera photos keep their rotation in EXIF; BitmapFactory ignores it.
    val deg = when (runCatching { android.media.ExifInterface(f.path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)) {
      android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
      android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
      android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
      else -> 0f
    }
    if (deg != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postRotate(deg) }, true)
    bmp.asImageBitmap().also { thumbs.put(key, it) }
  }
}
