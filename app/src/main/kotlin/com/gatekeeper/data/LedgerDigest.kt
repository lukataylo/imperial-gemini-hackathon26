package com.gatekeeper.data

import com.gatekeeper.model.AccessMode
import com.gatekeeper.model.LedgerSnapshot
import com.gatekeeper.model.Rules
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object LedgerDigest {

    /**
     * Builds the compact (<=250 tokens) digest string that Gemma sees.
     * Matches the format defined in 02-ARCHITECTURE.md and 05-PROMPTS.md.
     */
    fun buildDigest(
        appName: String,
        snapshot: LedgerSnapshot,
        rules: Rules,
        now: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val sb = StringBuilder()

        // 1. App line
        sb.append("App: ").append(appName).append("\n")

        // 2. Today line
        val dailyBudget = rules.dailyBudgetMinutes[snapshot.appId]
            ?: snapshot.todayDailyBudgetMinutes.takeIf { it > 0 }
            ?: 60
        sb.append("Today: ")
            .append(snapshot.todayUsageMinutes)
            .append(" min used of ")
            .append(dailyBudget)
            .append(" min budget. ")
            .append(snapshot.todayGrantsCount)
            .append(" of ")
            .append(rules.maxGrantsPerDay)
            .append(" grants used.\n")

        // 3. Last grant line (if any)
        val last = snapshot.lastGrant
        if (last != null) {
            val lastEndedAt = last.endedAt ?: (last.requestedAt + last.grantedMinutes * 60 * 1000L)
            val elapsedMin = ((now - lastEndedAt) / (60 * 1000L)).coerceAtLeast(0)
            val modeStr = when (last.mode) {
                AccessMode.GRAYSCALE -> " (grayscale)"
                AccessMode.DELAYED -> " (delayed)"
                AccessMode.NO_FEED -> " (no feed)"
                AccessMode.SEARCH_ONLY -> " (search only)"
                AccessMode.FULL -> ""
            }
            sb.append("Last grant: ")
                .append(elapsedMin)
                .append(" min ago — asked for ")
                .append(last.proposedMinutes)
                .append(", got ")
                .append(last.grantedMinutes)
                .append(modeStr)
                .append(".\n")

            if (last.promise.isNotBlank()) {
                sb.append("  Promised: \"").append(last.promise).append("\". ")
                if (last.honoured == true) {
                    sb.append("HONOURED.\n")
                } else if (last.overranBy != null && last.overranBy > 0) {
                    sb.append("Overran by ").append(last.overranBy).append(" min. NOT HONOURED.\n")
                } else if (last.honoured == false) {
                    sb.append("NOT HONOURED.\n")
                } else {
                    sb.append("\n")
                }
            }
        }

        // 4. Prior week line
        if (snapshot.weekTotalPromisesCount > 0) {
            sb.append("Prior week: ")
                .append(snapshot.weekHonouredCount)
                .append(" of ")
                .append(snapshot.weekTotalPromisesCount)
                .append(" promises honoured.\n")
        }

        // 5. User's stated goal
        if (snapshot.userGoal.isNotBlank()) {
            sb.append("User's stated goal (onboarding): \"")
                .append(snapshot.userGoal)
                .append("\"\n")
        }

        // 6. Current time and upcoming blackout
        val zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zoneId)
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val currentTimeStr = zonedDateTime.format(timeFormatter)
        val currentMinuteOfDay = zonedDateTime.hour * 60 + zonedDateTime.minute

        val upcomingBlackout = rules.blackoutWindows.firstOrNull { window ->
            window.contains(currentMinuteOfDay) || (window.startMinuteOfDay > currentMinuteOfDay && window.startMinuteOfDay - currentMinuteOfDay <= 60)
        }

        if (upcomingBlackout != null) {
            val blackoutHour = upcomingBlackout.startMinuteOfDay / 60
            val blackoutMin = upcomingBlackout.startMinuteOfDay % 60
            val blackoutTimeStr = String.format("%02d:%02d", blackoutHour, blackoutMin)
            if (upcomingBlackout.contains(currentMinuteOfDay)) {
                sb.append("Current time: ").append(currentTimeStr).append(" (blackout active until ")
                val endHour = upcomingBlackout.endMinuteOfDay / 60
                val endMin = upcomingBlackout.endMinuteOfDay % 60
                sb.append(String.format("%02d:%02d", endHour, endMin)).append(")")
            } else {
                sb.append("Current time: ").append(currentTimeStr).append(" (blackout starts ")
                    .append(blackoutTimeStr).append(")")
            }
        } else {
            sb.append("Current time: ").append(currentTimeStr)
        }

        return sb.toString().trim()
    }
}
