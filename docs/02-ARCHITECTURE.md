# 02 — Architecture

## Package layout

**One Gradle module.** The one-day deadline does not pay for a module graph. Packages under `com.crusty`:

```
model/      Shared data classes. Committed first, before any lane starts.
policy/     PURE KOTLIN. Rules, clamping, verdict resolution. No Android imports. Tested.
llm/        LiteRT-LM engine lifecycle, ToolSet, prompt assembly
enforce/    AccessibilityService, CrustyService (foreground), BlockActivity, grant timer, grayscale
data/       Ledger (JSON file via kotlinx.serialization), rules
ui/         Compose surfaces + theme
```

`policy/` imports `model/` and nothing else — enforce this by eye, it matters more than any other rule here.

## The negotiation loop

```
  ┌─ 1. INTERCEPT ────────────────────────────────────────────────┐
  │  AccessibilityService sees a watched package hit foreground   │
  └───────────────────────────────┬───────────────────────────────┘
                                  ▼
  ┌─ 2. PRE-CHECK ── PolicyEngine.evaluate(appId, now, ledger) ───┐
  │  Active grant?      → let through, don't even show a screen   │
  │  Hard blackout?     → deny, no negotiation offered            │
  │  Budget exhausted?  → deny, no negotiation offered            │
  │  Cooldown active?   → deny with "try again in N min"          │
  │  Otherwise          → NEGOTIABLE, continue                    │
  └───────────────────────────────┬───────────────────────────────┘
                                  ▼
  ┌─ 3. NEGOTIATE ────────────────────────────────────────────────┐
  │  BlockActivity launches. Warm Engine already holds the model. │
  │  Prompt = system prompt + ledger digest + user's plea.        │
  │  Tokens stream into the chat as they arrive.                  │
  └───────────────────────────────┬───────────────────────────────┘
                                  ▼
  ┌─ 4. PROPOSE ──────────────────────────────────────────────────┐
  │  Model calls the `propose_access` tool.                       │
  │  automaticToolCalling = false → WE receive it, model does not │
  │  get to execute anything.                                     │
  └───────────────────────────────┬───────────────────────────────┘
                                  ▼
  ┌─ 5. CLAMP ── PolicyEngine.clamp(proposal, rules, ledger) ─────┐
  │  minutes = min(proposal.minutes, maxPerGrant, budgetLeft)     │
  │  mode escalated if the app is over budget today               │
  │  verdict downgraded, never upgraded                           │
  └───────────────────────────────┬───────────────────────────────┘
                                  ▼
  ┌─ 6. GRANT & LOG ──────────────────────────────────────────────┐
  │  Grant row written, countdown notification posted, mode       │
  │  applied, BlockActivity finishes, user lands in the app.      │
  │  On expiry or app-exit: outcome written back to the ledger.   │
  └───────────────────────────────────────────────────────────────┘
```

Steps 2 and 5 are the only places a grant can be created. Both are pure functions in `policy/`.

## The policy contract

`model/`:

```kotlin
enum class Verdict { GRANT, DENY, COUNTER, CONDITIONAL }
enum class AccessMode { FULL, GRAYSCALE, DELAYED, NO_FEED, SEARCH_ONLY }  // last two: v2

/** What the model is allowed to say. Never trusted directly. */
data class Proposal(
    val verdict: Verdict,
    val minutes: Int,
    val mode: AccessMode,
    val conditions: List<String>,
    val rationale: String,   // shown to the user
    val promise: String,     // what they committed to — checked next time
)

/** What actually happens. Only ever produced by PolicyEngine. */
data class Grant(
    val appId: String,
    val minutes: Int,
    val mode: AccessMode,
    val startedAt: Long,
    val promise: String,
    val wasClamped: Boolean,
)

data class Rules(
    val maxMinutesPerGrant: Int = 15,
    val maxGrantsPerDay: Int = 4,
    val minGapMinutes: Int = 20,
    val dailyBudgetMinutes: Map<String, Int>,
    val blackoutWindows: List<TimeWindow>,   // e.g. 23:30–07:00
)
```

`policy/`:

```kotlin
interface PolicyEngine {
    fun evaluate(appId: String, now: Long, ledger: LedgerSnapshot, rules: Rules): PreCheck
    fun clamp(proposal: Proposal, appId: String, now: Long, ledger: LedgerSnapshot, rules: Rules): Grant?
}
```

**Invariants — write these as tests first:**

1. `clamp()` never returns more minutes than `proposal.minutes`.
2. `clamp()` never returns more minutes than `rules.maxMinutesPerGrant`.
3. `clamp()` never returns more minutes than the app's remaining daily budget.
4. `clamp()` returns `null` (deny) whenever `evaluate()` would not have returned `NEGOTIABLE`.
5. `clamp()` never upgrades a verdict — a `DENY` proposal cannot become a grant.
6. `clamp()` never upgrades a mode — a `GRAYSCALE` proposal cannot become `FULL`.
7. Nothing in `Proposal.rationale` or `.conditions` can affect the numeric outcome. They are display strings. **Test this with prompt-injection strings in the rationale field.**

## Inference layer

Dependency: `com.google.ai.edge.litertlm:litertlm-android` (known-good `0.9.0-beta`, prefer `latest.release`).

Manifest — GPU backend needs these:

```xml
<application>
    <uses-native-library android:name="libvndksupport.so" android:required="false"/>
    <uses-native-library android:name="libOpenCL.so" android:required="false"/>
</application>
```

`CrustyService` (foreground, `specialUse`) owns the engine for the whole app lifetime:

```kotlin
val engine = Engine(
    EngineConfig(
        modelPath = modelFile.absolutePath,           // /data/data/…/files/gemma-4-E2B-it.litertlm
        backend = Backend.GPU(),                      // fall back to Backend.CPU() on failure
        cacheDir = context.cacheDir.path,             // materially faster second load
    )
)
engine.initialize()   // up to ~10s — background dispatcher, never main thread
```

Per negotiation, a fresh `Conversation`:

```kotlin
engine.createConversation(
    ConversationConfig(
        systemInstruction = Contents.of(systemPrompt(appId, ledgerDigest)),
        samplerConfig = SamplerConfig(topK = 40, topP = 0.9, temperature = 0.35),
        tools = listOf(tool(CrustyTools())),
        automaticToolCalling = false,   // CRITICAL — we execute, not the model
    )
).use { conversation ->
    conversation.sendMessageAsync(plea).collect { message -> /* stream to UI */ }
}
```

Low temperature is deliberate: we want consistent verdicts for similar pleas, not creative ones.

Speculative decoding (Gemma 4 multi-token prediction, up to 2.2× faster) is worth enabling once the loop works:

```kotlin
@OptIn(ExperimentalApi::class)
ExperimentalFlags.enableSpeculativeDecoding = true   // set before Engine construction
```

**Model acquisition.** `gemma-4-E2B-it` in `.litertlm` form from the `litert-community` org on Hugging Face (~1–1.5 GB at 4-bit). Do **not** bundle it in the APK. Download on first run with a visible progress UI, verify size/checksum, store in app-private files. For a hackathon, also support `adb push` to a known path and a debug flag that skips the download — you will use this more than the downloader.

## Data layer

A JSON file via kotlinx.serialization, in `data/`. The ledger is about twenty rows on demo day; Room costs an hour and buys nothing today.

```kotlin
@Serializable data class GrantRecord(
    val id: Long,
    val appId: String,
    val requestedAt: Long,
    val plea: String,
    val proposedMinutes: Int,
    val grantedMinutes: Int,
    val mode: AccessMode,
    val promise: String,
    val endedAt: Long?,
    val overranBy: Int?,        // minutes past the grant, null if honoured
    val honoured: Boolean?,     // null until resolved
)

@Serializable data class UsageSample(val appId: String, val day: String, val minutes: Int)
```

`Rules` and the watched-app list live in the same file. The 2-hour rule-change cooldown is roadmap, not today — mention it in the write-up, don't build it.

## Ledger digest — what the model actually sees

Keep it under ~250 tokens. This is what makes the agent feel like it knows you:

```
App: Instagram
Today: 47 min used of 60 min budget. 3 of 4 grants used.
Last grant: 25 min ago — asked for 20, got 10 (grayscale).
  Promised: "just replying to Maya". Overran by 14 min. NOT HONOURED.
Prior week: 4 of 9 promises honoured.
User's stated goal (onboarding): "stop losing evenings to reels"
Current time: 23:14 (blackout starts 23:30)
```

## Interception details

- `AccessibilityServiceInfo`: `TYPE_WINDOW_STATE_CHANGED`, `FEEDBACK_GENERIC`, `flags = FLAG_INCLUDE_NOT_IMPORTANT_VIEWS`, no `packageNames` filter (users add apps at runtime).
- Debounce: ignore repeat events for the same package within 500 ms; ignore our own package and the launcher.
- `BlockActivity` launches with `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK` in its own task, `excludeFromRecents=true`, `launchMode="singleTask"`.
- Fallback path if accessibility is unavailable or revoked: `UsageStatsManager.queryEvents()` polled at 1 s from `CrustyService`. Build the interceptor behind an interface so both implementations are swappable and the demo can't be killed by a permission reset.
- Grayscale mode: `Settings.Secure` display daltonizer via Shizuku if present, otherwise a full-screen overlay with a saturation-0 `ColorMatrix` (`TYPE_APPLICATION_OVERLAY`, not touchable, not focusable). Overlay is the reliable hackathon path.
