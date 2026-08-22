package com.gatekeeper.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.gatekeeper.di.AppContainer
import com.gatekeeper.llm.DownloadState
import com.gatekeeper.model.Rules
import com.gatekeeper.model.TimeWindow
import com.gatekeeper.model.WatchedApp
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    appContainer: AppContainer,
    onComplete: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) }
    var userGoal by remember { mutableStateOf("stop losing evenings to reels") }
    var watchedApps by remember {
        mutableStateOf(
            listOf(
                WatchedApp("com.instagram.android", "Instagram", isWatched = true),
                WatchedApp("com.zhiliaoapp.musically", "TikTok", isWatched = true),
                WatchedApp("com.twitter.android", "X / Twitter", isWatched = true),
                WatchedApp("com.reddit.frontpage", "Reddit", isWatched = true),
                WatchedApp("com.google.android.youtube", "YouTube", isWatched = true)
            )
        )
    }
    var maxPerGrant by remember { mutableFloatStateOf(15f) }
    var dailyBudget by remember { mutableFloatStateOf(60f) }

    val scope = rememberCoroutineScope()
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var downloadStatusText by remember { mutableStateOf("Ready to initialize") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        // Step Indicator
        Text(
            text = "Step $step of 4",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f),
            label = "OnboardingSteps"
        ) { currentStep ->
            when (currentStep) {
                // Step 1: Goal setting
                1 -> Column {
                    Text(
                        text = "What are you trying to change?",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "This goes directly into your on-device gatekeeper's memory. It will quote your own reason back to you when you ask for access.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedTextField(
                        value = userGoal,
                        onValueChange = { userGoal = it },
                        placeholder = { Text("e.g. stop losing evenings to reels") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        minLines = 3
                    )
                }

                // Step 2: App picker
                2 -> Column {
                    Text(
                        text = "Which apps do you want to protect?",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Gatekeeper will intercept launches of these apps and start a local negotiation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(watchedApps) { app ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        watchedApps = watchedApps.map {
                                            if (it.packageName == app.packageName) it.copy(isWatched = !it.isWatched) else it
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = app.appName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Checkbox(
                                        checked = app.isWatched,
                                        onCheckedChange = { checked ->
                                            watchedApps = watchedApps.map {
                                                if (it.packageName == app.packageName) it.copy(isWatched = checked) else it
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Step 3: Hard rails
                3 -> Column {
                    Text(
                        text = "Set your hard limits",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "The model proposes, but policy clamps. Changes to loosen these rules take 2 hours to apply.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Max minutes per grant: ${maxPerGrant.toInt()} min",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = maxPerGrant,
                        onValueChange = { maxPerGrant = it },
                        valueRange = 5f..30f,
                        steps = 4
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Daily budget per app: ${dailyBudget.toInt()} min",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = dailyBudget,
                        onValueChange = { dailyBudget = it },
                        valueRange = 15f..120f,
                        steps = 6
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Default blackout window: 23:30 – 07:00",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Step 4: Model download & ready
                4 -> Column {
                    Text(
                        text = "Getting the model ready",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "About 1.2 GB. It stays on your phone. After this, Gatekeeper works with no connection at all.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Gemma 4 E2B LiteRT-LM",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = downloadStatusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            LinearProgressIndicator(
                                progress = { downloadProgress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            if (!isDownloading && downloadProgress < 100) {
                                FilledTonalButton(
                                    onClick = {
                                        isDownloading = true
                                        scope.launch {
                                            appContainer.modelDownloader.downloadModel().collect { state ->
                                                when (state) {
                                                    is DownloadState.Downloading -> {
                                                        downloadProgress = state.progressPercent
                                                        downloadStatusText = "Downloading: ${state.progressPercent}% (${state.bytesDownloaded / 1_000_000} MB)"
                                                    }
                                                    is DownloadState.Verifying -> {
                                                        downloadStatusText = "Verifying model integrity…"
                                                    }
                                                    is DownloadState.Completed -> {
                                                        downloadProgress = 100
                                                        downloadStatusText = "Ready on device."
                                                        isDownloading = false
                                                        appContainer.inferenceManager.initialize()
                                                    }
                                                    is DownloadState.Error -> {
                                                        downloadStatusText = "Local fallback ready. (Download error: ${state.message})"
                                                        downloadProgress = 100
                                                        isDownloading = false
                                                    }
                                                    else -> {}
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Download On-Device Model")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Navigation bottom row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step > 1) {
                TextButton(onClick = { step-- }) {
                    Text("Back")
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            Button(
                onClick = {
                    if (step < 4) {
                        step++
                    } else {
                        // Finish onboarding and save settings
                        scope.launch {
                            appContainer.settingsRepository.setUserGoal(userGoal)
                            appContainer.settingsRepository.updateWatchedApps(watchedApps)

                            val budgets = watchedApps.associate { it.packageName to dailyBudget.toInt() }
                            val rules = Rules(
                                maxMinutesPerGrant = maxPerGrant.toInt(),
                                maxGrantsPerDay = 4,
                                minGapMinutes = 20,
                                dailyBudgetMinutes = budgets,
                                blackoutWindows = listOf(TimeWindow(1410, 420))
                            )
                            appContainer.settingsRepository.updateRules(rules)
                            appContainer.settingsRepository.setOnboardingCompleted(true)
                            onComplete()
                        }
                    }
                }
            ) {
                Text(if (step == 4) "Get Started" else "Next")
                Spacer(modifier = Modifier.width(4.dp))
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}
