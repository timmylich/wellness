/**
 * Shared pantry between phones ("family code") through Supabase, without accounts.
 *
 * The 16-character code is the only secret and never leaves the phones: the server keeps
 * AES-256-GCM ciphertext under a SHA-256 id of the code, so it sees neither products nor the code.
 * Each sync is a 3-way merge (the list as last synced, this phone, the server) written back with an
 * optimistic version check. Server side and its limits: supabase/pantry-share.sql.
 *
 * State lives in its own SharedPreferences, not in AppData: a backup sent to a chat must not carry
 * the code.
 */
package io.github.timmylich.wellness.lib

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.timmylich.wellness.BuildConfig
import io.github.timmylich.wellness.data.Actions
import io.github.timmylich.wellness.data.PantryItem
import io.github.timmylich.wellness.data.PantryOutcome
import io.github.timmylich.wellness.data.ShoppingItem
import io.github.timmylich.wellness.data.Store
import io.github.timmylich.wellness.data.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** [key] is an i18n key under `pantry.share`. */
class ShareError(val key: String) : Exception(key)

/** A phone that joined the list; each phone writes only its own entry. */
@Serializable
data class Member(val id: String, val name: String, val changedAt: Long = 0, val change: String = "")

/** What a family code shares: the pantry, the shopping list, who takes part and the eaten / thrown-away history. */
@Serializable
data class Shared(
  val p: List<PantryItem> = emptyList(),
  val s: List<ShoppingItem> = emptyList(),
  val m: List<Member> = emptyList(),
  val l: List<PantryOutcome> = emptyList(),
)

object PantryShare {
  private val api = BuildConfig.SUPABASE_URL.trimEnd('/')
  private val apiKey = BuildConfig.SUPABASE_KEY
  val available = api.startsWith("https://") && apiKey.isNotEmpty()

  /**
   * Without local edits, the server is asked at most this often by default. The open pantry tab
   * polls more often (see Pantry.kt); edits are pushed as soon as they settle.
   */
  const val MIN_INTERVAL_MS = 5 * 60_000L
  /** Server limit is 120 KB; base64 of the ciphertext must fit. */
  private const val MAX_CHARS = 120_000

  // Crockford base32: no I, L, O, U, so the code survives being read aloud or retyped.
  private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
  private val random = SecureRandom()
  private val listSer = ListSerializer(PantryItem.serializer())

  /** Reads [Shared]; the first format (a bare pantry array) is still accepted. */
  internal fun decode(text: String): Shared =
    if (text.trimStart().startsWith("[")) Shared(json.decodeFromString(listSer, text)) else json.decodeFromString(Shared.serializer(), text)

  private lateinit var prefs: SharedPreferences

  /** Connected code (normalized), or null. */
  var code by mutableStateOf<String?>(null)
    private set
  /** The server dropped the list after 7 idle days; shown once in the pantry. */
  var expired by mutableStateOf(false)
    private set
  var syncedAt by mutableLongStateOf(0L)
    private set
  var busy by mutableStateOf(false)
    private set
  /** Last background sync error key, cleared by a successful sync. */
  var error by mutableStateOf<String?>(null)
    private set
  /** Phones on the list, as of the last sync. */
  var members by mutableStateOf<List<Member>>(emptyList())
    private set
  /** This phone's name for the others. */
  var name by mutableStateOf("")
    private set
  private var version = 0
  private var base = Shared()
  private var deviceId = ""
  val me get() = deviceId

  fun init(c: Context) {
    prefs = c.getSharedPreferences("pantry-share", Context.MODE_PRIVATE)
    code = prefs.getString("code", null)
    expired = prefs.getBoolean("expired", false)
    syncedAt = prefs.getLong("syncedAt", 0)
    version = prefs.getInt("version", 0)
    name = prefs.getString("name", "").orEmpty()
    deviceId = prefs.getString("device", null) ?: newCode().also { prefs.edit().putString("device", it).apply() }
    base = runCatching { decode(prefs.getString("base", "{}")!!) }.getOrDefault(Shared())
    members = base.m
  }

  // ---- code and crypto -------------------------------------------------------------------

  /** 16 symbols × 5 bits = 80 random bits. */
  fun newCode(): String = String(CharArray(16) { ALPHABET[random.nextInt(32)] })

  /** Accepts dashes, spaces, lower case and look-alikes (O→0, I/L→1); null if not a code. */
  fun normalize(input: String): String? {
    val s = input.uppercase().map { when (it) { 'O' -> '0'; 'I', 'L' -> '1'; else -> it } }.filter { it in ALPHABET }
    return s.joinToString("").takeIf { it.length == 16 && s.size == input.count { ch -> ch.isLetterOrDigit() } }
  }

  fun pretty(code: String) = code.chunked(4).joinToString("-")

  private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())

  internal fun idOf(code: String) = sha256("wellness-pantry-id:$code").joinToString("") { "%02x".format(it) }

  private fun cipher(mode: Int, code: String, iv: ByteArray) = Cipher.getInstance("AES/GCM/NoPadding").apply {
    init(mode, SecretKeySpec(sha256("wellness-pantry-key:$code"), "AES"), GCMParameterSpec(128, iv))
    // Binds the ciphertext to its id: a row copied under another id does not decrypt.
    updateAAD(idOf(code).toByteArray())
  }

  internal fun seal(code: String, items: Shared): String {
    val iv = ByteArray(12).also(random::nextBytes)
    // Compressed before encryption (ciphertext does not compress): JSON shrinks several times.
    val zipped = java.io.ByteArrayOutputStream().also { b -> java.util.zip.GZIPOutputStream(b).use { it.write(json.encodeToString(Shared.serializer(), items).toByteArray()) } }.toByteArray()
    val out = Base64.getEncoder().encodeToString(iv + cipher(Cipher.ENCRYPT_MODE, code, iv).doFinal(zipped))
    if (out.length > MAX_CHARS) throw ShareError("tooBig")
    return out
  }

  internal fun open(code: String, data: String): Shared = try {
    val raw = Base64.getDecoder().decode(data)
    val plain = cipher(Cipher.DECRYPT_MODE, code, raw.copyOfRange(0, 12)).doFinal(raw, 12, raw.size - 12)
    decode(java.util.zip.GZIPInputStream(plain.inputStream()).use { String(it.readBytes()) })
  } catch (e: Exception) {
    throw ShareError("corrupt")
  }

  // ---- merge ------------------------------------------------------------------------------

  /**
   * 3-way merge by id against [base], the list both sides last agreed on. A change or deletion
   * on one side wins over an untouched item; when both sides changed an item, this phone's
   * version wins, and an edit beats a deletion.
   */
  internal fun <T> mergeList(base: List<T>, local: List<T>, remote: List<T>, id: (T) -> String): List<T> {
    val b = base.associateBy(id)
    val l = local.associateBy(id)
    val r = remote.associateBy(id)
    return (l.keys + r.keys).mapNotNull { id ->
      val (o, x, y) = Triple(b[id], l[id], r[id])
      when {
        x == y -> x
        x == o -> y
        y == o -> x
        else -> x ?: y
      }
    }
  }

  internal fun merge(base: Shared, local: Shared, remote: Shared) =
    Shared(mergeList(base.p, local.p, remote.p) { it.id }, mergeList(base.s, local.s, remote.s) { it.id }, mergeList(base.m, local.m, remote.m) { it.id }, mergeList(base.l, local.l, remote.l) { it.id })

  private fun <T> same(a: List<T>, b: List<T>) = a.size == b.size && a.toSet() == b.toSet()

  /** Same products and shopping list (what this phone stores). */
  private fun sameData(a: Shared, b: Shared) = same(a.p, b.p) && same(a.s, b.s) && same(a.l, b.l)

  private fun same(a: Shared, b: Shared) = sameData(a, b) && same(a.m, b.m)

  /** This phone's lists; members are not stored locally, so they come from the last sync. */
  private fun local() = Shared(Store.data.pantry, Store.data.shopping, base.m, Store.data.pantryLog)

  /** Short, language-neutral summary of this phone's edits since [b]: "+ Milk, − Bread, ✎ Cheese, 🛒 Eggs, ✓ Butter". */
  internal fun summary(b: Shared, l: Shared): String {
    val bp = b.p.associateBy { it.id }
    val lp = l.p.associateBy { it.id }
    val bs = b.s.associateBy { it.id }
    val bl = b.l.mapTo(HashSet()) { it.id }
    val parts = buildList {
      for (x in l.p) bp[x.id].let { o -> if (o == null) add("+ ${x.name}") else if (o != x) add("✎ ${x.name}") }
      // A product that was eaten or thrown away shows up below as 🍽 / 🗑, not as removed.
      val logged = l.l.filter { it.id !in bl }.mapTo(HashSet()) { it.name }
      for (x in b.p) if (x.id !in lp && x.name !in logged) add("− ${x.name}")
      for (x in l.s) bs[x.id].let { o ->
        if (o == null) add("🛒 ${x.name}") else if (o.boughtAt == null && x.boughtAt != null) add("✓ ${x.name}")
      }
      for (x in l.l) if (x.id !in bl) add((if (x.kind == "wasted") "🗑 " else "🍽 ") + x.name)
    }
    return parts.take(4).joinToString(", ") + if (parts.size > 4) " …" else ""
  }

  /** [v] with this phone's member entry added or updated ([change]: a new summary, or null to keep the old one). */
  private fun withMe(v: Shared, change: String?): Shared {
    val old = v.m.firstOrNull { it.id == deviceId }
    val nm = name.ifBlank { defaultName() }
    if (old != null && change == null && old.name == nm) return v
    val entry = Member(deviceId, nm, if (change != null) System.currentTimeMillis() else old?.changedAt ?: 0, change ?: old?.change.orEmpty())
    return v.copy(m = v.m.filterNot { it.id == deviceId } + entry)
  }

  private fun defaultName() = Store.data.profile.name.ifBlank { android.os.Build.MODEL ?: "Android" }

  /** This phone's name on a shared list, or null when not sharing (history entries record it). */
  fun author(): String? = if (code == null) null else name.ifBlank { defaultName() }

  /** Name shown to the others; sent with the next sync. */
  fun rename(n: String) {
    name = n.trim().take(40)
    prefs.edit().putString("name", name).apply()
  }

  private fun apply(v: Shared) = Actions.setShared(v.p, v.s, v.l)

  // ---- server -----------------------------------------------------------------------------

  private suspend fun rpc(fn: String, body: Map<String, Any>): String = withContext(Dispatchers.IO) {
    val c = URL("$api/rest/v1/rpc/$fn").openConnection() as HttpURLConnection
    try {
      c.requestMethod = "POST"
      c.connectTimeout = 15_000
      c.readTimeout = 15_000
      c.doOutput = true
      c.setRequestProperty("Content-Type", "application/json")
      c.setRequestProperty("apikey", apiKey)
      // Legacy anon keys are JWTs and go in Authorization too; new publishable keys must not.
      if (apiKey.startsWith("eyJ")) c.setRequestProperty("Authorization", "Bearer $apiKey")
      val payload = JsonObject(body.mapValues { (_, v) -> if (v is Number) JsonPrimitive(v) else JsonPrimitive(v.toString()) })
      c.outputStream.use { it.write(payload.toString().toByteArray()) }
      val status = c.responseCode
      val text = (if (status < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
      if (status >= 400) throw ShareError(
        when {
          status == 429 || "rate_limited" in text -> "rateLimited"
          "\"message\":\"full\"" in text -> "full"
          else -> "server"
        },
      )
      text
    } catch (e: java.io.IOException) {
      throw ShareError("network")
    } finally {
      c.disconnect()
    }
  }

  class Remote(val items: Shared, val version: Int)

  /**
   * The shared list, or null when the code is unknown or expired. When the server is still at
   * [known] (the synced version), it sends no data and [base] is returned instead.
   */
  suspend fun fetch(code: String, known: Int = 0): Remote? {
    val rows = json.parseToJsonElement(rpc("pantry_share_get", mapOf("p_id" to idOf(code), "p_version" to known))) as? JsonArray ?: throw ShareError("server")
    val row = rows.firstOrNull() as? JsonObject ?: return null
    val v = row["version"]?.jsonPrimitive?.int ?: throw ShareError("server")
    val data = (row["data"] as? JsonPrimitive)?.takeIf { it.isString }?.content
      ?: return if (known > 0 && v == known) Remote(base, v) else throw ShareError("server")
    return Remote(withContext(Dispatchers.Default) { open(code, data) }, v)
  }

  private fun save(code: String?, base: Shared, version: Int) {
    this.code = code
    this.base = base
    this.version = version
    members = base.m
    syncedAt = if (code == null) 0 else System.currentTimeMillis()
    error = null
    prefs.edit().putString("code", code).putString("base", json.encodeToString(Shared.serializer(), base)).putInt("version", version).putLong("syncedAt", syncedAt).apply()
  }

  /** Start sharing this phone's list under a new code. */
  suspend fun create() {
    val code = newCode()
    val items = withMe(local(), "")
    rpc("pantry_share_create", mapOf("p_id" to idOf(code), "p_data" to seal(code, items)))
    markExpired(false)
    save(code, items, 1)
  }

  /** Join a list fetched with [fetch]: merge it with this phone's list, or replace this phone's. */
  suspend fun join(code: String, remote: Remote, replace: Boolean) {
    apply(if (replace) remote.items else merge(Shared(m = remote.items.m), local().copy(m = remote.items.m), remote.items))
    markExpired(false)
    save(code, remote.items, remote.version)
    sync(force = true)
  }

  /** Stop syncing on this phone; the shared list stays for the others. */
  fun leave() = save(null, Shared(), 0)

  fun dismissExpired() = markExpired(false)

  private fun markExpired(v: Boolean) {
    expired = v
    prefs.edit().putBoolean("expired", v).apply()
  }

  /**
   * Bring this phone and the server to the same list. Skips the network when nothing changed
   * locally and the last sync is recent, unless [force]. Returns true if the local list changed.
   * Call on the main thread. Errors go to [error] (or are thrown when [force]).
   */
  suspend fun sync(force: Boolean = false, maxAgeMs: Long = MIN_INTERVAL_MS): Boolean {
    val code = code ?: return false
    if (busy) return false
    // A rename also has to go out, even without other edits.
    val meCurrent = base.m.any { it.id == deviceId && it.name == name.ifBlank { defaultName() } }
    if (!force && meCurrent && same(local(), base) && System.currentTimeMillis() - syncedAt < maxAgeMs) return false
    busy = true
    var changed = false
    try {
      // A concurrent write from another phone means: read, merge and try again.
      repeat(3) {
        val remote = fetch(code, version) ?: return changed.also { gone() }
        val loc = local()
        val edited = !sameData(loc, base)
        val merged = withMe(merge(base, loc, remote.items), if (edited) summary(base, loc) else null)
        if (!sameData(merged, loc)) {
          apply(merged)
          changed = true
        }
        if (same(merged, remote.items)) return changed.also { save(code, remote.items, remote.version) }
        when (val v = rpc("pantry_share_put", mapOf("p_id" to idOf(code), "p_data" to seal(code, merged), "p_version" to remote.version)).trim().toIntOrNull()) {
          null -> throw ShareError("server")
          0 -> return changed.also { gone() }
          -1 -> {}
          else -> return changed.also { save(code, merged, v) }
        }
      }
      throw ShareError("server")
    } catch (e: ShareError) {
      error = e.key
      if (force) throw e
      return changed
    } finally {
      busy = false
    }
  }

  private fun gone() {
    save(null, Shared(), 0)
    markExpired(true)
  }
}
