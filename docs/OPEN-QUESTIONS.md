# Open questions

Log blockers here with your task ID, make the most reasonable assumption, mark it `// ASSUMPTION:` in code, and keep moving.

| Question | Status | Resolution |
|---|---|---|
| Exact `.litertlm` filename for `gemma-4-E2B-it` on the `litert-community` HF org | **Assumed** | `ModelProvider` expects `gemma-4-E2B-it.litertlm` and also probes `/data/local/tmp/` and `/sdcard/Download/`. **Verify the real filename on Hugging Face before demo day** — if it differs, only `MODEL_FILENAME` changes |
| Does `Backend.GPU()` work on the demo Pixel? | **Open** | CPU fallback is implemented in `InferenceManager`. Measure both and record tokens/sec |
| Toolchain: stay on AGP 8.7.3 / Kotlin 2.1.0 / BOM 2024.10.01? | **Resolved — stay** | It builds and the APK packages `liblitertlm_jni.so`. Current 2026 versions (AGP 9.2.0, Kotlin 2.4.0, BOM 2026.08.00) work too but need `kotlin.android` and `kotlinOptions {}` removed — AGP 9 has Kotlin built in. Not worth the risk before the demo works |
| M3 Expressive components? | **Resolved — no** | `material3:1.3.1` predates them. Dynamic color, the part that carries the native-Pixel feel, works. See `03-UI-SPEC.md` |
| JDK | **Resolved** | Java 21. `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home` on this machine. Deliberately **not** committed to `gradle.properties` — machine-specific |
| Rule-change cooldown was descoped for the one-day build | **Built anyway** | `DefaultPolicyEngine.scheduleRulesChange()` implements it: tightening applies immediately, loosening waits 2h. Covered by tests |
| Avatar asset file | **Open** | Need avatar asset file from the team. Implemented canonical `CrustyAvatar` wrapper with placeholder marked `// PLACEHOLDER: swap for real avatar asset` |
