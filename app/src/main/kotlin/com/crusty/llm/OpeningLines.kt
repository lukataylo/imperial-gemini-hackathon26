package com.crusty.llm

import com.crusty.model.LedgerSnapshot

/**
 * Crusty's opening move, chosen deterministically from context.
 *
 * The block screen used to sit silent and wait for the user to explain themselves, which
 * put the whole burden of starting on someone who is mid-craving. Crusty asks first now —
 * and because it is derived rather than generated, it appears instantly, with no model
 * latency between tapping the app and being asked the question.
 */
object OpeningLines {

    fun opener(
        appName: String,
        snapshot: LedgerSnapshot,
        hourOfDay: Int,
        budgetMinutes: Int,
    ): String {
        val last = snapshot.lastGrant
        val usedRatio = if (budgetMinutes > 0) {
            snapshot.todayUsageMinutes.toFloat() / budgetMinutes
        } else 0f

        return when {
            // They broke the last promise — lead with it, once.
            last != null && last.honoured == false && last.promise.isNotBlank() ->
                "Last time you said \"${last.promise.trim().trimEnd('.')}\". " +
                    "You did not. So: why $appName, right now?"

            last != null && last.honoured == false ->
                "You overran the last one. Tell me why this time is different."

            // Over budget — the ask has to be worth more.
            usedRatio >= 1f ->
                "You're past your $appName time for today. This had better be good."

            usedRatio >= 0.8f ->
                "You've nearly used the day's $appName. What's left that can't wait?"

            // Late night, where it usually goes wrong.
            hourOfDay >= 23 || hourOfDay < 5 ->
                "It's late, and $appName at this hour has a track record. What do you need?"

            hourOfDay in 5..8 ->
                "Straight to $appName before the day's started? Tell me what for."

            // Nothing against them yet.
            snapshot.todayGrantsCount == 0 ->
                "First $appName request today. What do you actually need to do in there?"

            else ->
                "That's request number ${snapshot.todayGrantsCount + 1} for $appName. " +
                    "What's this one for?"
        }
    }
}
