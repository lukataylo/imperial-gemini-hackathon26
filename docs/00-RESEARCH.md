# 00 — Research: how self-control apps actually work

Condensed from a survey of open-source Android blockers, iOS Screen Time apps, and the AI-coach category (Aug 2026). Read this so you don't reinvent solved problems or walk into known traps.

## The five enforcement primitives

Every blocker on the market is a combination of these.

| Mechanism | Platform | How it works | Ships in |
|---|---|---|---|
| **Accessibility service** | Android | Subscribe to `TYPE_WINDOW_STATE_CHANGED`, read foreground package, launch a full-screen blocker or press HOME. View-tree access also enables in-app blocking (hiding Reels/Shorts) | DetoxDroid, DigiPaws, Curbox, AppBlock |
| **UsageStats polling** | Android | `UsageStatsManager.queryEvents()` on a ~1s loop from a foreground service | Most Play-Store blockers, as fallback |
| **Local VPN / DNS** | Both | `VpnService` loopback tunnel that never leaves the device; NXDOMAIN blocked hosts. The only no-root way to block *websites* | Blokada, RethinkDNS, Freedom |
| **Screen Time API** | iOS | FamilyControls picker → opaque tokens → `ManagedSettingsStore.shield` | Opal, Jomo, ScreenZen |
| **Shortcuts intercept** | iOS | User automation "when X opens → run my App Intent" | one sec |

**We use #1, with #2 as the fallback.** #3 is out of scope for v1 (browser blocking is a stretch goal).

## What the good open-source ones do

- **DetoxDroid** — doesn't block, *degrades*: forced system-wide grayscale with per-app exclusions, doomscroll detection that interrupts infinite feeds, per-app daily budgets. Direct precedent for our counter-offer modes.
- **DigiPaws** — blocked launch triggers "Survival Mode": all apps lock for an hour unless you complete a hardcoded "Quest". Also keyword blocking, in-app Shorts/Reels blocking, anti-uninstall. The closest existing thing to a negotiation — but the deal never changes.
- **Curbox** — granular UI hiding (block the YouTube home feed, keep search) and uses **Shizuku** (ADB-privileged helper) rather than relying solely on accessibility. Our escape hatch if the accessibility API keeps tightening.

## Why friction works at all

The [PNAS study](https://www.pnas.org/doi/10.1073/pnas.2213114120) on *one sec* reports ~57% reduction in social media use from three ingredients: **a delay**, **a deliberation prompt**, and **an explicit dismiss option**. Our agent must preserve all three — the conversation *is* the delay, the model's question *is* the deliberation, and "never mind" must always be one tap away.

## The anti-bypass arms race — and why we opt out

Standard Android ladder: change-cooldowns → device admin to block uninstall → accessibility watchdog that presses HOME when you open the device-admin settings page → password/NFC gates.

This is the stalkerware playbook and platforms are closing it:
- Android 13 added restricted-settings friction for sideloaded apps requesting accessibility.
- Play policy allows `isAccessibilityTool="true"` only for screen readers, switch access, voice input, Braille — **not** monitoring or automation tools.
- **Android 17** (beta 2, Mar 2026): Advanced Protection Mode blocks granting accessibility to any app without that flag, and auto-revokes it from installed ones.

**Our position:** a legitimate door means we need far less brutality holding it shut. v1 ships change-cooldowns only. No device admin, no settings watchdog. Say so in onboarding — it's a feature, not a gap.

## Prior art in negotiation (this category exists)

| App | What it does | The gap |
|---|---|---|
| Zario | "First AI that grants or denies access to distracting apps" | Cloud model |
| LOCKR | You give a reason, AI picks a duration from a strictness slider | No memory of prior deals |
| Superhappy | "Intentional conversations with your AI coach" | Weak enforcement coupling |

**Our three differentiators:** it runs **local** (works in airplane mode, excuses never leave the phone), it has **memory** (holds you to your last promise), and it makes **counter-offers** (degraded/delayed/conditional access, not yes-or-no).

## Why Android, not iOS

iOS is structurally hostile to this specific product:
- `DeviceActivityMonitor` extension has a hard **6 MB memory limit**. A 1 GB model cannot exist near it.
- `ShieldConfiguration` is a static snapshot — no animation, no countdown, no chat.
- `ShieldActionResponse` is only `.none`/`.defer`/`.close` — no supported way to jump from the shield into your app to hold the conversation.
- FamilyControls (Distribution) is a request-and-wait entitlement, needed on the app *and* every extension bundle ID.

On Android the interceptor, the model, and the UI live in one process with no meaningful memory ceiling, and we own the blocking screen completely.

## Sources

- [DigiPaws](https://github.com/nethical6/digi-paws) · [DetoxDroid](https://f-droid.org/en/packages/com.flx_apps.digitaldetox/) · [Curbox](https://f-droid.org/packages/neth.iecal.curbox/) · [Shizuku](https://github.com/rikkaapps/shizuku)
- [one sec PNAS study](https://www.pnas.org/doi/10.1073/pnas.2213114120)
- [Play policy: AccessibilityService API](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en) · [Android 17 accessibility restrictions](https://thehackernews.com/2026/03/android-17-blocks-non-accessibility.html)
- [Gemma 4 model card](https://ai.google.dev/gemma/docs/core/model_card_4) · [LiteRT-LM on Android](https://developers.google.com/edge/litert-lm/android) · [LiteRT-LM Kotlin API](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md)
