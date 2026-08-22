# AGENTS.md

Standing instructions for any agent working in this repo. Read this first, then `docs/` in numbered order.

## What this is

**Crusty** — an Android app that blocks distracting apps, but lets the user *negotiate* their way in by arguing with an on-device Gemma 4 agent. Nothing leaves the phone.

Built for the **UK AI Agents Lab hackathon at Imperial**, Track 3 (Hybrid Gemini + Gemma). **Submission deadline is 17:30 GMT the same day.** Every scope decision serves that.

## Read order

| File | What it gives you |
|---|---|
| `docs/00-RESEARCH.md` | How existing blockers work, what's been tried, what to avoid |
| `docs/01-PRD.md` | Scope, the demo we are building toward, non-goals |
| `docs/02-ARCHITECTURE.md` | Package layout, the negotiation loop, the policy contract |
| `docs/03-UI-SPEC.md` | Visual language. Read before writing any Composable |
| `docs/04-BUILD-PLAN.md` | **Hour-by-hour plan with hard gates. This is your work queue** |
| `docs/05-PROMPTS.md` | System prompt, tool schema, eval pleas |
| `docs/06-SUBMISSION.md` | Deliverables, video script, write-up draft, judging alignment |

## Non-negotiable rules

1. **The model is never the authority.** Gemma proposes; the policy engine decides. Every grant passes through `PolicyEngine.clamp()`. No code path may issue a grant that skipped it. If you find one, that's a P0 bug — and it is also the thing we get judged on.
2. **`policy/` is pure Kotlin.** No Android imports, no coroutines, no I/O. Unit-testable on the JVM in milliseconds. This is where the tests live; nothing else needs them today.
3. **Nothing leaves the device.** No analytics, no crash reporter that ships text. The negotiation path makes no network call, ever.
4. **The engine stays warm.** `Engine.initialize()` takes up to ~10s. Never construct it on the interception path. It lives in a foreground service.
5. **Don't invent Material.** M3 components and dynamic color as-is. No custom brand palette, no hand-rolled buttons. See `docs/03-UI-SPEC.md`.
6. **Ship over polish.** It is 17:30 either way. A working ugly loop beats a beautiful half-loop. Respect the gates in `04-BUILD-PLAN.md` and cut scope at them rather than sliding them.

## Conventions

- **One Gradle module.** Packages, not modules: `policy/`, `llm/`, `enforce/`, `ui/`, `data/`. Do not refactor into a module graph because it feels cleaner.
- Kotlin, Jetpack Compose, no XML layouts. Manual DI via a single `AppContainer` — no Hilt.
- Coroutines + Flow. No RxJava, no LiveData.
- Persistence is a JSON file via kotlinx.serialization. No Room.
- Package root: `com.crusty`. `minSdk 31`, `compileSdk 36`.
- Tests: JUnit. The policy engine needs real coverage including adversarial cases. Nothing else does today.
- Commit per completed checkbox with the lane letter, e.g. `C: clamp invariants + adversarial tests`.

## Lane boundaries

Three lanes run in parallel and must not collide. Respect package ownership:

| Lane | Owns | Never touches |
|---|---|---|
| **A — Model** | `llm/` | UI, policy internals |
| **B — Enforcement** | `enforce/` | Model internals |
| **C — Policy + UI** | `policy/`, `ui/`, `data/` | Model internals, service lifecycle |

Shared data classes (`Proposal`, `Grant`, `Rules`, `LedgerSnapshot`) live in `model/` and are **committed first, before any lane starts**. Changing one means one commit that updates every consumer.

## When you are blocked

Log it in `docs/OPEN-QUESTIONS.md` with your lane, make the most reasonable assumption, mark it `// ASSUMPTION:` in code, and keep going. Do not stall the queue — there is no time to stall.
