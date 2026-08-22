package com.crusty.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crusty.insights.NegotiationArchetype
import com.crusty.insights.WeeklyInsights
import com.crusty.llm.InferenceManager
import kotlinx.coroutines.launch
import com.crusty.R
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.Image
import androidx.compose.ui.unit.Dp
import androidx.annotation.DrawableRes

@Composable
fun WrappedScreen(
    insights: WeeklyInsights,
    inferenceManager: InferenceManager?,
    onClose: () -> Unit
) {
    val pageCount = 6
    val pagerState = rememberPagerState { pageCount }
    val scope = rememberCoroutineScope()

    var coachNote by remember { mutableStateOf<String?>(null) }
    var isLoadingCoachNote by remember { mutableStateOf(false) }

    LaunchedEffect(insights) {
        if (inferenceManager != null) {
            isLoadingCoachNote = true
            val prompt = "User goal: '${insights.userGoal}'. " +
                    "This week: ${insights.negotiationsCount} negotiations, " +
                    "${(insights.overallHonourRate * 100).toInt()}% promises kept, " +
                    "signature move: '${insights.signatureTechnique.archetype.title}'. " +
                    "You are Crusty, a dry, warm, spiky timer creature who has watched all of this happen. " +
                    "Write 2 sentences about their week, second person, specific, no advice-speak, " +
                    "no names, no bullet points, no emojis. Sound like a friend who noticed something."
            val generated = inferenceManager.generateOneShot(prompt, timeoutMs = 4000L)
            coachNote = generated
            isLoadingCoachNote = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Weekly Wrapped",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Pager Content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            when (page) {
                0 -> HeadlineNumbersCard(insights)
                1 -> SignatureTechniqueCard(insights)
                2 -> DangerHourCard(insights)
                3 -> OverrunProfileCard(insights)
                4 -> RecommendationsCard(insights)
                5 -> CoachNoteCard(
                    insights = insights,
                    coachNote = coachNote,
                    isLoading = isLoadingCoachNote
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom Controls: Page Dots & Navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Page Indicator Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pageCount) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                    )
                }
            }

            // Next / Done Button
            if (pagerState.currentPage < pageCount - 1) {
                TextButton(
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                ) {
                    Text("Next")
                }
            } else {
                Button(onClick = onClose) {
                    Text("Done")
                }
            }
        }
    }
}

/**
 * Crusty reacting to whatever the page just said. The expression is the page's tone:
 * curious at the numbers, unimpressed at your go-to excuse, sleepy at the overruns,
 * pleased when there's nothing to fix.
 */
@Composable
private fun CrustyMood(
    @DrawableRes res: Int,
    modifier: Modifier = Modifier,
    height: Dp = 132.dp,
) {
    Image(
        painter = painterResource(id = res),
        contentDescription = null,
        modifier = modifier.height(height)
    )
}

@Composable
private fun HeadlineNumbersCard(insights: WeeklyInsights) {
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "THE RECAP",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "${insights.negotiationsCount}",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "negotiations with Crusty this week",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Time you talked yourself out of",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${insights.totalProposedMinutes}m asked → ${insights.totalGrantedMinutes}m granted",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "You asked for more and settled for less, ${maxOf(0, insights.hagglingGapMinutes)} minutes of it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Times you kept your word",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val pct = (insights.overallHonourRate * 100).toInt()
                        Text(
                            text = "$pct%",
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (pct >= 80) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${insights.honouredCount} out of ${insights.totalDecidedCount}. Not bad for a running argument with a kitchen timer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

            CrustyMood(
                res = R.drawable.crusty_curious,
                modifier = Modifier.align(Alignment.End)
            )
            }
        }
    }
}

@Composable
private fun SignatureTechniqueCard(insights: WeeklyInsights) {
    val technique = insights.signatureTechnique
    val copy = when (technique.archetype) {
        NegotiationArchetype.NAMED_HUMAN ->
            "Your best excuse is someone waiting on you. It holds up too, you kept ${technique.honouredCount} of ${technique.totalDecided} of them."
        NegotiationArchetype.ERRAND ->
            "You do best when you go in for one specific thing. ${technique.honouredCount} of ${technique.totalDecided} of those ended when you said they would."
        NegotiationArchetype.DEADLINE ->
            "Everything is urgent when you want in. You played the deadline card ${technique.count} times."
        NegotiationArchetype.JUST_CHECKING ->
            "'Just checking for a second', ${technique.count} times this week. You know how that one ends."
        NegotiationArchetype.BARE_MINIMUM ->
            "You barely explain yourself. Short, blunt, and hoping for the best."
        NegotiationArchetype.LAWYER ->
            "You tried to argue with the rules instead of giving a reason. It never once worked."
        NegotiationArchetype.GENERAL ->
            "You just ask. No games, no angle."
    }

    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "SIGNATURE MOVE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = technique.archetype.title,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = technique.archetype.subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = copy,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

            CrustyMood(
                res = R.drawable.crusty_angry,
                modifier = Modifier.align(Alignment.End)
            )
            }
        }
    }
}

@Composable
private fun DangerHourCard(insights: WeeklyInsights) {
    val dangerHour = insights.dangerHour
    val hourText = if (dangerHour != null) {
        String.format("%02d:00", dangerHour.hourOfDay)
    } else {
        "None"
    }

    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1420))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Full-bleed night desk scene; the art leaves the top third empty sky,
            // which is exactly where the hour sits.
            Image(
                painter = painterResource(id = R.drawable.danger_hour_bg),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Scrim so the readouts stay legible over the lamp and the moon.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0xCC0A0F18),
                            0.35f to Color(0x660A0F18),
                            0.62f to Color(0x330A0F18),
                            1f to Color(0xF00A0F18)
                        )
                    )
            )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "WHEN IT'S HARDEST",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = hourText,
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "The hour that gets you",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xCCFFFFFF)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Peak Friction",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (dangerHour != null) {
                                "${dangerHour.count} negotiations clustered at $hourText with a ${(dangerHour.honourRate * 100).toInt()}% honour rate."
                            } else {
                                "No single hour got you this week. That's rarer than you'd think."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Calmest Day",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val dayName = insights.calmestDay?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Sunday"
                        Text(
                            text = dayName,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$dayName was your easiest day. You barely argued at all.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun OverrunProfileCard(insights: WeeklyInsights) {
    val overrun = insights.overrunProfile
    val avgOverrunText = "${overrun.avgOverrunMinutes.toInt()} min"

    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "OVERRUN PROFILE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = avgOverrunText,
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "average overrun when you stayed late",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Mode Comparison",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            val grayPct = (overrun.grayscaleHonourRate * 100).toInt()
                            Text(
                                text = "$grayPct%",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Grayscale mode",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            val fullPct = (overrun.fullHonourRate * 100).toInt()
                            Text(
                                text = "$fullPct%",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Full colour mode",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = if (overrun.grayscaleHonourRate > overrun.fullHonourRate) {
                            "Colour is doing more work than you think. You kept far more promises when the screen went grey."
                        } else {
                            "Shorter asks stick. The long ones quietly become something else."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

            CrustyMood(
                res = R.drawable.crusty_sleepy,
                modifier = Modifier.align(Alignment.End)
            )
            }
        }
    }
}

@Composable
private fun RecommendationsCard(insights: WeeklyInsights) {
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "ADJUSTMENTS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "What Crusty would change",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (insights.recommendations.isEmpty()) {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Leave it as it is",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Your limits fit how you actually behave. Rare. Don't touch them.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    insights.recommendations.take(3).forEach { rec ->
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = rec.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = rec.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

            CrustyMood(
                res = R.drawable.crusty_happy,
                modifier = Modifier.align(Alignment.End)
            )
            }
        }
    }
}

@Composable
private fun CoachNoteCard(
    insights: WeeklyInsights,
    coachNote: String?,
    isLoading: Boolean
) {
    val fallbackNote = if (insights.overallHonourRate >= 0.7) {
        "You kept most of what you promised, and you were best when you knew exactly what you were going in for. The evenings are still where it slips."
    } else {
        "Late evenings are still the hard part, but grey screens kept the overruns small. One pause early seems to decide the whole night."
    }

    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ON-DEVICE COACH",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Coach's Note",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    CrustyAvatar(size = 48.dp)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    AnimatedContent(targetState = coachNote ?: fallbackNote, label = "coach_note") { note ->
                        Text(
                            text = "\"$note\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Written on your phone · Nothing leaves the device",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
