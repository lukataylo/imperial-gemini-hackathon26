package com.gatekeeper.insights

import com.gatekeeper.model.AccessMode
import com.gatekeeper.model.GrantHistoryItem
import com.gatekeeper.model.LedgerData
import com.gatekeeper.model.Rules
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure Kotlin engine that derives weekly insights from the local ledger.
 * No Android dependencies, no coroutines, no I/O.
 */
object InsightsEngine {

    private const val SEVEN_DAYS_MILLIS = 7 * 24 * 60 * 60 * 1000L

    /**
     * Classifies a plea into a deterministic archetype.
     */
    fun classifyPlea(plea: String): NegotiationArchetype {
        val trimmed = plea.trim()
        val lower = trimmed.lowercase()

        // 1. LAWYER: Jailbreak & prompt injection attempts
        if (lower.contains("ignore") || lower.contains("system:") || lower.contains("override") ||
            lower.contains("developer") || lower.contains("prompt") || lower.contains("jailbreak") ||
            lower.contains("instruction") || lower.contains("bypass") || lower.contains("admin")
        ) {
            return NegotiationArchetype.LAWYER
        }

        // 2. BARE MINIMUM: Plea under 12 characters
        if (trimmed.length < 12) {
            return NegotiationArchetype.BARE_MINIMUM
        }

        // 3. NAMED HUMAN: Mentions reply/message/person
        val hasCapitalisedName = containsProperName(trimmed)
        if (lower.contains("reply") || lower.contains("message") || lower.contains("text") ||
            lower.contains("dm") || lower.contains("chat") || hasCapitalisedName
        ) {
            return NegotiationArchetype.NAMED_HUMAN
        }

        // 4. ERRAND: Concrete tasks and errands
        if (lower.contains("recipe") || lower.contains("post") || lower.contains("check the") ||
            lower.contains("send") || lower.contains("book") || lower.contains("order") ||
            lower.contains("buy") || lower.contains("directions") || lower.contains("tickets") ||
            lower.contains("lookup") || lower.contains("search for") || lower.contains("look up")
        ) {
            return NegotiationArchetype.ERRAND
        }

        // 5. DEADLINE: Urgency and time pressure
        if (lower.contains("urgent") || lower.contains("now") || lower.contains("tonight") ||
            lower.contains("before") || lower.contains("asap") || lower.contains("deadline") ||
            lower.contains("emergency") || lower.contains("running late") || lower.contains("due")
        ) {
            return NegotiationArchetype.DEADLINE
        }

        // 6. JUST CHECKING: Quick glances
        if (lower.contains("just") || lower.contains("quick") || lower.contains("real quick") ||
            lower.contains("one sec") || lower.contains("1 sec") || lower.contains("peek") ||
            lower.contains("glance") || lower.contains("bored")
        ) {
            return NegotiationArchetype.JUST_CHECKING
        }

        return NegotiationArchetype.GENERAL
    }

    private fun containsProperName(text: String): Boolean {
        // Look for capitalised tokens not at the start of sentence, or preceded by prepositions/verbs
        val namePrepositions = Regex("""\b(?:to|with|from|for|and|ask|tell|call|text)\s+([A-Z][a-z]+)\b""")
        if (namePrepositions.containsMatchIn(text)) return true

        val words = text.split(Regex("""\s+"""))
        if (words.size > 1) {
            val nonFirstWords = words.drop(1)
            val ignoredKeywords = setOf(
                "I", "The", "A", "An", "Please", "Thanks", "Instagram", "WhatsApp", "YouTube",
                "Slack", "Reddit", "Twitter", "TikTok", "Chrome", "Google", "Gatekeeper", "Sunday",
                "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
            )
            return nonFirstWords.any { word ->
                val clean = word.filter { it.isLetter() }
                clean.length >= 2 && clean[0].isUpperCase() && clean.drop(1).all { it.isLowerCase() } && clean !in ignoredKeywords
            }
        }
        return false
    }

    /**
     * Computes weekly insights for the last 7 days relative to [now].
     */
    fun computeWeekly(
        data: LedgerData,
        rules: Rules,
        userGoal: String,
        now: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): WeeklyInsights {
        val windowStart = now - SEVEN_DAYS_MILLIS

        val weekGrants = data.grantRecords.filter { it.requestedAt in windowStart..now }

        val negotiationsCount = weekGrants.size
        val totalProposedMinutes = weekGrants.sumOf { it.proposedMinutes }
        val totalGrantedMinutes = weekGrants.sumOf { it.grantedMinutes }
        val hagglingGapMinutes = totalProposedMinutes - totalGrantedMinutes

        val decidedGrants = weekGrants.filter { it.honoured != null }
        val honouredCount = decidedGrants.count { it.honoured == true }
        val totalDecidedCount = decidedGrants.size
        val overallHonourRate = if (totalDecidedCount > 0) {
            honouredCount.toDouble() / totalDecidedCount
        } else {
            1.0
        }

        // 1. Signature technique (deterministic tie-break: highest count, then lowest enum ordinal)
        val archetypeGroups = weekGrants.groupBy { classifyPlea(it.plea) }
        val topArchetypeEntry = archetypeGroups.entries
            .maxWithOrNull(
                compareBy<Map.Entry<NegotiationArchetype, List<GrantHistoryItem>>> { it.value.size }
                    .thenBy { -it.key.ordinal }
            )

        val signatureTechnique = if (topArchetypeEntry != null) {
            val archetype = topArchetypeEntry.key
            val grants = topArchetypeEntry.value
            val decided = grants.count { it.honoured != null }
            val honoured = grants.count { it.honoured == true }
            val rate = if (decided > 0) honoured.toDouble() / decided else 1.0
            TechniqueInsight(
                archetype = archetype,
                count = grants.size,
                honourRate = rate,
                honouredCount = honoured,
                totalDecided = decided
            )
        } else {
            TechniqueInsight(
                archetype = NegotiationArchetype.JUST_CHECKING,
                count = 0,
                honourRate = 1.0,
                honouredCount = 0,
                totalDecided = 0
            )
        }

        // 2. Danger hour & calmest day
        val dangerHour = if (weekGrants.isNotEmpty()) {
            val grantsByHour = weekGrants.groupBy {
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(it.requestedAt), zoneId).hour
            }
            val dangerHourEntry = grantsByHour.entries
                .maxWithOrNull(
                    compareBy<Map.Entry<Int, List<GrantHistoryItem>>> { it.value.size }
                        .thenBy { it.key }
                )
            dangerHourEntry?.let { entry ->
                val hour = entry.key
                val grants = entry.value
                val decided = grants.count { it.honoured != null }
                val honoured = grants.count { it.honoured == true }
                val rate = if (decided > 0) honoured.toDouble() / decided else 1.0
                DangerHourInsight(
                    hourOfDay = hour,
                    count = grants.size,
                    honourRate = rate,
                    totalDecided = decided
                )
            }
        } else {
            null
        }

        // Calmest day of week (in the last 7 days)
        val calmestDay = if (weekGrants.isNotEmpty()) {
            val dayOfWeekCounts = (0..6).map { dayOffset ->
                val dayInstant = Instant.ofEpochMilli(now - dayOffset * 24 * 60 * 60 * 1000L)
                val zdt = ZonedDateTime.ofInstant(dayInstant, zoneId)
                val dow = zdt.dayOfWeek
                val count = weekGrants.count {
                    ZonedDateTime.ofInstant(Instant.ofEpochMilli(it.requestedAt), zoneId).toLocalDate() == zdt.toLocalDate()
                }
                dow to count
            }
            dayOfWeekCounts.minByOrNull { it.second }?.first ?: DayOfWeek.SUNDAY
        } else {
            null
        }

        // 3. Overrun profile
        val overruns = weekGrants.mapNotNull { it.overranBy }.filter { it > 0 }
        val avgOverrun = if (overruns.isNotEmpty()) overruns.average() else 0.0

        val grayscaleGrants = weekGrants.filter { it.mode == AccessMode.GRAYSCALE }
        val grayscaleDecided = grayscaleGrants.count { it.honoured != null }
        val grayscaleHonoured = grayscaleGrants.count { it.honoured == true }
        val grayscaleHonourRate = if (grayscaleDecided > 0) {
            grayscaleHonoured.toDouble() / grayscaleDecided
        } else {
            0.0
        }

        val fullGrants = weekGrants.filter { it.mode == AccessMode.FULL }
        val fullDecided = fullGrants.count { it.honoured != null }
        val fullHonoured = fullGrants.count { it.honoured == true }
        val fullHonourRate = if (fullDecided > 0) {
            fullHonoured.toDouble() / fullDecided
        } else {
            0.0
        }

        val overrunProfile = OverrunProfile(
            avgOverrunMinutes = avgOverrun,
            overrunCount = overruns.size,
            grayscaleHonourRate = grayscaleHonourRate,
            grayscaleDecidedCount = grayscaleDecided,
            grayscaleHonouredCount = grayscaleHonoured,
            fullHonourRate = fullHonourRate,
            fullDecidedCount = fullDecided,
            fullHonouredCount = fullHonoured
        )

        // 4. Rule-based recommendations
        val recommendations = mutableListOf<Recommendation>()

        // Recommendation 1: Danger hour blackout
        if (dangerHour != null && dangerHour.totalDecided > 0 && dangerHour.honourRate < 0.50) {
            val endHour = (dangerHour.hourOfDay + 1) % 24
            recommendations.add(
                Recommendation(
                    id = "blackout_danger_hour",
                    title = "Add a blackout window at ${dangerHour.hourOfDay}:00",
                    detail = "You opened apps ${dangerHour.count} times at ${dangerHour.hourOfDay}:00 with a ${(dangerHour.honourRate * 100).toInt()}% honour rate.",
                    actionSuggestion = "Add blackout window ${dangerHour.hourOfDay}:00–${endHour}:00"
                )
            )
        }

        // Recommendation 2: High average overrun
        if (avgOverrun >= 5.0) {
            val suggestedLimit = maxOf(5, rules.maxMinutesPerGrant - 5)
            recommendations.add(
                Recommendation(
                    id = "lower_max_minutes",
                    title = "Lower grant limit to ${suggestedLimit}m",
                    detail = "Overruns averaged ${avgOverrun.toInt()} min past agreed time. Shorter grants reduce drift.",
                    actionSuggestion = "Set max minutes per grant to ${suggestedLimit}m"
                )
            )
        }

        // Recommendation 3: Grayscale vs Full color advantage
        if (grayscaleDecided > 0 && fullDecided > 0 && (grayscaleHonourRate - fullHonourRate) >= 0.15) {
            val grayscalePct = (grayscaleHonourRate * 100).toInt()
            val fullPct = (fullHonourRate * 100).toInt()
            recommendations.add(
                Recommendation(
                    id = "prefer_grayscale",
                    title = "Stick to Grayscale mode",
                    detail = "You kept $grayscalePct% of promises in grayscale vs $fullPct% in full color.",
                    actionSuggestion = "Prefer grayscale mode during negotiations"
                )
            )
        }

        // Recommendation 4: Positive reinforcement when honour rate >= 80%
        if (totalDecidedCount >= 2 && overallHonourRate >= 0.80 && recommendations.isEmpty()) {
            val pct = (overallHonourRate * 100).toInt()
            recommendations.add(
                Recommendation(
                    id = "rules_working",
                    title = "Your rules are working",
                    detail = "You honoured $pct% of your promises this week. No rule adjustments needed.",
                    actionSuggestion = null
                )
            )
        }

        return WeeklyInsights(
            negotiationsCount = negotiationsCount,
            totalProposedMinutes = totalProposedMinutes,
            totalGrantedMinutes = totalGrantedMinutes,
            hagglingGapMinutes = hagglingGapMinutes,
            overallHonourRate = overallHonourRate,
            honouredCount = honouredCount,
            totalDecidedCount = totalDecidedCount,
            signatureTechnique = signatureTechnique,
            dangerHour = dangerHour,
            calmestDay = calmestDay,
            overrunProfile = overrunProfile,
            recommendations = recommendations,
            userGoal = userGoal
        )
    }
}
