# Crusty — Hackathon Submission Write-up

## Problem
Existing app blockers rely on rigid binary switches: either an app is completely locked or fully open. When an urge strikes, users inevitably disable or uninstall the blocker, rendering static rules ineffective. **Crusty** replaces hard walls with real-time negotiation. Opening a watched app triggers an instant on-device dialogue where Crusty greets you with a context-derived opening line quoting your past promise history. You state your reason for needing access, Crusty pushes back on weak excuses, and grants only the minimum access needed—such as 10 minutes in grayscale to send one message.

## Architecture
Crusty is built for Android using Kotlin and Jetpack Compose, structured into clean, decoupled components under `com.crusty`:

- **Enforcement (`enforce/`):** A `CrustyAccessibilityService` detects watched apps entering the foreground and checks active passes via `GrantManager`. A background `CrustyService` keeps the model engine warm in memory for instant responsiveness. When intercepted, `BlockActivity` presents the negotiation UI, and `GrayscaleOverlayManager` applies degraded display modes when granted.
- **Model & Opener Engine (`llm/`):** `OpeningLines.kt` deterministically generates an instant, zero-latency opening question based on context (e.g. quoting a broken promise from last time or warning about late-night usage). `InferenceManager` executes on-device Gemma 4 E2B via LiteRT-LM. During negotiation, Gemma streams responses and emits a structured `propose_access` tool call via `CrustyTools.kt`.
- **Policy Engine (`policy/`):** A pure-Kotlin, zero-dependency safety layer. The model generates proposals, but `PolicyEngine.clamp()` holds sole authority over access grants. It enforces hard time caps, daily budgets, blackout hours, and a 2-hour rule loosening cooldown. Prompt-injecting the AI to grant 8 hours fails silently—the policy engine clamps the grant to the user's preset limit or refuses access.
- **Persistence & Cloud Insights (`data/`, `cloud/`, `insights/`):** `LedgerRepository` persists negotiation logs and outcome records locally using `kotlinx.serialization`. `UsageInsights.kt` integrates cloud-side Gemini 3.7 Flash for opt-in weekly habit reflections over anonymized ledger data. The UI is built with Material 3 Expressive components and Material You dynamic color.

## Why Gemma
We chose **Gemma 4 E2B** running via LiteRT-LM for three key technical requirements:

1. **Sub-second Interception Latency:** Interception must happen in the split second between reaching for an app and getting distracted. Cloud API calls add network round-trips that ruin the experience.
2. **Offline Reliability:** Compulsive app usage often happens late at night or in areas with poor connectivity. On-device inference guarantees Crusty works in airplane mode without any external network calls.
3. **Data Privacy:** A record of personal excuses and habit lapses is sensitive user data. Running Gemma entirely on-device ensures no personal data ever leaves the phone.

Gemma 4's reliable structured function calling is what makes our proposal-and-clamp safety model possible.

## Why Gemini (Hybrid Architecture)
The division of labor between Gemma and Gemini sits where each model is uniquely justified:

- **Gemma 4 E2B (On-Device):** Handles real-time **Negotiation**. It is latency-critical, operates offline, and keeps sensitive free-text pleas and promises on the device.
- **Gemini 3.7 Flash (Cloud):** Handles **Weekly Reflections**. Spotting long-term behavioral patterns (such as promise fulfillment collapsing after 10 PM) requires longitudinal reasoning across weeks of history. This is not latency-critical and benefits from Gemini's large-scale reasoning capabilities.

Privacy is preserved by design: `UsageInsights.buildAnonymisedSummary` strips all free-text pleas and promises, sending **aggregates only** (app ID, hour of day, minutes requested/granted, access mode, and kept/broken status).

## Future Roadmap
- **App Feature Degradation:** Move beyond grayscale overlays to selective in-app restriction—such as hiding algorithmic recommendation feeds while keeping direct messaging and search functional.
- **Delayed Access Grants:** Introduce mandatory cooldown periods before granted access begins to curb impulse opens.
- **Longitudinal Trend Analytics:** Expand Gemini 3.7 Flash weekly reflections to track multi-month behavior trends and automatically recommend personalized policy limit adjustments.
