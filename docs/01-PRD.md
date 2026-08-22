# 01 — Product requirements

## One line

A blocker you can argue with — and that argues back, remembers what you promised, and runs entirely on your phone.

## The demo we are building toward

Rehearse this end-to-end. Every scope decision serves it.

1. Phone in **airplane mode**, visibly. (Sells "local" in one gesture.)
2. Tap Instagram. Gatekeeper takes the screen — calm, not alarming.
3. Type a weak plea: *"just quickly checking something"*. The agent pushes back and offers nothing, or offers 3 minutes with the feed hidden.
4. Type a real reason: *"I need to reply to Maya about tomorrow's venue"*. Agent grants **10 minutes, grayscale**, and asks you to confirm the promise.
5. Countdown runs. Grayscale visibly applied. Time expires, block returns.
6. Tap Instagram again 30 seconds later. Agent: *"You said one reply and took the full ten. What happened?"* — offers less this time.

The sixth beat is the one that wins. Protect it.

## Track

**Track 3 — Hybrid Gemini + Gemma.** On-device Gemma 4 negotiation paired with Gemini 3.7 Flash weekly habit reflections.

## In scope today

- Foreground-app interception for a hardcoded list of apps
- Full-screen negotiation surface with streaming chat
- On-device Gemma 4 E2B via LiteRT-LM, warm-loaded in a foreground service
- Structured proposal from the model → deterministic policy clamp → timed grant
- Ledger: what was asked, given, promised, and whether it was honoured
- Counter-offer modes: `FULL` and `GRAYSCALE`

## Stretch, in this order

`DELAYED` mode · in-app app picker · home screen · grayscale via Shizuku instead of an overlay · the Gemini 3.7 Flash weekly reflection (which would move us to Track 3)

## Explicitly out of scope

- Onboarding flow, model downloader, rule-change cooldown — all roadmap
- Website/browser blocking (VpnService)
- In-app element hiding (`NO_FEED`, `SEARCH_ONLY`) — enum them, don't implement
- Device admin / uninstall protection / settings watchdog — deliberate, see `00-RESEARCH.md`
- iOS
- Accounts, sync, sharing, any network call at all
- Voice or image input (E2B supports both; not needed for the demo)

## Success criteria

| | Target |
|---|---|
| Interception latency (foreground app → blocker visible) | < 400 ms |
| First token of the agent's reply | < 1.5 s from send |
| Full counter-offer generated | < 4 s |
| Model cold-start after boot | hidden — warm before first interception |
| Policy clamp survives adversarial pleas | 100% — proven by unit test, shown to judges |
| Policy engine test coverage | 100% of clamp rules |
| Works with no network | Fully, after first download |

## The hard product rule

**The user can always get out.** "Never mind" closes the screen and returns them home, no argument, always one tap, never behind a delay. We are building a negotiator, not a jailer. A blocker that traps people is a bug report and an app-store removal.
