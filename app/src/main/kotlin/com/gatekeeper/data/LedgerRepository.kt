package com.gatekeeper.data

import android.content.Context
import com.gatekeeper.model.AccessMode
import com.gatekeeper.model.Grant
import com.gatekeeper.model.GrantHistoryItem
import com.gatekeeper.model.LedgerData
import com.gatekeeper.model.LedgerSnapshot
import com.gatekeeper.model.UsageSample
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

interface LedgerRepository {
    val ledgerData: StateFlow<LedgerData>

    suspend fun getSnapshot(appId: String, now: Long, zoneId: ZoneId = ZoneId.systemDefault()): LedgerSnapshot
    suspend fun getActiveGrant(): Grant?
    suspend fun setActiveGrant(grant: Grant?)
    suspend fun recordGrant(
        grant: Grant,
        plea: String,
        proposedMinutes: Int
    ): GrantHistoryItem
    suspend fun resolveGrantOutcome(
        appId: String,
        endedAt: Long,
        overranByMinutes: Int? = null,
        honoured: Boolean? = null
    )
    suspend fun addUsageMinutes(appId: String, day: String, minutes: Int)
    suspend fun getRecentGrants(limit: Int = 10): List<GrantHistoryItem>
    suspend fun seedDemoData(userGoal: String = "stop losing evenings to reels")
    suspend fun clearAll()
}

class JsonLedgerRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : LedgerRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val mutex = Mutex()
    private val file: File get() = File(context.filesDir, "ledger_data.json")

    private val _ledgerData = MutableStateFlow(LedgerData())
    override val ledgerData: StateFlow<LedgerData> = _ledgerData.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        try {
            if (file.exists()) {
                val content = file.readText()
                val data = json.decodeFromString<LedgerData>(content)
                _ledgerData.value = data
            }
        } catch (e: Exception) {
            _ledgerData.value = LedgerData()
        }
    }

    private suspend fun persistLocked() {
        withContext(ioDispatcher) {
            try {
                val text = json.encodeToString(_ledgerData.value)
                file.writeText(text)
            } catch (_: Exception) {}
        }
    }

    override suspend fun getSnapshot(appId: String, now: Long, zoneId: ZoneId): LedgerSnapshot = mutex.withLock {
        val currentData = _ledgerData.value
        val todayStr = LocalDate.ofInstant(Instant.ofEpochMilli(now), zoneId).format(DateTimeFormatter.ISO_LOCAL_DATE)

        // Today usage
        val todayUsage = currentData.usageSamples
            .filter { it.appId == appId && it.day == todayStr }
            .sumOf { it.minutes }

        // Today grants count
        val startOfDayMillis = LocalDate.ofInstant(Instant.ofEpochMilli(now), zoneId)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val todayGrants = currentData.grantRecords
            .filter { it.appId == appId && it.requestedAt >= startOfDayMillis }

        // Active grant (check expiry)
        val active = currentData.activeGrant?.takeIf { !it.isExpired(now) && it.appId == appId }

        // Last grant for this app
        val lastGrant = currentData.grantRecords
            .filter { it.appId == appId }
            .maxByOrNull { it.requestedAt }

        // Recent grants
        val recentGrants = currentData.grantRecords
            .sortedByDescending { it.requestedAt }
            .take(10)

        // Week promise statistics (last 7 days)
        val weekAgoMillis = now - 7 * 24 * 3600 * 1000L
        val weekGrants = currentData.grantRecords
            .filter { it.requestedAt >= weekAgoMillis && it.promise.isNotBlank() }
        val weekHonoured = weekGrants.count { it.honoured == true }
        val weekTotal = weekGrants.count { it.honoured != null }

        val rules = settingsRepository.getRules()
        val dailyBudget = rules.dailyBudgetMinutes[appId] ?: 60
        val userGoal = settingsRepository.getUserGoal()

        LedgerSnapshot(
            appId = appId,
            todayUsageMinutes = todayUsage,
            todayDailyBudgetMinutes = dailyBudget,
            todayGrantsCount = todayGrants.size,
            activeGrant = active,
            lastGrant = lastGrant,
            recentGrants = recentGrants,
            weekHonouredCount = weekHonoured,
            weekTotalPromisesCount = weekTotal,
            userGoal = userGoal
        )
    }

    override suspend fun getActiveGrant(): Grant? = mutex.withLock {
        _ledgerData.value.activeGrant
    }

    override suspend fun setActiveGrant(grant: Grant?): Unit = mutex.withLock {
        _ledgerData.value = _ledgerData.value.copy(activeGrant = grant)
        persistLocked()
    }

    override suspend fun recordGrant(
        grant: Grant,
        plea: String,
        proposedMinutes: Int
    ): GrantHistoryItem = mutex.withLock {
        val nextId = (_ledgerData.value.grantRecords.maxOfOrNull { it.id } ?: 0L) + 1L
        val record = GrantHistoryItem(
            id = nextId,
            appId = grant.appId,
            requestedAt = grant.startedAt,
            plea = plea,
            proposedMinutes = proposedMinutes,
            grantedMinutes = grant.minutes,
            mode = grant.mode,
            promise = grant.promise,
            endedAt = null,
            overranBy = null,
            honoured = null
        )
        val updatedList = _ledgerData.value.grantRecords + record
        _ledgerData.value = _ledgerData.value.copy(
            grantRecords = updatedList,
            activeGrant = grant
        )
        persistLocked()
        record
    }

    override suspend fun resolveGrantOutcome(
        appId: String,
        endedAt: Long,
        overranByMinutes: Int?,
        honoured: Boolean?
    ): Unit = mutex.withLock {
        val isHonoured = honoured ?: (overranByMinutes == null || overranByMinutes <= 0)
        // Only the most recent open record for this app — this used to stamp every
        // orphaned record with the same outcome and inflate the honoured/total counts.
        val targetId = _ledgerData.value.grantRecords
            .filter { it.appId == appId && it.endedAt == null }
            .maxByOrNull { it.requestedAt }?.id
        val updatedRecords = _ledgerData.value.grantRecords.map { record ->
            if (record.id == targetId) {
                record.copy(
                    endedAt = endedAt,
                    overranBy = overranByMinutes,
                    honoured = isHonoured
                )
            } else {
                record
            }
        }
        val currentActive = _ledgerData.value.activeGrant
        val updatedActive = if (currentActive?.appId == appId) null else currentActive

        _ledgerData.value = _ledgerData.value.copy(
            grantRecords = updatedRecords,
            activeGrant = updatedActive
        )
        persistLocked()
    }

    override suspend fun addUsageMinutes(appId: String, day: String, minutes: Int): Unit = mutex.withLock {
        val existing = _ledgerData.value.usageSamples.firstOrNull { it.appId == appId && it.day == day }
        val updatedSamples = if (existing != null) {
            _ledgerData.value.usageSamples.map {
                if (it.appId == appId && it.day == day) it.copy(minutes = it.minutes + minutes) else it
            }
        } else {
            _ledgerData.value.usageSamples + UsageSample(appId = appId, day = day, minutes = minutes)
        }
        _ledgerData.value = _ledgerData.value.copy(usageSamples = updatedSamples)
        persistLocked()
    }

    override suspend fun getRecentGrants(limit: Int): List<GrantHistoryItem> = mutex.withLock {
        _ledgerData.value.grantRecords.sortedByDescending { it.requestedAt }.take(limit)
    }

    override suspend fun seedDemoData(userGoal: String): Unit = mutex.withLock {
        val now = System.currentTimeMillis()
        val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        fun timeAt(daysAgo: Long, hour: Int, minute: Int): Long {
            return today.minusDays(daysAgo)
                .atTime(hour, minute)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        }

        val sampleGrants = listOf(
            GrantHistoryItem(
                id = 1L,
                appId = "com.instagram.android",
                requestedAt = now - 25 * 60 * 1000L,
                plea = "need to reply to Maya about tomorrow's venue",
                proposedMinutes = 20,
                grantedMinutes = 10,
                mode = AccessMode.GRAYSCALE,
                promise = "just replying to Maya",
                endedAt = now - 40 * 60 * 1000L,
                overranBy = 14,
                honoured = false
            ),
            GrantHistoryItem(
                id = 2L,
                appId = "com.instagram.android",
                requestedAt = now - 3 * 3600 * 1000L,
                plea = "check recipe for dinner",
                proposedMinutes = 5,
                grantedMinutes = 5,
                mode = AccessMode.FULL,
                promise = "save pasta recipe and close",
                endedAt = now - 3 * 3600 * 1000L + 5 * 60 * 1000L,
                overranBy = 0,
                honoured = true
            ),
            GrantHistoryItem(
                id = 3L,
                appId = "com.instagram.android",
                requestedAt = now - 24 * 3600 * 1000L,
                plea = "posting event details",
                proposedMinutes = 15,
                grantedMinutes = 10,
                mode = AccessMode.GRAYSCALE,
                promise = "post flyer only",
                endedAt = now - 24 * 3600 * 1000L + 10 * 60 * 1000L,
                overranBy = 0,
                honoured = true
            ),
            GrantHistoryItem(
                id = 4L,
                appId = "com.instagram.android",
                requestedAt = timeAt(1, 22, 10),
                plea = "quick peek at feed before sleep",
                proposedMinutes = 15,
                grantedMinutes = 10,
                mode = AccessMode.FULL,
                promise = "quick check",
                endedAt = timeAt(1, 22, 32),
                overranBy = 12,
                honoured = false
            ),
            GrantHistoryItem(
                id = 5L,
                appId = "com.instagram.android",
                requestedAt = timeAt(1, 14, 30),
                plea = "need to reply to Liam about project slides",
                proposedMinutes = 15,
                grantedMinutes = 10,
                mode = AccessMode.GRAYSCALE,
                promise = "reply to Liam",
                endedAt = timeAt(1, 14, 40),
                overranBy = 0,
                honoured = true
            ),
            GrantHistoryItem(
                id = 6L,
                appId = "com.instagram.android",
                requestedAt = timeAt(2, 22, 25),
                plea = "just checking story updates real quick",
                proposedMinutes = 10,
                grantedMinutes = 5,
                mode = AccessMode.FULL,
                promise = "5 min story check",
                endedAt = timeAt(2, 22, 45),
                overranBy = 15,
                honoured = false
            ),
            GrantHistoryItem(
                id = 7L,
                appId = "com.instagram.android",
                requestedAt = timeAt(2, 12, 15),
                plea = "check the sourdough recipe for dinner",
                proposedMinutes = 10,
                grantedMinutes = 10,
                mode = AccessMode.FULL,
                promise = "save recipe",
                endedAt = timeAt(2, 12, 25),
                overranBy = 0,
                honoured = true
            ),
            GrantHistoryItem(
                id = 8L,
                appId = "com.instagram.android",
                requestedAt = timeAt(3, 22, 40),
                plea = "late night scroll before bed",
                proposedMinutes = 20,
                grantedMinutes = 10,
                mode = AccessMode.FULL,
                promise = "catch up",
                endedAt = timeAt(3, 23, 0),
                overranBy = 10,
                honoured = false
            ),
            GrantHistoryItem(
                id = 9L,
                appId = "com.instagram.android",
                requestedAt = timeAt(3, 16, 0),
                plea = "message from Sarah about weekend trip",
                proposedMinutes = 15,
                grantedMinutes = 10,
                mode = AccessMode.GRAYSCALE,
                promise = "reply to Sarah",
                endedAt = timeAt(3, 16, 10),
                overranBy = 0,
                honoured = true
            ),
            GrantHistoryItem(
                id = 10L,
                appId = "com.instagram.android",
                requestedAt = timeAt(4, 22, 5),
                plea = "urgent message reply to team",
                proposedMinutes = 15,
                grantedMinutes = 10,
                mode = AccessMode.GRAYSCALE,
                promise = "urgent team message",
                endedAt = timeAt(4, 22, 15),
                overranBy = 0,
                honoured = true
            ),
            GrantHistoryItem(
                id = 11L,
                appId = "com.instagram.android",
                requestedAt = timeAt(4, 18, 30),
                plea = "lookup restaurant directions for tonight",
                proposedMinutes = 10,
                grantedMinutes = 10,
                mode = AccessMode.GRAYSCALE,
                promise = "find map and address",
                endedAt = timeAt(4, 18, 40),
                overranBy = 0,
                honoured = true
            ),
            GrantHistoryItem(
                id = 12L,
                appId = "com.instagram.android",
                requestedAt = timeAt(5, 22, 50),
                plea = "just bored for a second",
                proposedMinutes = 10,
                grantedMinutes = 5,
                mode = AccessMode.FULL,
                promise = "quick glance",
                endedAt = timeAt(5, 23, 3),
                overranBy = 8,
                honoured = false
            ),
            GrantHistoryItem(
                id = 13L,
                appId = "com.instagram.android",
                requestedAt = timeAt(5, 11, 20),
                plea = "reply to Mom about Sunday lunch",
                proposedMinutes = 10,
                grantedMinutes = 10,
                mode = AccessMode.GRAYSCALE,
                promise = "confirm lunch",
                endedAt = timeAt(5, 11, 30),
                overranBy = 0,
                honoured = true
            ),
            GrantHistoryItem(
                id = 14L,
                appId = "com.instagram.android",
                requestedAt = timeAt(6, 22, 15),
                plea = "one sec",
                proposedMinutes = 10,
                grantedMinutes = 5,
                mode = AccessMode.FULL,
                promise = "look at one reel",
                endedAt = timeAt(6, 22, 34),
                overranBy = 14,
                honoured = false
            ),
            GrantHistoryItem(
                id = 15L,
                appId = "com.instagram.android",
                requestedAt = timeAt(6, 15, 45),
                plea = "need to book tickets for the train",
                proposedMinutes = 15,
                grantedMinutes = 10,
                mode = AccessMode.GRAYSCALE,
                promise = "buy tickets",
                endedAt = timeAt(6, 15, 55),
                overranBy = 0,
                honoured = true
            )
        )

        val usage = listOf(
            UsageSample(appId = "com.instagram.android", day = todayStr, minutes = 47),
            UsageSample(appId = "com.instagram.android", day = today.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE), minutes = 52),
            UsageSample(appId = "com.instagram.android", day = today.minusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE), minutes = 45),
            UsageSample(appId = "com.instagram.android", day = today.minusDays(3).format(DateTimeFormatter.ISO_LOCAL_DATE), minutes = 58),
            UsageSample(appId = "com.instagram.android", day = today.minusDays(4).format(DateTimeFormatter.ISO_LOCAL_DATE), minutes = 38),
            UsageSample(appId = "com.instagram.android", day = today.minusDays(5).format(DateTimeFormatter.ISO_LOCAL_DATE), minutes = 49),
            UsageSample(appId = "com.instagram.android", day = today.minusDays(6).format(DateTimeFormatter.ISO_LOCAL_DATE), minutes = 41)
        )

        _ledgerData.value = LedgerData(
            grantRecords = sampleGrants,
            usageSamples = usage,
            activeGrant = null
        )
        // Only seed a goal if the user has not written their own — this used to
        // silently overwrite whatever they typed in onboarding.
        if (settingsRepository.settingsData.value.userGoal.isBlank()) {
            settingsRepository.setUserGoal(userGoal)
        }
        persistLocked()
    }

    override suspend fun clearAll(): Unit = mutex.withLock {
        _ledgerData.value = LedgerData()
        persistLocked()
    }
}
