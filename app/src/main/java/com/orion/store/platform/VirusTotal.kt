package com.orion.store.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.orion.store.data.HttpError
import com.orion.store.data.Net
import com.orion.store.data.VtStats
import com.orion.store.data.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLDecoder

enum class VtView { KEY, READY, SCANNING, RESULTS, ERROR }
enum class ScanTargetType { APK, DOWNLOADED_APK, REMOTE_APK }
data class ScanTarget(val type: ScanTargetType, val label: String, val detail: String)
data class VtEngine(val engine: String, val category: String, val result: String?)
data class VtResult(val type: ScanTargetType, val hash: String?, val stats: VtStats, val engines: List<VtEngine>, val fresh: Boolean, val permalink: String?)

private const val VT = "https://www.virustotal.com/api/v3"

private fun parseBody(b: String): JsonObject {
    if (b.isEmpty()) return JsonObject(emptyMap())
    if (b.trimStart().startsWith("<")) throw Exception("VirusTotal returned an HTML page instead of JSON. This usually means the request was blocked by Cloudflare or the API is temporarily unavailable. Try again in a minute.")
    return runCatching { json.parseToJsonElement(b).jsonObject }.getOrElse { throw Exception("VirusTotal returned an unexpected response. The API may be temporarily unavailable. Try again later.") }
}

private fun apiError(status: Int, fallback: String = "VirusTotal request failed.") = when {
    status == 401 || status == 403 -> "API key rejected. Check the key and try again."
    status == 404 -> "No VirusTotal report exists for this target yet."
    status == 429 -> "VirusTotal quota reached. Free keys are commonly limited to 4 requests per minute."
    status >= 500 -> "VirusTotal is having trouble right now. Try again later."
    else -> fallback
}

private fun JsonObject.obj(k: String) = this[k] as? JsonObject ?: JsonObject(emptyMap())
private fun JsonObject.int(k: String) = (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull ?: 0
private fun JsonObject.str(k: String) = (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
private fun stats(o: JsonObject) = VtStats(o.int("harmless"), o.int("malicious"), o.int("suspicious"), o.int("undetected"), o.int("timeout"))
private fun engines(o: JsonObject) = o.map { (k, v) -> v.jsonObject.let { VtEngine(it.str("engine_name") ?: k, it.str("category") ?: "", it.str("result")) } }

/** Port of hooks/useVirusTotalScan.ts. Compose state so sheets observe it directly. */
class VtScan(private val tracker: AppTracker, private val appId: String, private val appName: String, private val packageName: String?, apiKey: String) {
    var view by mutableStateOf(if (apiKey.isNotBlank()) VtView.READY else VtView.KEY)
    var note by mutableStateOf("Preparing scan...")
    var progress by mutableFloatStateOf(0f)
    var result by mutableStateOf<VtResult?>(null)
    var error by mutableStateOf("")

    private suspend fun get(path: String, key: String): Pair<Int, JsonObject> {
        val c = Net.client.newBuilder().connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS).readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS).build()
        return try { 200 to parseBody(Net.text("$VT$path", mapOf("x-apikey" to key), c = c)) } catch (e: HttpError) { e.code to runCatching { parseBody(e.body) }.getOrDefault(JsonObject(emptyMap())) }
    }

    private fun step(n: String, p: Int) { note = n; progress = p / 100f }

    private suspend fun report(hash: String, key: String): VtResult? {
        val (s, b) = get("/files/$hash", key)
        if (s == 404) return null
        if (s !in 200..299) throw Exception(apiError(s))
        val a = b.obj("data").obj("attributes")
        return VtResult(ScanTargetType.APK, hash, stats(a.obj("last_analysis_stats")), engines(a.obj("last_analysis_results")), false, "https://www.virustotal.com/gui/file/$hash")
    }

    private suspend fun poll(id: String, key: String, hash: String): VtResult {
        repeat(18) { i ->
            step(if (i == 0) "Waiting for engines..." else "Collecting engine verdicts...", minOf(94, 58 + i * 2))
            delay(if (i < 3) 5000 else 10000)
            val (s, b) = get("/analyses/$id", key)
            if (s !in 200..299) throw Exception(apiError(s, "Analysis polling failed."))
            val a = b.obj("data").obj("attributes")
            if (a.str("status") == "completed") return VtResult(ScanTargetType.APK, hash, stats(a.obj("stats")), engines(a.obj("results")), true, "https://www.virustotal.com/gui/file/$hash")
        }
        throw Exception("VirusTotal is still analyzing this app. Try opening the VirusTotal report in a minute.")
    }

    private suspend fun scanPath(path: String, key: String): VtResult {
        step("Calculating SHA-256...", 18)
        val hash = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { tracker.hash(path) }
        step("Checking existing VirusTotal report...", 34)
        report(hash, key)?.let { return it }
        step("Uploading APK only because VirusTotal has no report...", 48)
        val (s, body) = tracker.vtUpload(path, key)
        val b = parseBody(body)
        if (s !in 200..299) throw Exception(apiError(s, "Upload failed."))
        val id = b.obj("data").str("id") ?: throw Exception("VirusTotal did not return an analysis id.")
        return poll(id, key, hash)
    }

    private fun tempName(url: String): String {
        var base = "${appId.ifEmpty { appName }}-vt-scan.apk"
        runCatching { URLDecoder.decode(URI(url).path.substringAfterLast('/'), "UTF-8") }.getOrNull()?.takeIf { Regex("\\.(apk|apks|xapk|zip)$", RegexOption.IGNORE_CASE).containsMatchIn(it) }?.let { base = it }
        val safe = base.replace(Regex("[^a-zA-Z0-9._-]"), "_"); val sfx = System.currentTimeMillis().toString(36); val dot = safe.lastIndexOf('.')
        return if (dot > 0) "${safe.take(dot)}_$sfx${safe.substring(dot)}" else "${safe}_$sfx.apk"
    }

    private suspend fun downloadAndScan(url: String, key: String): VtResult {
        val name = tempName(url)
        step("Downloading APK for file scan...", 8)
        try {
            Downloads.start(url, name)
            run wait@{
                repeat(180) {
                    val d = Downloads.status(name)
                    progress = d.progress.coerceIn(8, 40) / 100f
                    if (d.status == DlStatus.SUCCESSFUL) return@wait
                    if (d.status == DlStatus.FAILED) throw Exception("APK download failed before scanning.")
                    delay(1000)
                }
                throw Exception("APK download took too long. Try again on a stronger connection.")
            }
            return scanPath(tracker.resolve(name).absolutePath, key)
        } finally { Downloads.forget(name); runCatching { tracker.deleteFile(name) } }
    }

    fun reset() { result = null; error = ""; progress = 0f; note = "Preparing scan..." }

    /** Returns the result, or null on error. [haptic] receives "success" | "warning" | "error". */
    suspend fun start(target: ScanTarget?, key: String, haptic: (String) -> Unit): VtResult? {
        val k = key.trim()
        if (k.isEmpty()) { view = VtView.KEY; return null }
        if (target == null) { error = "No installed APK, downloaded APK, or direct APK file was found for this app."; view = VtView.ERROR; return null }
        reset(); view = VtView.SCANNING; step("Starting VirusTotal scan...", 8)
        return try {
            val r = when (target.type) {
                ScanTargetType.APK -> scanPath(tracker.extractApk(packageName ?: throw Exception("No Android package is available for this scan target.")), k)
                ScanTargetType.DOWNLOADED_APK -> scanPath(tracker.resolve(target.detail).absolutePath, k)
                ScanTargetType.REMOTE_APK -> downloadAndScan(target.detail, k).copy(type = ScanTargetType.REMOTE_APK)
            }.let { if (target.type == ScanTargetType.DOWNLOADED_APK) it.copy(type = target.type) else it }
            step("Report ready.", 100); delay(350)
            result = r; view = VtView.RESULTS
            haptic(if (r.stats.malicious + r.stats.suspicious >= 5) "warning" else "success"); r
        } catch (e: Exception) {
            error = e.message ?: "VirusTotal scan failed."; view = VtView.ERROR; haptic("error"); null
        }
    }
}
