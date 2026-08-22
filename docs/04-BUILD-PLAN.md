# 04 — Build plan (one day)

**Deadline: 17:30 GMT sharp.** This is a single-day build, not a weekend. The plan below is reverse-planned from submission and assumes a 09:00 kickoff — shift every time if yours differs, but keep the *gaps* between checkpoints.

The earlier multi-module, Hilt, Room, onboarding-flow plan is dead. What follows is what actually fits.

## Scope, cut to fit

| Cut | Replaced with |
|---|---|
| 7 Gradle modules | **One module.** Packages, not modules: `policy/`, `llm/`, `enforce/`, `ui/`, `data/` |
| Hilt | Manual DI. One `AppContainer` object |
| Room | **A JSON file** via kotlinx.serialization. The ledger is ~20 rows on demo day |
| Onboarding flow | Hardcoded watched-app list + one settings screen |
| Model downloader | **`adb push` only.** Say so in the README |
| Permission rationale screens | One screen with three buttons that deep-link to the system settings |
| Rule-change cooldown | Skip. Mention it in the roadmap |

**Kept, because it is the entire pitch:** the accessibility interceptor, the warm on-device engine, the structured proposal, the policy clamp, the ledger with promises, and the grayscale counter-offer.

## The one thing that must be true by lunch

**A plea typed on the phone produces a parsed `Proposal` from Gemma 4 running locally.** Everything else is Android plumbing you already know how to write. This is the only genuinely unknown part, so it gets the morning and the best person.

## Three lanes

Run these in parallel from the start. They touch different packages and don't block each other until the 13:00 join.

| Lane | Owns | Person |
|---|---|---|
| **A — Model** | `llm/`, `docs/05-PROMPTS.md` | Whoever is least afraid of native crashes |
| **B — Enforcement** | `enforce/` | Whoever knows Android services |
| **C — Policy + UI** | `policy/`, `ui/`, `data/` | Whoever writes the cleanest Kotlin |

If you are solo: do A first to de-risk, then B, then C, and drop grayscale.

---

## 09:00 — Setup (everyone, 30 min)

- [ ] `adb push` the `gemma-4-E2B-it` `.litertlm` file to the device **now**, before anything else. It is ~1–1.5 GB and it will take longer than you think.
- [ ] One Android Studio project, `minSdk 31`, `compileSdk 36`, Compose BOM, kotlinx.serialization.
- [ ] Push an empty repo with a **MIT license** and the README skeleton. Required for submission; doing it now costs 5 minutes and at 17:15 it costs you the submission.
- [ ] Agree the `Proposal` / `Grant` / `Rules` data classes verbatim from `02-ARCHITECTURE.md` and commit them first. This is the contract all three lanes build against.

## 09:30 – 12:30 — Parallel build

**Lane A — Model**
- [ ] `litertlm-android` dependency + the two `uses-native-library` OpenCL entries in the manifest
- [ ] `InferenceEngine`: `Engine.initialize()` on `Dispatchers.Default`, GPU with CPU fallback, `cacheDir` set. **Log time-to-READY.**
- [ ] `CrustyTools : ToolSet` with `@Tool proposeAccess(...)`, `automaticToolCalling = false`
- [ ] System prompt from `05-PROMPTS.md`, few-shot refusals included
- [ ] Prove it: a hardcoded plea in a test activity → streamed text → parsed `Proposal`. **This is the 12:30 gate.**

**Lane B — Enforcement**
- [ ] `CrustyAccessibilityService`, `TYPE_WINDOW_STATE_CHANGED`, 500 ms debounce, self and launcher excluded
- [ ] `CrustyService` foreground service (`specialUse`) that will hold the engine
- [ ] `BlockActivity`: own task, `excludeFromRecents`, `singleTask`, launches on watched-app foreground
- [ ] Grant lifecycle: in-memory timed pass + ongoing countdown notification with `Chronometer`, re-block on expiry
- [ ] Prove it: opening Instagram shows a blank block screen in < 400 ms, a hardcoded 1-minute pass lets you through and re-blocks

**Lane C — Policy + UI**
- [ ] `PolicyEngine.evaluate()` and `clamp()`, pure Kotlin, with the **7 invariants from `02-ARCHITECTURE.md` as JUnit tests**. Ninety minutes, and it is the thing you will defend in Q&A.
- [ ] Adversarial test: injection strings in `rationale`, negative minutes, `Int.MAX_VALUE`, verdict and mode upgrades
- [ ] `Ledger`: JSON file, append a grant, resolve its outcome, produce the ≤250-token digest string
- [ ] Chat UI per `03-UI-SPEC.md`: header, streaming bubbles, input, "Never mind". Fake the stream off a `flowOf(...)` until Lane A lands

## 12:30 — GATE 1

Model produces a parsed `Proposal` on-device. **If it doesn't, stop and fix it — do not carry this past lunch.** Fallbacks in order: CPU backend instead of GPU; E4B if E2B won't parse tool calls; a regex parser over plain-text output if `ToolSet` is fighting you. A regex over `MINUTES: 10` is not elegant and it will not cost you a single point at 17:30.

## 12:30 – 14:30 — Join the loop

- [ ] Move the engine into `CrustyService`, warm from service start
- [ ] `BlockActivity` → real streaming → tool call → `PolicyEngine.clamp()` → `Grant` → pass issued
- [ ] Ledger writes on grant and on expiry, digest feeds the next prompt
- [ ] Offer card with the clamp disclosure line

## 14:30 — GATE 2: the loop is closed

The six beats in `01-PRD.md` run end to end, even if ugly. **Nothing new gets added after this point except from the list below.**

## 14:30 – 15:30 — Make it demo-proof

- [ ] Seed script for a realistic ledger with one broken promise — beat 6 works without waiting for real history
- [ ] Pre-warm the engine on service start and screen-on
- [ ] Debug kill-switch so you can safely hand the phone to a judge
- [ ] Airplane mode rehearsal, twice
- [ ] **Then, only if all of the above is green:** grayscale overlay, the app picker screen, the home screen

## 15:30 – 16:30 — Submission assets

Hand these to whoever is *not* fixing bugs. See `06-SUBMISSION.md` — it has the video script and a draft write-up.

- [ ] 2-minute demo video recorded and uploaded
- [ ] README with setup instructions and the mermaid architecture diagram
- [ ] Write-up, 2–3 paragraphs
- [ ] License, repo made public, screenshots

## 16:30 — CODE FREEZE

Bugs only, and only demo-path bugs. Nothing new.

## 16:30 – 17:15 — Rehearse

Run the demo five times, on the demo device, in airplane mode, on the actual venue wifi being off. Time it. Decide who says what. Prepare answers for the three questions you will definitely get:

1. *"What stops the user just jailbreaking the model into granting 8 hours?"* → the policy clamp, and you have the adversarial test to show
2. *"Why local instead of an API?"* → latency at the gate, works offline at 1am, and the content is a record of someone's compulsions
3. *"How is this different from Screen Time / Opal / one sec?"* → they're static rules; the escape hatch is a bypass. Ours is a negotiation with memory

## 17:30 — SUBMIT

Submit at 17:15. "Sharp" means sharp.

---

## Known pitfalls

- **Cold start kills the demo.** Never construct `Engine` on the interception path.
- **Accessibility permission resets** on reinstall. Re-grant it after every install and check before demoing.
- **The model will be a pushover.** Expected. The clamp makes it harmless — that's the point, and it's a feature to show, not hide.
- **Don't build device admin.** No time, no need, bad optics.
- **Don't refactor into modules** because it feels cleaner. It is 17:30 either way.
