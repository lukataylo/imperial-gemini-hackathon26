package com.crusty.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.crusty.model.AccessMode
import com.crusty.model.GrantHistoryItem
import com.crusty.model.Rules
import com.crusty.model.WatchedApp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.crusty.R
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.Image
import androidx.compose.material3.HorizontalDivider

@Composable
fun HomeScreen(
    watchedApps: List<WatchedApp>,
    rules: Rules,
    todayUsage: Map<String, Int>,
    openPromise: GrantHistoryItem?,
    recentGrants: List<GrantHistoryItem>,
    onOpenSettings: () -> Unit,
    onOpenWrapped: () -> Unit,
    onSeedDemoData: () -> Unit,
    onResetLedger: () -> Unit = {},
    onTestNegotiation: (String) -> Unit,
    insight: String? = null,
    insightLoading: Boolean = false,
    insightAvailable: Boolean = false,
    onRunInsight: () -> Unit = {}
) {
    // Only show meters for apps that are actually on this device — a budget bar for an
    // app you don't have is noise, and it makes the demo look staged.
    val packageManager = LocalContext.current.packageManager
    val installedWatched = remember(watchedApps) {
        watchedApps.filter { it.isWatched && isAppInstalled(packageManager, it.packageName) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Title row: wordmark + tagline, Crusty, then settings
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Crusty",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Stay focused. Win your time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Image(
                painter = painterResource(id = R.drawable.crusty_happy),
                contentDescription = null,
                modifier = Modifier
                    .height(72.dp)
                    .aspectRatio(568f / 640f)
            )

            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Weekly Wrapped Entry Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenWrapped),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Your week with Crusty →",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Weekly recap, negotiation stats & insights",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section 1: Today Usage (Screen 4 in 03-UI-SPEC.md)
            item {
                Text(
                    text = "Today",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (installedWatched.isEmpty()) {
                            Text(
                                text = "None of your watched apps are installed on this device.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        installedWatched.forEach { app ->
                            val used = todayUsage[app.packageName] ?: 0
                            val budget = rules.dailyBudgetMinutes[app.packageName] ?: 60
                            val progress = (used.toFloat() / budget.toFloat()).coerceIn(0f, 1f)
                            val isOver = used >= budget

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppIcon(packageName = app.packageName, size = 32.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = app.appName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "$used of $budget min",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = if (isOver) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                )
                            }
                        }
                    }
                }
            }

            // Section: cloud reflection. Gemma negotiates on-device; Gemini reads the
            // aggregate history, where a bigger model earns its keep and latency is irrelevant.
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Weekly reflection",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (insightAvailable) {
                        TextButton(onClick = onRunInsight, enabled = !insightLoading) {
                            Text(
                                if (insightLoading) "Thinking…" else "Analyse",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = insight
                                ?: "Gemini reads your history in the cloud and tells you what " +
                                "you can't see from inside the habit. Only aggregates are sent — " +
                                "never what you typed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (insight != null) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Section 2: Open Promise (if any)
            if (openPromise != null && openPromise.promise.isNotBlank()) {
                item {
                    Text(
                        text = "Open promise",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "\"${openPromise.promise}\"",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val appName = watchedApps.firstOrNull { it.packageName == openPromise.appId }?.appName ?: openPromise.appId
                            Text(
                                text = "Committed for $appName (${openPromise.grantedMinutes} min granted)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Section 3: Recent Negotiations
            item {
                Text(
                    text = "Recent negotiations",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (recentGrants.isEmpty()) {
                    Text(
                        text = "No negotiations yet today.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (recentGrants.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Column {
                            recentGrants.forEachIndexed { index, grant ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 54.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                }
                                RecentGrantRow(
                                    grant = grant,
                                    appName = watchedApps.firstOrNull { it.packageName == grant.appId }?.appName
                                        ?: grant.appId
                                )
                            }
                        }
                    }
                }
            }

            // Section 4: Demo Tools
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onResetLedger,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "Reset ledger",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = onSeedDemoData,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "Seed ledger",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = {
                            val firstApp = installedWatched.firstOrNull()?.packageName
                                ?: watchedApps.firstOrNull { it.isWatched }?.packageName
                                ?: "com.reddit.frontpage"
                            onTestNegotiation(firstApp)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "Test blocker",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun RecentGrantRow(grant: GrantHistoryItem, appName: String) {
    var expanded by remember { mutableStateOf(false) }
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    val timeStr = timeFormatter.format(Instant.ofEpochMilli(grant.requestedAt))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(packageName = grant.appId, size = 28.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val modeStr = if (grant.mode == AccessMode.GRAYSCALE) " (grayscale)" else ""
                Text(
                    text = "Asked ${grant.proposedMinutes}m → got ${grant.grantedMinutes}m$modeStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        text = "Plea: \"${grant.plea}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (grant.promise.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Promise: \"${grant.promise}\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (grant.honoured != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val outcomeText = if (grant.honoured) "Honoured" else "Overran by ${grant.overranBy ?: 0} min"
                        Text(
                            text = "Outcome: $outcomeText",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (grant.honoured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }
    }
}
