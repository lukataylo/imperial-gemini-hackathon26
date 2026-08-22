package com.crusty.cloud

import android.util.Log
import com.crusty.BuildConfig
import com.crusty.model.GrantHistoryItem
import com.crusty.model.UsageSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId

/**
 * Cloud-side reflection over the ledger, using Gemini.
 *
 * The division of labour is deliberate and is the whole hybrid argument:
 *
 *  - Gemma 4 E2B, on-device, handles the NEGOTIATION. It is latency-critical (it sits between
 *    a person and the app they are reaching for), it must work offline, and its input is a
 *    running record of someone's compulsions. That belongs on the phone.
 *
 *  - Gemini handles the WEEKLY REFLECTION. Spotting that promises hold before 10pm and
 *    collapse after it is longitudinal reasoning over aggregate history — not latency
 *    critical, and it benefits from a much larger model.
 *
 * This call is opt-in and sends only aggregates: app id, minutes, durations, hour-of-day and
 * whether a promise was kept. No plea text, no promise text, no message content ever leaves
 * the device — see [buildAnonymisedSummary].
 */
class UsageInsights(
    private val apiKey: String = BuildConfig.GEMINI_API_KEY,
    private val model: String = DEFAULT_MODEL,
) {
    companion object {
        private const val TAG = "UsageInsights"
        const val DEFAULT_MODEL = "gemini-3.7-flash"
        /** Tried in order. The newest model is the most contended on a hackathon day,
         *  and a 503 there should not read to the user as "this feature is broken". */
        val FALLBACK_MODELS = listOf("gemini-3.7-flash", "gemini-3.6-flash", "gemini-flash-latest")
        private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"
    }

    val isConfigured: Boolean get() = apiKey.isNotBlank()

    sealed interface Result {
        data class Success(val text: String) : Result
        data class Failure(val message: String) : Result
        data object NotConfigured : Result
    }

    /**
     * Aggregates only. Deliberately drops [GrantHistoryItem.plea] and .promise, which are
     * the free-text fields containing anything personal.
     */
    fun buildAnonymisedSummary(
        grants: List<GrantHistoryItem>,
        usage: List<UsageSample>,
        userGoal: String,
    ): String {
        val zone = ZoneId.systemDefault()
        val sb = StringBuilder()
        sb.append("Stated goal: ").append(userGoal.ifBlank { "(none given)" }).append('\n')
        sb.append("Grants (app, hour of day, asked, granted, mode, promise kept):\n")
        grants.takeLast(40).forEach { g ->
            val hour = Instant.ofEpochMilli(g.requestedAt).atZone(zone).hour
            sb.append("- ").append(g.appId.substringAfterLast('.'))
                .append(", ").append(hour).append(":00")
                .append(", asked ").append(g.proposedMinutes)
                .append(", got ").append(g.grantedMinutes)
                .append(", ").append(g.mode.name.lowercase())
                .append(", ").append(
                    when (g.honoured) {
                        true -> "kept"
                        false -> "broken"
                        null -> "unresolved"
                    }
                ).append('\n')
        }
        sb.append("Daily totals (app, day, minutes):\n")
        usage.takeLast(30).forEach { u ->
            sb.append("- ").append(u.appId.substringAfterLast('.'))
                .append(", ").append(u.day).append(", ").append(u.minutes).append(" min\n")
        }
        return sb.toString()
    }

    suspend fun analyse(
        grants: List<GrantHistoryItem>,
        usage: List<UsageSample>,
        userGoal: String,
    ): Result = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext Result.NotConfigured
        if (grants.isEmpty() && usage.isEmpty()) {
            return@withContext Result.Failure("Not enough history yet. Come back after a few negotiations.")
        }

        val prompt = """
            You are reviewing one person's phone-use history from an app that makes them
            negotiate for access to distracting apps. They set this up themselves.

            Find the patterns they cannot see from inside the habit. Look for time-of-day
            effects, which apps they overrun on, whether asking for more correlates with
            keeping the promise, and whether they are improving.

            Write at most 150 words, second person, plain and specific. Lead with the single
            most useful observation. Quote real numbers from the data. No preamble, no
            bullet-point padding, no praise, no moralising. If the data is too thin to
            support a claim, say that instead of inventing one.

            ${buildAnonymisedSummary(grants, usage, userGoal)}
        """.trimIndent()

        val models = (listOf(model) + FALLBACK_MODELS).distinct()
        var lastError = "Network error"

        for (candidateModel in models) {
            var attempt = 0
            while (attempt < 3) {
                if (attempt > 0) kotlinx.coroutines.delay(1200L * attempt)
                when (val r = callOnce(candidateModel, prompt)) {
                    is Result.Success -> return@withContext r
                    is Result.Failure -> {
                        lastError = r.message
                        val transient = r.message.contains("503") || r.message.contains("429")
                        // Anything else (bad key, unknown model, DNS) will not improve by
                        // repeating — move to the next model instead of burning three
                        // 15-second timeouts on the same one.
                        if (!transient) break
                        attempt++
                    }
                    else -> break
                }
            }
        }
        Result.Failure(lastError)
    }

    private fun callOnce(model: String, prompt: String): Result {
        return try {
            val body = JSONObject()
                .put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                    )
                )
                // Gemini 3.x thinks before answering, which costs most of the latency here.
                // Measured on this key: 3.05s default vs 2.52s with thinking held low.
                // The task is a short summary, so deep reasoning buys us nothing.
                .put(
                    "generationConfig",
                    JSONObject()
                        .put("maxOutputTokens", 400)
                        .put("temperature", 0.7)
                        .put("thinkingConfig", JSONObject().put("thinkingLevel", "low"))
                )

            val url = URL("$ENDPOINT/$model:generateContent")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 6_000
                readTimeout = 45_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-goog-api-key", apiKey)
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()

            if (code !in 200..299) {
                Log.w(TAG, "Gemini $model HTTP $code: ${text.take(300)}")
                return Result.Failure(
                    when (code) {
                        503, 429 -> "$code busy"
                        401, 403 -> "Gemini rejected the API key."
                        else -> "Gemini returned HTTP $code."
                    }
                )
            }

            // 3.x returns thought parts alongside text, so collect every text part
            // rather than assuming parts[0] holds the answer.
            val parts = JSONObject(text)
                .optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
            val out = buildString {
                for (i in 0 until (parts?.length() ?: 0)) {
                    parts?.optJSONObject(i)?.optString("text")?.let { append(it) }
                }
            }.trim()

            if (out.isBlank()) Result.Failure("Gemini returned an empty response.")
            else Result.Success(out)
        } catch (e: Exception) {
            Log.e(TAG, "Gemini call failed", e)
            Result.Failure(e.message ?: "Network error")
        }
    }
}
