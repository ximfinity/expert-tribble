package com.phantomfloor.util

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Asks GitHub for the latest release of [repo] ("owner/name") and reports it if newer than
 * [currentVersion]. Failures (offline, private repo, rate limit) return null: never a crash.
 */
class UpdateChecker(private val repo: String, private val currentVersion: String) {

    data class Update(val version: String, val url: String)

    /** What the About screen shows. */
    sealed interface Status {
        data object Checking : Status
        data object UpToDate : Status
        data object Unknown : Status
        data class Available(val update: Update) : Status
    }

    suspend fun check(): Update? = (status() as? Status.Available)?.update

    suspend fun status(): Status = withContext(Dispatchers.IO) {
        if (repo.isBlank()) return@withContext Status.Unknown
        runCatching {
            val conn = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                if (conn.responseCode != 200) return@runCatching Status.Unknown
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val version = json.getString("tag_name").removePrefix("v")
                val url = json.getString("html_url")
                if (isNewer(version, currentVersion)) Status.Available(Update(version, url)) else Status.UpToDate
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(Status.Unknown)
    }

    companion object {
        /** Compares dotted numeric versions ("1.10.0" > "1.9.2"); non-numeric parts count as 0. */
        fun isNewer(candidate: String, current: String): Boolean {
            val a = candidate.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val b = current.split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
