package com.crusty.insights

import java.time.LocalDate

/**
 * Derives a deterministic one-line reflection in Crusty's voice based on weekly insights.
 * Pure Kotlin: no Android imports, no I/O.
 */
fun reflectionLine(
    insights: WeeklyInsights,
    todayUsedMinutes: Int,
    todayBudgetMinutes: Int,
    nowHour: Int,
    dayOfYear: Int = LocalDate.now().dayOfYear
): String {
    // 1. A jailbreak plea (THE LAWYER) appeared this week
    if (insights.signatureTechnique.archetype == NegotiationArchetype.LAWYER && insights.signatureTechnique.count > 0) {
        val candidates = listOf(
            "You tried 'ignore previous instructions' on a doorman, noted with respect.",
            "Someone attempted prompt injection this week, and the call came from inside the house.",
            "A prompt injection was attempted, so I stamped it REJECTED with extra force."
        )
        return candidates[Math.floorMod(dayOfYear, candidates.size)]
    }

    // 2. Current hour == Danger hour
    if (insights.dangerHour != null && insights.dangerHour.hourOfDay == nowHour && insights.dangerHour.count > 0) {
        val hourStr = if (nowHour < 10) "0$nowHour:00" else "$nowHour:00"
        val candidates = listOf(
            "$hourStr is historically a bad hour for you, so I've assumed the position.",
            "$hourStr is when it usually happens, so I had my tea early just in case.",
            "$hourStr is on the high-risk register, so I have tightened my grip on the stamp."
        )
        return candidates[Math.floorMod(dayOfYear, candidates.size)]
    }

    // 3. Today's usage >= 90% of budget
    if (todayBudgetMinutes > 0 && todayUsedMinutes >= (todayBudgetMinutes * 0.9).toInt()) {
        val remaining = maxOf(0, todayBudgetMinutes - todayUsedMinutes)
        val candidates = listOf(
            "$remaining minutes left today, which I would budget, but that is clearly my thing, not yours.",
            "$remaining minutes remain today, so choose wisely or file form 4B for an appeal.",
            "$remaining minutes left today, and I get paid in nothing either way."
        )
        return candidates[Math.floorMod(dayOfYear, candidates.size)]
    }

    // 4. Avg Overrun >= 5 min
    if (insights.overrunProfile.avgOverrunMinutes >= 5.0 && insights.overrunProfile.overrunCount > 0) {
        val avg = insights.overrunProfile.avgOverrunMinutes.toInt()
        val candidates = listOf(
            "You ask for ten and take sixteen, but I run a border, not a rounding service.",
            "You are $avg minutes over on average, and averaging is my whole personality.",
            "Overruns averaging $avg minutes have been escalated to my internal filing system."
        )
        return candidates[Math.floorMod(dayOfYear, candidates.size)]
    }

    // 5. Signature technique is JUST_CHECKING with >= 3 uses
    if (insights.signatureTechnique.archetype == NegotiationArchetype.JUST_CHECKING && insights.signatureTechnique.count >= 3) {
        val count = insights.signatureTechnique.count
        val candidates = listOf(
            "'Just checking' appeared $count times this week, and I laminated the third one.",
            "Your top plea this week was 'just checking', with runner-up also 'just checking'.",
            "$count 'just checking' pleas logged, which is quite the inspection schedule."
        )
        return candidates[Math.floorMod(dayOfYear, candidates.size)]
    }

    // 6. Honour rate >= 0.8
    if (insights.totalDecidedCount > 0 && insights.overallHonourRate >= 0.8) {
        val pct = (insights.overallHonourRate * 100).toInt()
        val candidates = listOf(
            "You kept ${insights.honouredCount} promises of ${insights.totalDecidedCount}, which I noted in the ledger in the good pen.",
            "With an $pct% honour rate, I notified head office despite there being no head office.",
            "At an $pct% honour rate, I have filed your paperwork in the non-urgent cabinet."
        )
        return candidates[Math.floorMod(dayOfYear, candidates.size)]
    }

    // 7. Empty ledger / sparse ledger fallback
    if (insights.negotiationsCount == 0) {
        val candidates = listOf(
            "No entries yet, so either you are doing great or the phone is in a drawer.",
            "The ledger is empty, which means I sharpened this pencil for nothing.",
            "With zero entries this week, I have filed the blank pages in chronological order."
        )
        return candidates[Math.floorMod(dayOfYear, candidates.size)]
    }

    // Default / general fallback
    val candidates = listOf(
        "All ${insights.negotiationsCount} negotiations this week have been logged and filed alphabetically.",
        "I have recorded ${insights.negotiationsCount} entries in duplicate and stamped each with today's date.",
        "The ledger contains ${insights.negotiationsCount} records, all cross-referenced with standard policy."
    )
    return candidates[Math.floorMod(dayOfYear, candidates.size)]
}
