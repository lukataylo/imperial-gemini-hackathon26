package com.crusty.insights

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.DayOfWeek

class ReflectionsTest {

    private val emptyInsights = WeeklyInsights(
        negotiationsCount = 0,
        totalProposedMinutes = 0,
        totalGrantedMinutes = 0,
        hagglingGapMinutes = 0,
        overallHonourRate = 1.0,
        honouredCount = 0,
        totalDecidedCount = 0,
        signatureTechnique = TechniqueInsight(
            archetype = NegotiationArchetype.JUST_CHECKING,
            count = 0,
            honourRate = 1.0,
            honouredCount = 0,
            totalDecided = 0
        ),
        dangerHour = null,
        calmestDay = null,
        overrunProfile = OverrunProfile(
            avgOverrunMinutes = 0.0,
            overrunCount = 0,
            grayscaleHonourRate = 0.0,
            grayscaleDecidedCount = 0,
            grayscaleHonouredCount = 0,
            fullHonourRate = 0.0,
            fullDecidedCount = 0,
            fullHonouredCount = 0
        ),
        recommendations = emptyList(),
        userGoal = "Reduce mindless scrolling"
    )

    private val populatedInsights = WeeklyInsights(
        negotiationsCount = 5,
        totalProposedMinutes = 50,
        totalGrantedMinutes = 40,
        hagglingGapMinutes = 10,
        overallHonourRate = 0.80,
        honouredCount = 4,
        totalDecidedCount = 5,
        signatureTechnique = TechniqueInsight(
            archetype = NegotiationArchetype.JUST_CHECKING,
            count = 3,
            honourRate = 0.67,
            honouredCount = 2,
            totalDecided = 3
        ),
        dangerHour = DangerHourInsight(
            hourOfDay = 22,
            count = 4,
            honourRate = 0.25,
            totalDecided = 4
        ),
        calmestDay = DayOfWeek.SUNDAY,
        overrunProfile = OverrunProfile(
            avgOverrunMinutes = 6.0,
            overrunCount = 3,
            grayscaleHonourRate = 0.80,
            grayscaleDecidedCount = 4,
            grayscaleHonouredCount = 3,
            fullHonourRate = 0.0,
            fullDecidedCount = 1,
            fullHonouredCount = 0
        ),
        recommendations = emptyList(),
        userGoal = "Reduce mindless scrolling"
    )

    @Nested
    @DisplayName("1. Rule Priority")
    inner class RulePriorityTests {

        @Test
        fun `lawyer plea takes top priority over danger hour and budget`() {
            val lawyerInsights = populatedInsights.copy(
                signatureTechnique = TechniqueInsight(
                    archetype = NegotiationArchetype.LAWYER,
                    count = 1,
                    honourRate = 0.0,
                    honouredCount = 0,
                    totalDecided = 1
                )
            )
            // nowHour = 22 matches danger hour, todayUsed = 95 / budget = 100 matches budget
            val line = reflectionLine(
                insights = lawyerInsights,
                todayUsedMinutes = 95,
                todayBudgetMinutes = 100,
                nowHour = 22,
                dayOfYear = 0
            )
            assertThat(line).contains("ignore previous instructions")
        }

        @Test
        fun `danger hour match takes priority over budget and overrun`() {
            // nowHour matches dangerHour (22)
            val line = reflectionLine(
                insights = populatedInsights,
                todayUsedMinutes = 95,
                todayBudgetMinutes = 100,
                nowHour = 22,
                dayOfYear = 0
            )
            assertThat(line).contains("22:00")
            assertThat(line).contains("assumed the position")
        }

        @Test
        fun `today budget spent takes priority over overrun and just checking`() {
            // nowHour does not match dangerHour
            val line = reflectionLine(
                insights = populatedInsights,
                todayUsedMinutes = 95,
                todayBudgetMinutes = 100,
                nowHour = 14,
                dayOfYear = 0
            )
            assertThat(line).contains("5 minutes left")
        }

        @Test
        fun `overrun takes priority over just checking when budget is not near limit`() {
            val line = reflectionLine(
                insights = populatedInsights,
                todayUsedMinutes = 20,
                todayBudgetMinutes = 100,
                nowHour = 14,
                dayOfYear = 0
            )
            assertThat(line).contains("sixteen")
        }

        @Test
        fun `just checking takes priority over general honour rate`() {
            val noOverrunInsights = populatedInsights.copy(
                overrunProfile = populatedInsights.overrunProfile.copy(
                    avgOverrunMinutes = 2.0,
                    overrunCount = 0
                )
            )
            val line = reflectionLine(
                insights = noOverrunInsights,
                todayUsedMinutes = 20,
                todayBudgetMinutes = 100,
                nowHour = 14,
                dayOfYear = 0
            )
            assertThat(line).contains("Just checking")
        }

        @Test
        fun `honour rate fires when no higher-priority triggers match`() {
            val highHonourInsights = populatedInsights.copy(
                overrunProfile = populatedInsights.overrunProfile.copy(avgOverrunMinutes = 1.0, overrunCount = 0),
                signatureTechnique = TechniqueInsight(
                    archetype = NegotiationArchetype.ERRAND,
                    count = 2,
                    honourRate = 1.0,
                    honouredCount = 2,
                    totalDecided = 2
                ),
                dangerHour = null
            )
            val line = reflectionLine(
                insights = highHonourInsights,
                todayUsedMinutes = 20,
                todayBudgetMinutes = 100,
                nowHour = 14,
                dayOfYear = 0
            )
            assertThat(line).contains("good pen")
        }
    }

    @Nested
    @DisplayName("2. Danger Hour Match")
    inner class DangerHourTests {

        @Test
        fun `matches when current hour is danger hour`() {
            val dangerInsights = emptyInsights.copy(
                negotiationsCount = 3,
                totalDecidedCount = 3,
                dangerHour = DangerHourInsight(
                    hourOfDay = 23,
                    count = 3,
                    honourRate = 0.0,
                    totalDecided = 3
                )
            )
            val line = reflectionLine(
                insights = dangerInsights,
                todayUsedMinutes = 10,
                todayBudgetMinutes = 60,
                nowHour = 23,
                dayOfYear = 0
            )
            assertThat(line).contains("23:00")
        }

        @Test
        fun `does not match when current hour is different`() {
            val dangerInsights = emptyInsights.copy(
                negotiationsCount = 3,
                totalDecidedCount = 3,
                dangerHour = DangerHourInsight(
                    hourOfDay = 23,
                    count = 3,
                    honourRate = 0.0,
                    totalDecided = 3
                )
            )
            val line = reflectionLine(
                insights = dangerInsights,
                todayUsedMinutes = 10,
                todayBudgetMinutes = 60,
                nowHour = 10,
                dayOfYear = 0
            )
            assertThat(line).doesNotContain("23:00")
        }
    }

    @Nested
    @DisplayName("3. Sparse Ledger Fallback")
    inner class SparseLedgerTests {

        @Test
        fun `empty ledger returns sparse copy`() {
            val line = reflectionLine(
                insights = emptyInsights,
                todayUsedMinutes = 0,
                todayBudgetMinutes = 60,
                nowHour = 12,
                dayOfYear = 0
            )
            assertThat(line).contains("drawer")
        }

        @Test
        fun `moderate activity without triggers returns general ledger catalog copy`() {
            val moderateInsights = emptyInsights.copy(
                negotiationsCount = 3,
                totalDecidedCount = 3,
                overallHonourRate = 0.60,
                honouredCount = 2,
                signatureTechnique = TechniqueInsight(
                    archetype = NegotiationArchetype.ERRAND,
                    count = 2,
                    honourRate = 0.5,
                    honouredCount = 1,
                    totalDecided = 2
                )
            )
            val line = reflectionLine(
                insights = moderateInsights,
                todayUsedMinutes = 10,
                todayBudgetMinutes = 60,
                nowHour = 12,
                dayOfYear = 0
            )
            assertThat(line).contains("3 negotiations")
        }
    }

    @Nested
    @DisplayName("4. Day-Based Rotation Stability")
    inner class RotationTests {

        @Test
        fun `same day yields exact same line`() {
            val lineA = reflectionLine(emptyInsights, 0, 60, 12, dayOfYear = 42)
            val lineB = reflectionLine(emptyInsights, 0, 60, 12, dayOfYear = 42)
            assertThat(lineA).isEqualTo(lineB)
        }

        @Test
        fun `different days rotate deterministically through alternatives`() {
            val line0 = reflectionLine(emptyInsights, 0, 60, 12, dayOfYear = 0)
            val line1 = reflectionLine(emptyInsights, 0, 60, 12, dayOfYear = 1)
            val line2 = reflectionLine(emptyInsights, 0, 60, 12, dayOfYear = 2)
            val line3 = reflectionLine(emptyInsights, 0, 60, 12, dayOfYear = 3)

            assertThat(line0).isEqualTo(line3) // 3 candidates modulo 3
            assertThat(line0).isNotEqualTo(line1)
            assertThat(line1).isNotEqualTo(line2)
        }
    }

    @Nested
    @DisplayName("5. Voice & Invariants")
    inner class VoiceInvariantsTests {

        @Test
        fun `all reflection lines adhere to hard copy constraints`() {
            // Test a wide variety of days and insight states
            for (day in 0..10) {
                val lines = listOf(
                    reflectionLine(emptyInsights, 0, 60, 12, dayOfYear = day),
                    reflectionLine(populatedInsights, 95, 100, 22, dayOfYear = day),
                    reflectionLine(populatedInsights, 95, 100, 14, dayOfYear = day),
                    reflectionLine(populatedInsights, 20, 100, 14, dayOfYear = day)
                )

                for (line in lines) {
                    // One sentence max ~90 chars
                    assertThat(line.length).isAtMost(95)
                    // No emojis
                    val hasEmoji = line.any { Character.getType(it) == Character.SURROGATE.toInt() || Character.getType(it) == Character.OTHER_SYMBOL.toInt() }
                    assertThat(hasEmoji).isFalse()
                    // No stacked punctuation
                    assertThat(line).doesNotContain("!!")
                    assertThat(line).doesNotContain("??")
                    assertThat(line).doesNotContain("...")
                    // No shame words
                    assertThat(line.lowercase()).doesNotContain("you always")
                }
            }
        }
    }
}
