# Crusty — Hackathon Submission Write-up

## Problem
Existing app blockers rely on rigid binary switches: either an app is completely locked or fully open. When an urge strikes, users inevitably disable or uninstall the blocker, rendering static rules ineffective. **Crusty** replaces hard walls with real-time negotiation. Opening a watched app triggers an on-device dialogue where you state your reason for needing access. Crusty cross-references your past promise history from a local ledger, pushes back on weak excuses, and grants only the minimum access needed—such as 10 minutes in grayscale to send one message.

## Architecture
Crusty is built for Android using Kotlin and Jetpack Compose, structured into clean, decoupled components:

- **Enforcement (`enforce/`):** An `AccessibilityService` detects watched apps entering the foreground and checks active passes via `GrantManager`. A background `GatekeeperService` keeps the inference engine warm in memory for instant responsiveness. When intercepted, `BlockActivity` presents the negotiation UI, and `GrayscaleOverlayManager` applies degraded display modes when granted.
- **Model Engine (`llm/`):** `InferenceManager` handles on-device model execution via LiteRT-LM. `PromptBuilder` injects past promise history via `LedgerDigest`. During negotiation, the model streams responses and emits a structured `propose_access` tool call.
- **Policy Engine (`policy/`):** A pure-Kotlin, zero-dependency safety layer. The model generates proposals, but `PolicyEngine.clamp()` holds sole authority over access grants. It enforces hard time caps, daily budgets, blackout hours, and a 2-hour rule loosening cooldown. Prompt-injecting the AI to grant 8 hours fails silently—the policy engine clamps the grant to the user's preset limit or refuses access.
- **Persistence & UI (`data/`, `ui/`):** `LedgerRepository` persists negotiation logs and outcome records locally using `kotlinx.serialization`. The interface is built with Material 3 Expressive components.

## Why Gemma
We chose **Gemma 4 E2B** running via LiteRT-LM for three key technical requirements:

1. **Sub-second Interception Latency:** Interception must happen in the split second between reaching for an app and getting distracted. Cloud API calls add network round-trips that ruin the experience.
2. **Offline Reliability:** Compulsive app usage often happens late at night or in areas with poor connectivity. On-device inference guarantees Crusty works in airplane mode without any external network calls.
3. **Data Privacy:** A record of personal excuses and habit lapses is sensitive user data. Running Gemma entirely on-device ensures no personal data ever leaves the phone.

Gemma 4's reliable structured function calling is what makes our proposal-and-clamp safety model possible.

## Future Roadmap
- **App Feature Degradation:** Move beyond grayscale overlays to selective in-app restriction—such as hiding algorithmic recommendation feeds while keeping direct messaging and search functional.
- **Delayed Grants:** Introduce mandatory cooldown periods before granted access begins, reducing impulse usage.
- **Gemini 3.7 Flash Weekly Reflections:** Add an opt-in weekly reflection feature using cloud-hosted Gemini 3.7 Flash. By processing anonymized ledger digests once a week, Gemini can provide long-term behavioral insights without introducing cloud latency to real-time interception.
