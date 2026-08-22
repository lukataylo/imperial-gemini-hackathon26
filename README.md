# Crusty

An Android app blocker you negotiate with — powered by on-device Gemma 4 and cloud Gemini 3.7 Flash.

Crusty stops you from opening distracting apps, but instead of a hard block, it starts a conversation. Crusty opens the dialogue instantly, asks clarifying questions, remembers whether you kept your previous promises, and offers the minimal pass required — like 10 minutes in grayscale to send a single reply.

Built for the **UK AI Agents Lab Hackathon at Imperial College London** (Track 3 — Hybrid Gemini + Gemma).

---

## How It Works

1. **Instant Opening Lines & 100% Local Negotiation:** Uses context-derived opening lines (`OpeningLines.kt`) to start the conversation with zero latency. Runs Gemma 4 E2B locally on-device via LiteRT-LM with no network required for negotiations, operating fully in airplane mode.
2. **Model Proposes, Policy Decides:** The model never has final authority. Gemma negotiates with you and calls `propose_access`. A deterministic, pure-Kotlin policy engine (`PolicyEngine.clamp()`) evaluates that proposal against hard limits you set while calm.
3. **Jailbreak Proof:** Even if you prompt-inject the model into granting 8 hours of screen time, `PolicyEngine.clamp()` caps the grant to your preset maximum (e.g. 15 minutes) or rejects it entirely.
4. **Hybrid Weekly Reflections (Gemini 3.7 Flash):** Opt-in weekly behavioral reflections (`UsageInsights.kt`) analyze anonymized habit patterns. Personal free-text pleas and promises never leave the device.

---

## Architecture

```mermaid
flowchart TD
    A[User opens watched app] --> B[CrustyAccessibilityService<br/>detects foreground app]
    B --> C{PolicyEngine.evaluate<br/>pure Kotlin}
    C -->|active grant| Z[Allow access]
    C -->|blackout / budget spent| D[Deny access]
    C -->|negotiable| E[BlockActivity]
    E -->|instant opener| F[OpeningLines.kt]
    E --> G[Gemma 4 E2B<br/>LiteRT-LM on-device]
    G -->|streams response| E
    G --> H[Tool call: propose_access]
    H --> I{PolicyEngine.clamp<br/>pure Kotlin}
    I -->|clamped to limits| J[Grant: 10 min, grayscale]
    I -->|rejected| D
    J --> K[Timed pass + countdown notification]
    K --> L[LedgerRepository: record promise & outcome]
    L -.->|history digest| G
    L -.->|anonymised summary| M[Gemini 3.7 Flash<br/>Weekly Insights]
```

> **Core Safety Invariant:** Gemma only generates proposals. The pure-Kotlin policy engine owns the decision diamonds.

---

## Quick Start

### Prerequisites
- Android 12+ device (Google Pixel recommended)
- JDK 21 & Android SDK installed
- `adb` configured

### 1. Download & Push Model Weights
Obtain `gemma-4-E2B-it.litertlm` (from Hugging Face LiteRT community) and push to your device:

```bash
adb push gemma-4-E2B-it.litertlm /sdcard/Download/
```

### 2. Build & Install

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21) # Or your JDK 21 path
./gradlew installDebug
```

### 3. Grant Device Permissions
On first launch, enable the following Android permissions:
- **Accessibility:** Settings → Accessibility → Crusty
- **Usage Access:** Settings → Apps → Special access → Usage access
- **Display Over Other Apps:** Settings → Apps → Special access → Display over other apps

---

## Testing

Run unit tests on the JVM (includes 25+ tests verifying `PolicyEngine` invariants, adversarial jailbreak resistance, and `ReflectionsTest`):

```bash
./gradlew :app:testDebugUnitTest
```

---

## Repository Structure

For AI agents working on this repo, read [`AGENTS.md`](AGENTS.md) first.

| Document | Description |
|---|---|
| [`docs/00-RESEARCH.md`](docs/00-RESEARCH.md) | Analysis of existing app blockers and failure modes |
| [`docs/01-PRD.md`](docs/01-PRD.md) | Product scope, user flows, and demo script |
| [`docs/02-ARCHITECTURE.md`](docs/02-ARCHITECTURE.md) | Package layout, policy engine contracts, and state machine |
| [`docs/03-UI-SPEC.md`](docs/03-UI-SPEC.md) | Material 3 Expressive UI specifications |
| [`docs/04-BUILD-PLAN.md`](docs/04-BUILD-PLAN.md) | Hour-by-hour build milestones |
| [`docs/05-PROMPTS.md`](docs/05-PROMPTS.md) | System prompt engineering, tool schemas, and evals |
| [`docs/06-SUBMISSION.md`](docs/06-SUBMISSION.md) | Hackathon submission deliverables and video outline |
| [`docs/SUBMISSION_WRITEUP.md`](docs/SUBMISSION_WRITEUP.md) | Hackathon submission write-up document |

---

## License

[MIT License](LICENSE) — Imperial College London, August 2026.
