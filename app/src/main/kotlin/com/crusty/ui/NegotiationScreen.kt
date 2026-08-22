package com.crusty.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.crusty.model.AccessMode
import com.crusty.model.DenialReason
import com.crusty.model.Grant
import com.crusty.model.Proposal
import com.crusty.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.Image

@Composable
fun NegotiationScreen(
    uiState: NegotiationUiState,
    onSendPlea: (String) -> Unit,
    onAcceptOffer: () -> Unit,
    onKeepNegotiating: () -> Unit,
    onNeverMind: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll when messages change or tokens stream
    LaunchedEffect(uiState.messages.size, uiState.messages.lastOrNull()?.text) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        uiState.errorMessage?.let { err ->
            Text(
                text = err,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (uiState.isSimulated) {
            // Never let the scripted fallback pass for the on-device model.
            Text(
                text = "Scripted fallback — Gemma not loaded",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Top Header: App icon + Name
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = uiState.appName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Today usage line
        Text(
            text = "You've had ${uiState.todayUsedMinutes} of ${uiState.todayBudgetMinutes} minutes today.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // The question
        if (uiState.denialReason == null) {
            Text(
                text = "Why now?",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Denial State Render (Fast <200ms pre-check result)
        if (uiState.denialReason != null) {
            Spacer(modifier = Modifier.height(32.dp))
            val denialText = when (uiState.denialReason) {
                DenialReason.BLACKOUT -> "Blackout window is active. No access permitted right now."
                DenialReason.BUDGET_EXHAUSTED -> "Today's daily budget for ${uiState.appName} is exhausted."
                DenialReason.MAX_GRANTS_REACHED -> "Not right now. You've used today's grant allowance."
                DenialReason.COOLDOWN_ACTIVE -> "In cooldown. Try again in ${uiState.retryAfterMinutes ?: 20} minutes."
                DenialReason.POLICY_REJECTED -> "Request declined by policy limits."
            }

            Text(
                text = denialText,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onNeverMind,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Return Home")
            }
            Spacer(modifier = Modifier.height(16.dp))
            return@Column
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Empty state: Crusty waits for you to say something.
        if (uiState.messages.none { it.isUser }) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.crusty_waiting),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth(0.42f)
                        .aspectRatio(757f / 900f)
                )
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "Take a moment.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tell me what you're looking for, feeling, or hoping " +
                        "to get from ${uiState.appName} right now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.86f)
                )
            }
        }

        // Chat messages
        if (uiState.messages.any { it.isUser } || uiState.clampedGrant != null) LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.messages, key = { it.id }) { message ->
                ChatBubble(message = message)
            }

            // In-place Offer Card at bottom of conversation
            if (uiState.clampedGrant != null) {
                item {
                    OfferCard(
                        proposal = uiState.currentProposal,
                        clampedGrant = uiState.clampedGrant,
                        onAccept = onAcceptOffer,
                        onKeepNegotiating = onKeepNegotiating
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input row
        if (uiState.clampedGrant == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Tell it why…") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                FilledIconButton(
                    onClick = {
                        onSendPlea(inputText)
                        inputText = ""
                    },
                    enabled = inputText.isNotBlank() && !uiState.isStreaming
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Send"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Never mind: Always present, instant dismiss
        TextButton(
            onClick = onNeverMind,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = "Never mind",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        val backgroundColor = if (message.isUser) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
        val textColor = if (message.isUser) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }

        if (message.isUser) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 4.dp
                        )
                    )
                    .background(backgroundColor)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                CrustyAvatar(
                    size = 28.dp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                topStart = 4.dp,
                                topEnd = 16.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 16.dp
                            )
                        )
                        .background(backgroundColor)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
fun OfferCard(
    proposal: Proposal?,
    clampedGrant: Grant,
    onAccept: () -> Unit,
    onKeepNegotiating: () -> Unit
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically { it / 2 }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Duration
                Text(
                    text = "${clampedGrant.minutes} minutes",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                // Mode
                val modeLabel = when (clampedGrant.mode) {
                    AccessMode.GRAYSCALE -> "Grayscale · feed still visible"
                    AccessMode.DELAYED -> "Delayed start in 5 minutes"
                    AccessMode.NO_FEED -> "Feed hidden · search and messages only"
                    AccessMode.SEARCH_ONLY -> "Search only"
                    AccessMode.FULL -> "Full color access"
                }
                Text(
                    text = modeLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )

                // Quoted promise if available
                if (clampedGrant.promise.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "\"${clampedGrant.promise}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // Clamped disclosure line (Honesty builds trust per 03-UI-SPEC.md)
                if (clampedGrant.wasClamped && proposal != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "It offered ${proposal.minutes}; your limit is ${clampedGrant.minutes}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onKeepNegotiating) {
                        Text(
                            text = "Keep negotiating",
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onAccept) {
                        Text("Accept")
                    }
                }
            }
        }
    }
}
