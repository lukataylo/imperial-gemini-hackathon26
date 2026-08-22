package com.crusty.llm

import com.crusty.model.LedgerSnapshot
import com.crusty.model.Rules
import com.crusty.model.TimeWindow
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class PromptBuilderTest {

    @Test
    fun `buildSystemPrompt incorporates user goal, hard limits, and digest correctly`() {
        val zone = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2026, 8, 22, 18, 0, 0, 0, zone).toInstant().toEpochMilli()

        val rules = Rules(
            maxMinutesPerGrant = 15,
            maxGrantsPerDay = 4,
            minGapMinutes = 20,
            dailyBudgetMinutes = mapOf("com.instagram.android" to 60),
            blackoutWindows = listOf(TimeWindow(1410, 420))
        )

        val snapshot = LedgerSnapshot(
            appId = "com.instagram.android",
            todayUsageMinutes = 20,
            todayDailyBudgetMinutes = 60,
            todayGrantsCount = 1,
            userGoal = "stop losing evenings to reels"
        )

        val systemPrompt = PromptBuilder.buildSystemPrompt("Instagram", snapshot, rules, now, zone)

        assertThat(systemPrompt).contains("You are Crusty, the gatekeeper on this person's phone.")
        assertThat(systemPrompt).contains("stop losing evenings to reels")
        assertThat(systemPrompt).contains("At most 15 minutes in one go")
        assertThat(systemPrompt).contains("3 grants left today")
        assertThat(systemPrompt).contains("40 minutes left in today's Instagram budget")
        assertThat(systemPrompt).contains("propose_access")
    }
}
