package com.crusty.model

import kotlinx.serialization.Serializable

@Serializable
enum class Verdict {
    GRANT,
    DENY,
    COUNTER,
    CONDITIONAL
}

@Serializable
enum class AccessMode {
    FULL,
    GRAYSCALE,
    DELAYED,
    NO_FEED,
    SEARCH_ONLY
}

/**
 * What the model is allowed to say. Never trusted directly.
 */
@Serializable
data class Proposal(
    val verdict: Verdict,
    val minutes: Int,
    val mode: AccessMode = AccessMode.FULL,
    val conditions: List<String> = emptyList(),
    val rationale: String = "",
    val promise: String = "",
)

/**
 * What actually happens. Only ever produced by PolicyEngine.
 */
@Serializable
data class Grant(
    val appId: String,
    val minutes: Int,
    val mode: AccessMode,
    val startedAt: Long,
    val promise: String,
    val wasClamped: Boolean = false,
) {
    val expiresAt: Long get() = startedAt + (minutes.toLong() * 60_000L)
    fun isExpired(now: Long): Boolean = now >= expiresAt
}

@Serializable
data class TimeWindow(
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
) {
    init {
        require(startMinuteOfDay in 0..1439) { "startMinuteOfDay must be in 0..1439" }
        require(endMinuteOfDay in 0..1439) { "endMinuteOfDay must be in 0..1439" }
    }

    fun contains(minuteOfDay: Int): Boolean {
        return if (startMinuteOfDay <= endMinuteOfDay) {
            minuteOfDay in startMinuteOfDay..endMinuteOfDay
        } else {
            // Crosses midnight, e.g. 23:30 (1410) to 07:00 (420)
            minuteOfDay >= startMinuteOfDay || minuteOfDay <= endMinuteOfDay
        }
    }
}

@Serializable
data class Rules(
    val maxMinutesPerGrant: Int = 15,
    val maxGrantsPerDay: Int = 4,
    val minGapMinutes: Int = 20,
    val dailyBudgetMinutes: Map<String, Int> = emptyMap(),
    val blackoutWindows: List<TimeWindow> = emptyList(),
    val pendingRules: PendingRulesChange? = null,
)

@Serializable
data class PendingRulesChange(
    val rules: Rules,
    val effectiveAt: Long,
)

sealed interface PreCheck {
    data class ActiveGrant(val grant: Grant) : PreCheck
    data object Negotiable : PreCheck
    data class Denied(val reason: DenialReason, val retryAfterMinutes: Int? = null) : PreCheck
}

@Serializable
enum class DenialReason {
    BLACKOUT,
    BUDGET_EXHAUSTED,
    MAX_GRANTS_REACHED,
    COOLDOWN_ACTIVE,
    POLICY_REJECTED,
}

@Serializable
data class GrantHistoryItem(
    val id: Long = 0,
    val appId: String,
    val requestedAt: Long,
    val plea: String,
    val proposedMinutes: Int,
    val grantedMinutes: Int,
    val mode: AccessMode,
    val promise: String,
    val endedAt: Long? = null,
    val overranBy: Int? = null,
    val honoured: Boolean? = null,
)

@Serializable
data class UsageSample(
    val appId: String,
    val day: String,
    val minutes: Int,
)

@Serializable
data class LedgerSnapshot(
    val appId: String,
    val todayUsageMinutes: Int,
    val todayDailyBudgetMinutes: Int,
    val todayGrantsCount: Int,
    val activeGrant: Grant? = null,
    val lastGrant: GrantHistoryItem? = null,
    val recentGrants: List<GrantHistoryItem> = emptyList(),
    val weekHonouredCount: Int = 0,
    val weekTotalPromisesCount: Int = 0,
    val userGoal: String = "",
)

@Serializable
data class LedgerData(
    val grantRecords: List<GrantHistoryItem> = emptyList(),
    val usageSamples: List<UsageSample> = emptyList(),
    val activeGrant: Grant? = null,
)

@Serializable
data class WatchedApp(
    val packageName: String,
    val appName: String,
    val isWatched: Boolean = true,
)


/** Which on-device model Crusty negotiates with. */
@Serializable
enum class ModelVariant(
    val id: String,
    val label: String,
    val fileName: String,
    val url: String,
    val approxBytes: Long,
    val blurb: String,
) {
    E2B(
        id = "E2B",
        label = "Gemma 4 E2B",
        fileName = "gemma-4-E2B-it.litertlm",
        url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        approxBytes = 2_590_000_000L,
        blurb = "Faster. Lower latency at the gate.",
    ),
    E4B(
        id = "E4B",
        label = "Gemma 4 E4B",
        fileName = "gemma-4-E4B-it.litertlm",
        url = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        approxBytes = 3_660_000_000L,
        blurb = "Slower, but far better at holding a line and calling tools.",
    );

    companion object {
        fun fromId(id: String?): ModelVariant = entries.firstOrNull { it.id == id } ?: E2B
    }
}
