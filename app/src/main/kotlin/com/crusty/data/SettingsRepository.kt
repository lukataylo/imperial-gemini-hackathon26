package com.crusty.data

import android.content.Context
import com.crusty.model.Rules
import com.crusty.model.TimeWindow
import com.crusty.model.WatchedApp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SettingsData(
    val rules: Rules = Rules(
        maxMinutesPerGrant = 15,
        maxGrantsPerDay = 4,
        // 0 by default: after a grant expires the user may argue again immediately,
        // but the ledger means they argue from a worse position. A non-zero gap turns
        // the re-open into a flat denial with no agent, which is not the product.
        minGapMinutes = 0,
        dailyBudgetMinutes = mapOf("com.instagram.android" to 60),
        blackoutWindows = listOf(
            TimeWindow(startMinuteOfDay = 23 * 60 + 30, endMinuteOfDay = 7 * 60)
        )
    ),
    val userGoal: String = "stop losing evenings to reels",
    val watchedApps: List<WatchedApp> = listOf(
        WatchedApp(packageName = "com.instagram.android", appName = "Instagram", isWatched = true),
        WatchedApp(packageName = "com.zhiliaoapp.musically", appName = "TikTok", isWatched = true),
        WatchedApp(packageName = "com.twitter.android", appName = "X / Twitter", isWatched = true),
        WatchedApp(packageName = "com.reddit.frontpage", appName = "Reddit", isWatched = true),
        WatchedApp(packageName = "com.google.android.youtube", appName = "YouTube", isWatched = true)
    ),
    val onboardingCompleted: Boolean = false,
    val interceptionEnabled: Boolean = true,
    val customModelPath: String? = null,
)

interface SettingsRepository {
    val settingsData: StateFlow<SettingsData>

    suspend fun getRules(): Rules
    suspend fun updateRules(newRules: Rules)
    suspend fun getUserGoal(): String
    suspend fun setUserGoal(goal: String)
    suspend fun getWatchedApps(): List<WatchedApp>
    suspend fun updateWatchedApps(apps: List<WatchedApp>)
    suspend fun isOnboardingCompleted(): Boolean
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun isInterceptionEnabled(): Boolean
    suspend fun setInterceptionEnabled(enabled: Boolean)
    suspend fun getCustomModelPath(): String?
    suspend fun setCustomModelPath(path: String?)
}

class JsonSettingsRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SettingsRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val mutex = Mutex()
    private val file: File get() = File(context.filesDir, "settings_data.json")

    private val _settingsData = MutableStateFlow(SettingsData())
    override val settingsData: StateFlow<SettingsData> = _settingsData.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        try {
            if (file.exists()) {
                val content = file.readText()
                val data = json.decodeFromString<SettingsData>(content)
                _settingsData.value = data
            }
        } catch (_: Exception) {
            _settingsData.value = SettingsData()
        }
    }

    private suspend fun persistLocked() {
        withContext(ioDispatcher) {
            try {
                val text = json.encodeToString(_settingsData.value)
                file.writeText(text)
            } catch (_: Exception) {}
        }
    }

    override suspend fun getRules(): Rules = mutex.withLock {
        _settingsData.value.rules
    }

    override suspend fun updateRules(newRules: Rules): Unit = mutex.withLock {
        _settingsData.value = _settingsData.value.copy(rules = newRules)
        persistLocked()
    }

    override suspend fun getUserGoal(): String = mutex.withLock {
        _settingsData.value.userGoal
    }

    override suspend fun setUserGoal(goal: String): Unit = mutex.withLock {
        _settingsData.value = _settingsData.value.copy(userGoal = goal)
        persistLocked()
    }

    override suspend fun getWatchedApps(): List<WatchedApp> = mutex.withLock {
        _settingsData.value.watchedApps
    }

    override suspend fun updateWatchedApps(apps: List<WatchedApp>): Unit = mutex.withLock {
        _settingsData.value = _settingsData.value.copy(watchedApps = apps)
        persistLocked()
    }

    override suspend fun isOnboardingCompleted(): Boolean = mutex.withLock {
        _settingsData.value.onboardingCompleted
    }

    override suspend fun setOnboardingCompleted(completed: Boolean): Unit = mutex.withLock {
        _settingsData.value = _settingsData.value.copy(onboardingCompleted = completed)
        persistLocked()
    }

    override suspend fun isInterceptionEnabled(): Boolean = mutex.withLock {
        _settingsData.value.interceptionEnabled
    }

    override suspend fun setInterceptionEnabled(enabled: Boolean): Unit = mutex.withLock {
        _settingsData.value = _settingsData.value.copy(interceptionEnabled = enabled)
        persistLocked()
    }

    override suspend fun getCustomModelPath(): String? = mutex.withLock {
        _settingsData.value.customModelPath
    }

    override suspend fun setCustomModelPath(path: String?): Unit = mutex.withLock {
        _settingsData.value = _settingsData.value.copy(customModelPath = path)
        persistLocked()
    }
}
