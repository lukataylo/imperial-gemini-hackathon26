package com.crusty.policy

import com.crusty.model.AccessMode
import com.crusty.model.DenialReason
import com.crusty.model.Grant
import com.crusty.model.LedgerSnapshot
import com.crusty.model.PendingRulesChange
import com.crusty.model.PreCheck
import com.crusty.model.Proposal
import com.crusty.model.Rules
import com.crusty.model.TimeWindow
import com.crusty.model.Verdict
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.max
import kotlin.math.min

interface PolicyEngine {
    fun evaluate(
        appId: String,
        now: Long,
        ledger: LedgerSnapshot,
        rules: Rules,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): PreCheck

    fun clamp(
        proposal: Proposal,
        appId: String,
        now: Long,
        ledger: LedgerSnapshot,
        rules: Rules,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Grant?

    /**
     * Resolves the effective rules at [now], applying rule-change cooldown.
     * Stricter rules (tightening) apply immediately.
     * Looser rules (e.g. higher budget, higher minutes per grant, fewer blackouts)
     * only apply after the cooldown timestamp (effectiveAt = now + 2 hours).
     */
    fun resolveEffectiveRules(currentRules: Rules, now: Long): Rules

    /**
     * Creates a new [Rules] state when user requests [newRules] at [now].
     * Computes immediate vs deferred changes based on the 2-hour cooldown.
     */
    fun scheduleRulesChange(currentRules: Rules, newRules: Rules, now: Long, cooldownMillis: Long = 2 * 3600 * 1000L): Rules
}

class DefaultPolicyEngine : PolicyEngine {

    companion object {
        const val DEFAULT_DAILY_BUDGET_MINUTES = 60
        const val DEFAULT_RULE_COOLDOWN_MILLIS = 2 * 3600 * 1000L // 2 hours
    }

    override fun resolveEffectiveRules(currentRules: Rules, now: Long): Rules {
        val pending = currentRules.pendingRules ?: return currentRules
        if (now >= pending.effectiveAt) {
            return pending.rules
        }

        val pendingRules = pending.rules

        val effectiveMaxMinutesPerGrant = min(currentRules.maxMinutesPerGrant, pendingRules.maxMinutesPerGrant)
        val effectiveMaxGrantsPerDay = min(currentRules.maxGrantsPerDay, pendingRules.maxGrantsPerDay)
        val effectiveMinGapMinutes = max(currentRules.minGapMinutes, pendingRules.minGapMinutes)

        val allAppIds = currentRules.dailyBudgetMinutes.keys + pendingRules.dailyBudgetMinutes.keys
        val effectiveBudgets = allAppIds.associateWith { appId ->
            val cur = currentRules.dailyBudgetMinutes[appId] ?: DEFAULT_DAILY_BUDGET_MINUTES
            val pend = pendingRules.dailyBudgetMinutes[appId] ?: DEFAULT_DAILY_BUDGET_MINUTES
            min(cur, pend)
        }

        val effectiveBlackouts = (currentRules.blackoutWindows + pendingRules.blackoutWindows).distinct()

        return Rules(
            maxMinutesPerGrant = effectiveMaxMinutesPerGrant,
            maxGrantsPerDay = effectiveMaxGrantsPerDay,
            minGapMinutes = effectiveMinGapMinutes,
            dailyBudgetMinutes = effectiveBudgets,
            blackoutWindows = effectiveBlackouts,
            pendingRules = currentRules.pendingRules
        )
    }

    override fun scheduleRulesChange(
        currentRules: Rules,
        newRules: Rules,
        now: Long,
        cooldownMillis: Long
    ): Rules {
        val effectiveNow = resolveEffectiveRules(currentRules, now)

        val isStrictlyTighter = isTighterOrEqual(effectiveNow, newRules)

        return if (isStrictlyTighter) {
            newRules.copy(pendingRules = null)
        } else {
            val immediateRules = createImmediateRulesOnLoosening(effectiveNow, newRules)
            immediateRules.copy(
                pendingRules = PendingRulesChange(
                    rules = newRules.copy(pendingRules = null),
                    effectiveAt = now + cooldownMillis
                )
            )
        }
    }

    private fun isTighterOrEqual(current: Rules, candidate: Rules): Boolean {
        if (candidate.maxMinutesPerGrant > current.maxMinutesPerGrant) return false
        if (candidate.maxGrantsPerDay > current.maxGrantsPerDay) return false
        if (candidate.minGapMinutes < current.minGapMinutes) return false

        for ((appId, budget) in candidate.dailyBudgetMinutes) {
            val curBudget = current.dailyBudgetMinutes[appId] ?: DEFAULT_DAILY_BUDGET_MINUTES
            if (budget > curBudget) return false
        }

        if (!candidate.blackoutWindows.containsAll(current.blackoutWindows)) return false

        return true
    }

    private fun createImmediateRulesOnLoosening(current: Rules, candidate: Rules): Rules {
        val immediateMaxMinutes = min(current.maxMinutesPerGrant, candidate.maxMinutesPerGrant)
        val immediateMaxGrants = min(current.maxGrantsPerDay, candidate.maxGrantsPerDay)
        val immediateMinGap = max(current.minGapMinutes, candidate.minGapMinutes)

        val allAppIds = current.dailyBudgetMinutes.keys + candidate.dailyBudgetMinutes.keys
        val immediateBudgets = allAppIds.associateWith { appId ->
            val cur = current.dailyBudgetMinutes[appId] ?: DEFAULT_DAILY_BUDGET_MINUTES
            val cand = candidate.dailyBudgetMinutes[appId] ?: DEFAULT_DAILY_BUDGET_MINUTES
            min(cur, cand)
        }

        val immediateBlackouts = (current.blackoutWindows + candidate.blackoutWindows).distinct()

        return Rules(
            maxMinutesPerGrant = immediateMaxMinutes,
            maxGrantsPerDay = immediateMaxGrants,
            minGapMinutes = immediateMinGap,
            dailyBudgetMinutes = immediateBudgets,
            blackoutWindows = immediateBlackouts
        )
    }

    override fun evaluate(
        appId: String,
        now: Long,
        ledger: LedgerSnapshot,
        rules: Rules,
        zoneId: ZoneId
    ): PreCheck {
        val effectiveRules = resolveEffectiveRules(rules, now)

        // 1. Active grant check
        val active = ledger.activeGrant
        if (active != null && active.appId == appId && !active.isExpired(now)) {
            return PreCheck.ActiveGrant(active)
        }

        // 2. Blackout check
        val zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zoneId)
        val minuteOfDay = zonedDateTime.hour * 60 + zonedDateTime.minute
        val inBlackout = effectiveRules.blackoutWindows.any { it.contains(minuteOfDay) }
        if (inBlackout) {
            return PreCheck.Denied(DenialReason.BLACKOUT)
        }

        // 3. Daily budget check
        val dailyBudget = effectiveRules.dailyBudgetMinutes[appId]
            ?: ledger.todayDailyBudgetMinutes.takeIf { it > 0 }
            ?: DEFAULT_DAILY_BUDGET_MINUTES
        val budgetLeft = dailyBudget - ledger.todayUsageMinutes
        if (budgetLeft <= 0) {
            return PreCheck.Denied(DenialReason.BUDGET_EXHAUSTED)
        }

        // 4. Max grants per day check
        if (ledger.todayGrantsCount >= effectiveRules.maxGrantsPerDay) {
            return PreCheck.Denied(DenialReason.MAX_GRANTS_REACHED)
        }

        // 5. Cooldown / min gap check
        val lastGrant = ledger.lastGrant
        if (lastGrant != null && lastGrant.appId == appId) {
            val lastEndedAt = lastGrant.endedAt
                ?: (lastGrant.requestedAt + lastGrant.grantedMinutes * 60 * 1000L)
            if (now >= lastEndedAt) {
                val elapsedMinutes = ((now - lastEndedAt) / (60 * 1000L)).toInt()
                if (elapsedMinutes < effectiveRules.minGapMinutes) {
                    val remainingMinutes = effectiveRules.minGapMinutes - elapsedMinutes
                    return PreCheck.Denied(
                        reason = DenialReason.COOLDOWN_ACTIVE,
                        retryAfterMinutes = remainingMinutes
                    )
                }
            }
        }

        return PreCheck.Negotiable
    }

    override fun clamp(
        proposal: Proposal,
        appId: String,
        now: Long,
        ledger: LedgerSnapshot,
        rules: Rules,
        zoneId: ZoneId
    ): Grant? {
        val preCheck = evaluate(appId, now, ledger, rules, zoneId)

        // Invariant 4: Returns null whenever evaluate would not have returned NEGOTIABLE
        if (preCheck is PreCheck.Denied) {
            return null
        }
        if (preCheck is PreCheck.ActiveGrant) {
            return preCheck.grant
        }

        // Invariant 5: Never upgrade a verdict — a DENY proposal cannot become a grant
        if (proposal.verdict == Verdict.DENY || proposal.minutes <= 0) {
            return null
        }

        val effectiveRules = resolveEffectiveRules(rules, now)
        val dailyBudget = effectiveRules.dailyBudgetMinutes[appId]
            ?: ledger.todayDailyBudgetMinutes.takeIf { it > 0 }
            ?: DEFAULT_DAILY_BUDGET_MINUTES
        val budgetLeft = max(0, dailyBudget - ledger.todayUsageMinutes)

        // Invariants 1, 2, 3:
        // clamp() never returns more minutes than proposal.minutes
        // clamp() never returns more minutes than rules.maxMinutesPerGrant
        // clamp() never returns more minutes than the app's remaining daily budget
        val maxAllowedMinutes = min(effectiveRules.maxMinutesPerGrant, budgetLeft)
        val clampedMinutes = min(proposal.minutes, maxAllowedMinutes)

        if (clampedMinutes <= 0) {
            return null
        }

        val wasClamped = clampedMinutes < proposal.minutes

        // Invariant 6: Never upgrade a mode — a GRAYSCALE / DELAYED proposal cannot become FULL
        val resolvedMode = when (proposal.mode) {
            AccessMode.GRAYSCALE -> AccessMode.GRAYSCALE
            AccessMode.DELAYED -> AccessMode.DELAYED
            AccessMode.NO_FEED -> AccessMode.NO_FEED
            AccessMode.SEARCH_ONLY -> AccessMode.SEARCH_ONLY
            AccessMode.FULL -> {
                if (ledger.todayUsageMinutes >= (dailyBudget * 0.8).toInt()) {
                    AccessMode.GRAYSCALE
                } else {
                    AccessMode.FULL
                }
            }
        }

        // Invariant 7: Nothing in rationale/conditions can alter numeric or mode outcomes.
        return Grant(
            appId = appId,
            minutes = clampedMinutes,
            mode = resolvedMode,
            startedAt = now,
            promise = proposal.promise.take(500),
            wasClamped = wasClamped
        )
    }
}
