package com.crusty.ui

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
import androidx.compose.foundation.layout.imePadding
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
import com.crusty.di.AppContainer
import com.crusty.llm.DownloadState
import com.crusty.model.Rules
import com.crusty.model.TimeWindow
import com.crusty.model.WatchedApp
import kotlinx.coroutines.launch
import com.crusty.R
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.layout.widthIn

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
                *com.crusty.data.SUPPORTED_APPS.toTypedArray()
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
            .imePadding()
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
                        text = "This goes directly into Crusty's on-device memory. It will quote your own reason back to you when you ask for access.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    OutlinedTextField(
                        value = userGoal,
                        onValueChange = { userGoal = it },
                        placeholder = { Text("Tell Crusty your reason\u2026") },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        minLines = 1
                    )

                    Spacer(modifier = Modifier.height(36.dp))

                    // Crusty, with a nudge
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.crusty_peeking),
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxWidth(0.58f)
                                .aspectRatio(693f / 800f)
                        )
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 16.dp)
                                .widthIn(max = 176.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = "Honesty helps me help you better.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
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
                        text = "Crusty will intercept launches of these apps and start a local negotiation.",
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
                        text = "About 2.6 GB. It stays on your phone. After this, Crusty works with no connection at all.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // Crusty warming up, with a speech bubble tucked to his right
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.crusty_peeking),
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth(0.56f)
                                .aspectRatio(693f / 800f)
                        )
                        Surface(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(top = 24.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = if (downloadProgress >= 100) "All set!" else "Warming up\nfor you!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.crusty_peeking),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "Gemma 4 E2B LiteRT-LM",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = downloadStatusText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))

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
