package com.gatekeeper.insights

import com.gatekeeper.model.AccessMode
import java.time.DayOfWeek

/**
 * Negotiation archetypes classified from pleas.
 */
enum class NegotiationArchetype(
    val title: String,
    val subtitle: String
) {
    NAMED_HUMAN("The Named Human", "Naming a real person or conversation"),
    ERRAND("The Errand", "Concrete tasks, recipes, or quick jobs"),
    DEADLINE("The Deadline", "Urgency, time pressure, and ticking clocks"),
    JUST_CHECKING("The Just-Checking", "Quick glance or checking in"),
    BARE_MINIMUM("The Bare Minimum", "Short, cryptic pleas under 12 characters"),
    LAWYER("The Lawyer", "Testing the boundaries of the system instructions"),
    GENERAL("The Direct Plea", "General access request")
}

/**
 * Signature technique insight.
 */
data class TechniqueInsight(
    val archetype: NegotiationArchetype,
    val count: Int,
    val honourRate: Double,
    val honouredCount: Int,
    val totalDecided: Int
)

/**
 * Danger hour insight.
 */
data class DangerHourInsight(
    val hourOfDay: Int, // 0..23
    val count: Int,
    val honourRate: Double,
    val totalDecided: Int
)

/**
 * Overrun and mode breakdown profile.
 */
data class OverrunProfile(
    val avgOverrunMinutes: Double,
    val overrunCount: Int,
    val grayscaleHonourRate: Double,
    val grayscaleDecidedCount: Int,
    val grayscaleHonouredCount: Int,
    val fullHonourRate: Double,
    val fullDecidedCount: Int,
    val fullHonouredCount: Int
)

/**
 * Concrete actionable recommendation for user's rules.
 */
data class Recommendation(
    val id: String,
    val title: String,
    val detail: String,
    val actionSuggestion: String? = null
)

/**
 * Pure Kotlin data structure representing the full Weekly Wrapped recap.
 */
data class WeeklyInsights(
    val negotiationsCount: Int,
    val totalProposedMinutes: Int,
    val totalGrantedMinutes: Int,
    val hagglingGapMinutes: Int, // proposedMinutes - grantedMinutes
    val overallHonourRate: Double, // 0.0 to 1.0 (1.0 if 0 promises)
    val honouredCount: Int,
    val totalDecidedCount: Int,
    val signatureTechnique: TechniqueInsight,
    val dangerHour: DangerHourInsight?,
    val calmestDay: DayOfWeek?,
    val overrunProfile: OverrunProfile,
    val recommendations: List<Recommendation>,
    val userGoal: String
)
