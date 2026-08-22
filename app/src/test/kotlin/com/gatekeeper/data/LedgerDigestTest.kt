package com.gatekeeper.data

import com.gatekeeper.model.AccessMode
import com.gatekeeper.model.GrantHistoryItem
import com.gatekeeper.model.LedgerSnapshot
import com.gatekeeper.model.Rules
import com.gatekeeper.model.TimeWindow
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class LedgerDigestTest {

    @Test
    fun `buildDigest produces exact required token-efficient format matching architecture spec`() {
        val zone = ZoneId.of("UTC")
        // 23:14 UTC
        val now = ZonedDateTime.of(2026, 8, 22, 23, 14, 0, 0, zone).toInstant().toEpochMilli()

        val rules = Rules(
            maxMinutesPerGrant = 15,
            maxGrantsPerDay = 4,
            minGapMinutes = 20,
            dailyBudgetMinutes = mapOf("com.instagram.android" to 60),
            blackoutWindows = listOf(
                TimeWindow(startMinuteOfDay = 23 * 60 + 30, endMinuteOfDay = 7 * 60) // 23:30 - 07:00
            )
        )

        val lastGrant = GrantHistoryItem(
            id = 1L,
            appId = "com.instagram.android",
            requestedAt = now - 35 * 60 * 1000L,
            plea = "Maya reply",
            proposedMinutes = 20,
            grantedMinutes = 10,
            mode = AccessMode.GRAYSCALE,
            promise = "just replying to Maya",
            endedAt = now - 25 * 60 * 1000L, // ended 25 mins ago
            overranBy = 14,
            honoured = false
        )

        val snapshot = LedgerSnapshot(
            appId = "com.instagram.android",
            todayUsageMinutes = 47,
            todayDailyBudgetMinutes = 60,
            todayGrantsCount = 3,
            lastGrant = lastGrant,
            weekHonouredCount = 4,
            weekTotalPromisesCount = 9,
            userGoal = "stop losing evenings to reels"
        )

        val digest = LedgerDigest.buildDigest("Instagram", snapshot, rules, now, zone)

        assertThat(digest).contains("App: Instagram")
        assertThat(digest).contains("Today: 47 min used of 60 min budget. 3 of 4 grants used.")
        assertThat(digest).contains("Last grant: 25 min ago — asked for 20, got 10 (grayscale).")
        assertThat(digest).contains("Promised: \"just replying to Maya\". Overran by 14 min. NOT HONOURED.")
        assertThat(digest).contains("Prior week: 4 of 9 promises honoured.")
        assertThat(digest).contains("User's stated goal (onboarding): \"stop losing evenings to reels\"")
        assertThat(digest).contains("Current time: 23:14 (blackout starts 23:30)")
    }
}
