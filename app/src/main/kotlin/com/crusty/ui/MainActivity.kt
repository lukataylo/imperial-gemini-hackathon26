package com.crusty.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.crusty.CrustyApp
import com.crusty.enforce.BlockActivity
import com.crusty.model.GrantHistoryItem
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as CrustyApp).container

        setContent {
            CrustyTheme {
                val settings by appContainer.settingsRepository.settingsData.collectAsState()
                val ledger by appContainer.ledgerRepository.ledgerData.collectAsState()
                val scope = rememberCoroutineScope()

                var showSettings by remember { mutableStateOf(false) }

                if (!settings.onboardingCompleted) {
                    OnboardingScreen(
                        appContainer = appContainer,
                        onComplete = {
                            // Request accessibility permission if needed
                            try {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    )
                } else if (showSettings) {
                    SettingsScreen(
                        appContainer = appContainer,
                        onBack = { showSettings = false }
                    )
                } else {
                    val todayUsageMap = remember(ledger.usageSamples) {
                        ledger.usageSamples.associate { it.appId to it.minutes }
                    }
                    val recentGrants = remember(ledger.grantRecords) {
                        ledger.grantRecords.sortedByDescending { it.requestedAt }.take(10)
                    }
                    val openPromise: GrantHistoryItem? = remember(ledger.grantRecords) {
                        ledger.grantRecords.lastOrNull { it.honoured == null && it.promise.isNotBlank() }
                    }

                    HomeScreen(
                        watchedApps = settings.watchedApps,
                        rules = settings.rules,
                        todayUsage = todayUsageMap,
                        openPromise = openPromise,
                        recentGrants = recentGrants,
                        onOpenSettings = { showSettings = true },
                        onSeedDemoData = {
                            scope.launch {
                                appContainer.ledgerRepository.seedDemoData()
                            }
                        },
                        onTestNegotiation = { appId ->
                            BlockActivity.launch(this@MainActivity, appId)
                        }
                    )
                }
            }
        }
    }
}
