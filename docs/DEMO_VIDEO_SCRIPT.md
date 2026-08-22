# Crusty — 2-Minute Demo Video Script & AI Video Generation Prompt

## Video Overview
- **Product:** Crusty (Android App Blocker with On-Device AI Negotiation)
- **Target Duration:** Exactly 2:00 (120 seconds)
- **Format:** 16:9 4K 60fps (YouTube / Hackathon Demo)
- **Tone:** Professional, human-centric, clean, Google I/O style

---

## Shot-by-Shot Video Script (0:00 – 2:00)

| Timecode | Visual / On-Screen Action | Audio / Voiceover Script | On-Screen Text Overlay |
|---|---|---|---|
| **0:00 – 0:12** | **Opening Hook:** Close-up of a Google Pixel running Crusty on home screen. Status bar shows **Airplane Mode** active. User taps Instagram icon; Crusty immediately intercepts with a sleek dark-mode block screen. | *"Every app blocker is just a switch you eventually turn off when an urge strikes. Crusty is different — it’s an app blocker you negotiate with, running 100% locally on your phone."* | **Crusty**<br/>The App Blocker You Negotiate With |
| **0:12 – 0:35** | **Vague Excuse Pushback:** User types *"Just checking something quick"* into the chat box. On-device Gemma 4 streams a response pushing back: *"What specifically are you checking? Vague excuses don't unlock time."* | *"When you try to sneak in, Crusty asks why. Vague answers won't earn you screen time."* | **On-Device Gemma 4**<br/>Pushes back on weak excuses |
| **0:35 – 1:00** | **Specific Reason & Clamped Grant:** User types *"Replying to Sarah's message about dinner tonight."* Gemma proposes a 10-minute pass in grayscale. User taps 'Accept'. | *"Give it a real reason, and Crusty offers the smallest pass that fits your goal — 10 minutes in grayscale mode to send your reply."* | **Smart Access Grant**<br/>10 Min • Grayscale Pass |
| **0:10 – 1:15** | **Degraded Access Flow:** Instagram opens in grayscale. A subtle persistent notification shade shows countdown timer *(09:42 remaining)*. | *"Access is timed and degraded. Once the timer hits zero, the block instantly returns."* | **Degraded Mode**<br/>Grayscale • Timed Pass |
| **1:15 – 1:40** | **The Memory Beat (Core Differentiator):** Later, user re-opens Instagram. Crusty's agent quotes past history: *"Last time you said 10 minutes for Sarah, but stayed past your grant. I can only offer 5 minutes now."* | *"Crucially, Crusty remembers your track record. It cross-references a local ledger of your past promises. If you broke a promise earlier, it gets stricter."* | **Local Habit Ledger**<br/>Remembers broken promises |
| **1:40 – 2:00** | **Architecture & Safety Invariant:** Screen transitions smoothly to the architecture diagram. Camera zooms into `PolicyEngine.clamp()`. Status bar shows Airplane Mode again. | *"Powered by Gemma 4 E2B on LiteRT-LM. But Gemma only proposes — a deterministic Kotlin policy engine clamps every grant. Talk it into 8 hours, and you still get 15 minutes max. Zero network, zero tracking, nothing leaves your phone."* | **Propose & Clamp Architecture**<br/>100% On-Device • Zero Network |

---

## Prompt for Google Omni Video Editor

Copy and paste the prompt below into Google Omni / Veo / video synthesis tool:

```text
A sleek, high-end 2-minute product showcase video for an Android mobile application named "Crusty". 

Visual Style: Google Pixel advertising aesthetic — clean, minimalist, human-centered, Material 3 Expressive UI, smooth motion graphics, 60fps frame rate. Warm neutral studio background (soft slate gray with subtle ambient lighting). 

Content Flow:
1. Close-up shot of a modern Google Pixel phone in Dark Mode on a sleek wooden desk. The phone status bar clearly shows Airplane Mode toggled on.
2. The user taps the Instagram app icon. Immediately, a smooth transition reveals the Crusty interception UI with a warm, conversational chat prompt asking "Why do you need access right now?".
3. Kinetic typography highlights on-screen text: "On-Device Gemma 4 Inference".
4. The user types "Just checking something quick". The AI responds instantly with a firm, helpful pushback: "What specifically? Vague excuses don't unlock time."
5. User types "Replying to Sarah's message". The AI streams a response proposing a 10-minute grayscale pass.
6. Smooth UI interaction showing Instagram launching with a grayscale filter applied, accompanied by a clean notification countdown timer.
7. Cut to a sleek animated architecture graphic showing the "Propose & Clamp" system: a neural network node (Gemma 4) passing a proposal into a solid shield icon labeled "Deterministic Policy Engine (Pure Kotlin)". 
8. Ending screen: Crisp white typography on dark background reading "Crusty — The AI App Blocker You Negotiate With. 100% On-Device. Zero Network."

Audio: Warm, confident, calm male narrator voice with clear English pronunciation. Ambient electronic background music — subtle, uplifting, low-tempo synth pads without heavy beats.
```

---

## Best Practices & Assets to Provide for Video Generation

To get the most professional output when designing/editing this video, provide these assets to your editor or AI generator:

### 1. Screen Recordings (High-Quality Raw Source)
- Record at **native resolution in 60fps** (1080p or 4K) using Android built-in screen recorder or `scrcpy --max-fps 60`.
- Keep **Airplane Mode clearly visible** in the status bar at all times to emphasize the offline, on-device nature of Gemma 4.
- Perform smooth, deliberate finger taps/swipes (enable "Show Taps" in Android Developer Options).

### 2. Branding & Graphic Assets
- **Device Frame:** Frame screen recordings inside a clean 2D vector or 3D Google Pixel 9 device mockup (no bulky third-party frames).
- **Architecture Diagram:** Export the Mermaid architecture diagram as a high-res vector/SVG or high-res PNG.
- **Font & Palette:** Stick to Google Sans / Roboto, Material 3 Dark Palette (`#121212` background, soft teal `#A8C7FA` accents).

### 3. Voiceover & Audio
- Record voiceover using a high-quality mic (or Studio-quality AI voiceover like ElevenLabs or Google Speech Synthesis with "Calm/Warm Tech Presenter" preset).
- Keep background music volume at **-18dB to -22dB** so the voiceover remains crystal clear.

---

## Google PM & Design Aesthetic Checklist

Google Product Managers and hackathon judges prioritize:
1. **Focus on the Innovation (The "Propose & Clamp" Invariant):** Don't just show a chat app. Make sure beat 1:40 (the PolicyEngine clamp diagram) is prominent.
2. **Authenticity over Polish:** Showing the real working app running offline on a real device carries significantly more weight than generic stock videos.
3. **No Fluff / Fast Pacing:** Get to the core value proposition in the first 10 seconds.
