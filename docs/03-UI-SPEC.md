# 03 — UI spec

Read this before writing any Composable.

## The intent

It should feel like a **part of the Pixel**, not an app installed on one. The reference points are Digital Wellbeing, the Pixel Recorder, and the Now Playing sheet: system-native, uncluttered, confident, and quiet. Nothing about it should look like a productivity startup's brand.

The emotional register matters more than the pixels. Someone reaching this screen is mid-craving and slightly ashamed. **Nothing red. Nothing alarming. No lecture, no scoreboard, no streak on fire.** The screen should feel like a calm colleague asking a fair question — which is also, conveniently, what makes the delay tolerable enough to work.

## Foundations

**Material 3, unmodified.**

The project ships on `material3:1.3.1` (Compose BOM 2024.10.01) — a known-good toolchain that builds today. **M3 Expressive is not available on it**: no `MediumFlexibleTopAppBar`, no `materialExpressiveTheme()`, no `MaterialTheme.motionScheme`. Do not reach for those APIs.

This costs almost nothing that matters. The native-Pixel feel here comes from dynamic color, system typography, M3 shapes and generous space — all of which 1.3.1 has. Expressive is a stretch (`material3:1.5.0-alpha26`, which needs Compose BOM 2026.08.00, AGP 9.1.1+ and Kotlin 2.4 — a toolchain bump nobody should attempt before the demo works).

**Dynamic color is mandatory.** The app has no brand palette. It takes the user's wallpaper colors:

```kotlin
val scheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    darkTheme -> darkColorScheme()
    else      -> lightColorScheme()
}   // implemented in ui/Theme.kt
```

Never hardcode a hex value. Semantic states come from the scheme: an honoured promise is `primary`, an overrun is `tertiary` — **not** `error`. Reserve `error` for things that are actually broken.

**Typography.** System default via `MaterialTheme.typography` — Google Sans Flex on Pixel. Do not ship a font. Set no custom weights beyond what the M3 type scale gives you.

**Shape and spacing.** M3 default shapes. 16 dp screen margins, 8 dp grid. Generous vertical rhythm — this app is mostly whitespace and one sentence at a time.

**System integration.** Edge-to-edge (`enableEdgeToEdge()`), predictive back, `Modifier.safeDrawingPadding()`, respect `Settings.Global.ANIMATOR_DURATION_SCALE`, dark theme follows the system. Haptics on grant/deny only (`HapticFeedbackType.Confirm`), nowhere else.

## Screen 1 — The negotiation (the whole product)

Full-screen, opaque, no app bar. The blocked app's icon and name sit small at the top — acknowledge what they wanted, don't shame them for it.

```
┌──────────────────────────────────────┐
│                                      │   ← generous top space
│   ◐  Instagram                       │   ← 40dp icon, labelLarge, onSurfaceVariant
│                                      │
│   You've had 47 of 60 minutes        │   ← bodyLarge, onSurfaceVariant
│   today.                             │      max two lines, factual, never judgmental
│                                      │
│   Why now?                           │   ← headlineMedium, onSurface. THE question.
│                                      │
│                                      │
│   ┌────────────────────────────┐     │
│   │ agent reply streams here   │     │   ← surfaceContainerHigh, large corner
│   └────────────────────────────┘     │      text streams token by token, no typing dots
│                                      │
│                       ┌────────────┐ │
│                       │ your reply │ │   ← primaryContainer, right-aligned
│                       └────────────┘ │
│                                      │
│  ─────────────────────────────────   │
│  [ Tell it why…              ] [ ↑ ] │   ← TextField + FilledIconButton
│  Never mind                          │   ← TextButton, always present, never delayed
└──────────────────────────────────────┘
```

Rules:
- **Streaming is the loading state.** No spinner, no skeleton, no three-dot bubble. Tokens appear as they arrive; that motion is the whole feedback mechanism.
- The chat scrolls; the header stays.
- Keyboard raises the input with `imePadding()`, no jank.
- **"Never mind" is always visible, always instant.** Never behind a countdown. This is a product rule, not a style choice.
- Empty state has no messages at all — just the question. Don't pre-fill the agent with a greeting; make the user speak first.

## Screen 2 — The offer

Not a dialog. The offer resolves *in place*, at the bottom of the conversation, so the negotiation stays one continuous surface.

```
   ┌──────────────────────────────────┐
   │  10 minutes                      │   ← displaySmall, tabular figures
   │  Grayscale · feed still visible  │   ← labelLarge, onSurfaceVariant
   │                                  │
   │  "You said one reply last time   │   ← bodyMedium, the promise, quoted back
   │   and stayed forty minutes."     │
   │                                  │
   │  [ Keep negotiating ] [ Accept ] │   ← TextButton + Button
   └──────────────────────────────────┘
```

- `primaryContainer` surface, M3 large shape.
- Animate in with a single spatial `spring()`. One motion, no bounce chain.
- If it was clamped, say so in one plain line — *"It offered 20; your limit is 15."* Honesty about the rails builds trust in them.

## Screen 3 — Countdown

A **notification**, not an activity. Ongoing, non-dismissible, with a live `Chronometer`. The user is in Instagram; do not take their screen back.

At T-2 minutes: one gentle heads-up notification. Not a full-screen takeover.
At T-0: block returns. No animation, no fanfare, no punishment copy.

## Screen 4 — Home

Not a dashboard. Four things, in this order:

1. **Today** — one line per watched app: name, minutes used, budget. A thin `LinearProgressIndicator` in `primary`, `tertiary` once over budget.
2. **Open promise**, if any — the single most recent unresolved one, quoted verbatim.
3. **Recent negotiations** — collapsed list: app, time, asked → got. Tap to expand the transcript.
4. **Settings** entry.

No charts. No streaks. No score. No emoji. If it looks like a fitness app, delete it and start again.

## Screen 5 — Onboarding

Four steps, one decision per screen, `MediumFlexibleTopAppBar` with the step title:

1. **What are you trying to change?** Free text. This goes verbatim into the system prompt and is the highest-leverage input in the app — give it a full screen.
2. **Which apps?** App picker with icons, multi-select.
3. **Your limits.** Daily budget per app, max minutes per grant, blackout window. Explain plainly that changes take 2 hours to apply, and why.
4. **Getting the model ready.** Download with real progress and size. Say it out loud: *"About 1.2 GB. It stays on your phone. After this, Crusty works with no connection at all."*

Permissions are requested in context with a plain-language rationale screen before the system dialog — never cold. The accessibility rationale must state exactly what is read and that it never leaves the device.

## Motion

Three animations exist in this app and no more:

1. Token stream — text appearing. No transition of its own.
2. Offer card entry — one spatial spring.
3. Block screen entry — fast fade, ~150 ms. It should feel like the system, not like an app launching.

Anything else is noise. `prefers-reduced-motion` equivalent: honour `ANIMATOR_DURATION_SCALE = 0`.

## Copy

Plain, short, second person, no exclamation marks, no personality quirks, no emoji. The agent is direct and a little dry.

| Don't | Do |
|---|---|
| "Oops! Time's up! 🚫" | "That's the ten minutes." |
| "You've been doing great!" | "Four of nine promises kept this week." |
| "Are you sure you want to continue?" | "Why now?" |
| "Access denied" | "Not right now. You've used today's four." |
