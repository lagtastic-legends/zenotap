# ⚡ ZenoTap — Next-Gen Android Keyboard & Tactile Media Deck (v3.15.0)

<p align="center">
  <img src="https://img.shields.io/badge/Release-v3.15.0_Flagship-06B6D4?style=for-the-badge&logo=android&logoColor=white" alt="Release" />
  <img src="https://img.shields.io/badge/Platform-Android_8.0+_(API_26--36)-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Kotlin-2.0_(JVM_21)-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Trackpad-50%25_Spacebar_Scrubbing-F59E0B?style=for-the-badge" alt="Trackpad" />
  <img src="https://img.shields.io/badge/Themes-Obsidian_%26_Terracotta-8B5CF6?style=for-the-badge" alt="Themes" />
  <img src="https://img.shields.io/badge/Memory-Strictly_<30MB-10B981?style=for-the-badge" alt="Memory Safe" />
  <img src="https://img.shields.io/badge/Privacy-100%25_Zero--Telemetry-EC4899?style=for-the-badge" alt="Privacy" />
  <img src="https://img.shields.io/badge/License-MIT-gray?style=for-the-badge" alt="License" />
</p>

---

## 📖 Overview

**ZenoTap** is a flagship, hardware-inspired Android Input Method Service (`IME`) engineered for rapid text input and instant 1-tap rich media (GIF, WebP & sticker) injection via the Android **CommitContent API** (`InputConnectionCompat`).

Featuring **3D physical tactile keycaps**, an **ergonomic 50% spacebar with horizontal cursor-scrubbing trackpad**, **accelerate-on-hold backspace**, **zero-lag floating key echo previews**, and **cross-boundary dynamic theming (Obsidian OKLCH & Terracotta Clay)**, ZenoTap combines physical mechanical keyboard tactile aesthetics with ultra-lightweight memory management (`<30MB` heap ceiling).

Unlike commercial keyboards filled with cloud telemetry, keyloggers, and bloated background daemons, ZenoTap is **100% offline**, **privacy-first**, and requests **zero internet permissions**.

---

## 📦 Releases & Direct APK Downloads (v3.15.0)

Pre-compiled, cryptographically verified Android APKs for release **v3.15.0**:

| Package / Artifact | Architecture | File Size | Description | Download |
| :--- | :--- | :---: | :--- | :---: |
| **`zenotap-v3.15.0-standalone.apk`** | Universal | **~5.49 MB** | ⚡ Standalone ultra-lightweight native keyboard companion app | [⬇️ Download](../../releases/tag/v3.15.0) |
| **`zenotap-v3.15.0.apk`** | Universal | **~11.98 MB** | 🚀 Flagship ZenoDeck + ZenoTap Suite with In-App Studio & Simulator | [⬇️ Download](../../releases/tag/v3.15.0) |
| **`zenotap-v3.15.0-arm64-v8a.apk`** | 64-bit ARM | **~11.98 MB** | Optimized for modern 64-bit Android smartphones & tablets | [⬇️ Download](../../releases/tag/v3.15.0) |
| **`zenotap-v3.15.0-armeabi-v7a.apk`** | 32-bit ARM | **~11.98 MB** | Optimized for legacy 32-bit Android devices | [⬇️ Download](../../releases/tag/v3.15.0) |
| **`zenotap-v3.15.0-x86_64.apk`** | x86_64 | **~11.98 MB** | Android Studio Emulators, Chromebooks & desktop virtualization | [⬇️ Download](../../releases/tag/v3.15.0) |

> [!TIP]
> If you only want the keyboard without the companion web-based deck manager, install **`zenotap-v3.15.0-standalone.apk`** (**5.49 MB**). For the complete ecosystem featuring drag-and-drop vault management and the live chat simulator, install **`zenotap-v3.15.0.apk`**.

---

## ✨ Flagship v3.15.0 UI/UX Innovations

### 1. 🎛️ 3D Tactile Hardware Keycaps & Physical Depth
- **Bespoke XML Layer-List Drawables**: Every keycap is rendered using pure vector layers featuring a `1dp` top specular highlight, `8dp` rounded ergonomic corners, recessed bottom bezel shadows, and an illuminated neon glow when pressed.
- **Obsidian & Terracotta Dual Theming**:
  - **Obsidian Cyber (Default)**: Deep OKLCH cyber-violet surface (`#0E0F14`), cyan accents (`#22D3EE`), and electric glow keycaps.
  - **Terracotta Clay**: Warm tactile 60/30/10 palette (`#FAF5EE` background, `#342A24` bezel, `#C2652A` warm clay accent).

### 2. 🕹️ 50% Spacebar Cursor Trackpad
- **Ergonomic 50% Deck Width**: The spacebar occupies exactly `50%` of the bottom row (`layout_weight="0.5"`), flanked by quick-access punctuation keys.
- **Horizontal Cursor Scrubbing**: Slide your thumb left or right across the spacebar trackpad to scrub the text cursor through input fields.
- **Physical Tick Haptics**: Triggers crisp `VibrationEffect.EFFECT_TICK` (or `HapticFeedbackConstants.CLOCK_TICK`) for each character increment (>12dp threshold).
- **Accidental Insertion Guard**: Automatically suppresses space character insertion if a cursor drag gesture was detected.

### 3. ⚡ Smart Tactile Backspace & Word Swipe
- **Accelerate-on-Hold**: Deletion speed dynamically ramps up the longer backspace is held down.
- **Swipe-Left Word Deletion**: Flick left across the Backspace key (>30dp delta) to instantly delete the entire preceding word with a distinct haptic detent.

### 4. 🪟 Zero-Lag Pre-Inflated Key Echo Preview
- **Pre-Inflated View**: Uses a dedicated, pre-inflated top-ribbon indicator (`tv_key_echo_preview`), eliminating the micro-stutter and layout-inflation lag typical of legacy popup windows.
- **Live Visual Echo**: Displays active character presses, cursor scrub directions (`◀ CURSOR`, `CURSOR ▶`), and deletion states.

### 5. 🗄️ Glassmorphic Media Vault & Category Filter
- **12dp Rounded Glass Cards**: Media cards feature frosted backgrounds, format badges (`GIF` in Cyan, `STICKER` in Emerald), file size micro-labels, and pin badges.
- **Instant Category Strip**: Filter instantly between `All`, `🔥 Fire`, `⚡ Hype`, `✨ Vibe`, `😂 LOL`, `❤️ Love`, `⭐ Favs`, and `🕒 Recent`.
- **Long-Press Actions**: Long-press any media cell to open a quick context drawer to Favorite/Pin, Copy to Clipboard, or Remove.

### 6. 🌐 Dynamic MIME Detection & Smart Fallback
- **Live Host App Compatibility**: Detects active input capabilities in real-time. Shows a green indicator dot (`GIF • WebP Ready`) on apps supporting CommitContent (WhatsApp, Discord, Slack, Telegram, Signal, Google Messages).
- **Graceful Clipboard Fallback**: If an app restricts direct media insertion, ZenoTap provisions a secure temporary `FLAG_GRANT_READ_URI_PERMISSION` and copies the item to the system clipboard with an informative HUD toast.

---

## 🏛️ Architecture & Clean Pipeline

```
com.zenotap.keyboard/
├── DeckStorageManager.kt    # Atomic file operations, auto-healing starter deck & recents/favorites
├── GifDeckAdapter.kt        # 3-column DiffUtil RecyclerView adapter with format badges
├── GifThumbnailLoader.kt    # Zero-dependency LruCache thumbnail engine (<30MB strict ceiling)
├── MainActivity.kt          # Interactive onboarding dashboard, live status badges & test sandbox
└── ZenoTapKeyboardService.kt# InputMethodService with 50% spacebar trackpad, backspace swipe & theming
```

### Media Injection & CommitContent Dataflow
```
[User Taps Vault Card]
         │
         ▼
[ZenoTapKeyboardService]
         │
         ├─► EditorInfo.contentMimeTypes contains "image/gif" or "image/webp"?
         │        │
         │        ├─► [YES] Direct Commit:
         │        │         InputContentInfoCompat(contentUri, description, linkUri)
         │        │         InputConnectionCompat.commitContent(...)
         │        │         ──► Injected directly into WhatsApp / Discord / Messages!
         │        │
         │        └─► [NO]  Graceful Clipboard Fallback:
         │                  FileProvider.getUriForFile()
         │                  ClipboardManager.setPrimaryClip(...)
         │                  Toast: "📋 Copied media to clipboard (Host app does not support direct insertion)"
```

---

## 🔒 Memory & Privacy Guarantees

| Metric / Directive | Architectural Constraint | Verification / Enforcement |
| :--- | :--- | :--- |
| **Max Heap Allocation** | **Strictly `< 30 MB`** | Verified via Android Profiler & memory leak guards |
| **Thumbnail Extraction** | **`< 15 KB` per frame** | Downsampled `inSampleSize` decoding via `GifThumbnailLoader` |
| **Spacebar Width** | **Strictly 50% (`0.5`)** | Fixed XML `layout_weight="0.5"` constraint |
| **Network Permissions** | **Zero (`0`)** | `android.permission.INTERNET` is completely absent from IME |
| **Keystroke Storage** | **None** | No key logging, no disk serialization of typed strings |
| **File Sandbox** | **Internal Storage** | Isolated inside `context.filesDir/zenotap_deck/` with strict FileProvider |

---

## 🚀 Getting Started & Installation

### Step 1: Sideload APK
1. Download **`zenotap-v3.15.0.apk`** (or `zenotap-v3.15.0-standalone.apk`) to your Android device.
2. Tap the downloaded file to install (allow "Install unknown apps" if prompted).

### Step 2: Enable & Select in Settings
1. Open the **ZenoTap** app from your home screen.
2. Follow the 2-step onboarding dashboard:
   - **Step 1: Enable Keyboard** — Taps directly into *System Settings > System > Languages & input > On-screen keyboard* to toggle ZenoTap ON.
   - **Step 2: Select Keyboard** — Pops open the Android Input Method Picker to select ZenoTap.
3. Use the built-in **Live Chat Sandbox** to test typing with the 50% spacebar and injecting reaction media!

---

## 🛠️ Building from Source

### Prerequisites
- **JDK 21** (`JAVA_HOME` pointing to JDK 21 or Android Studio JBR `jbr-21.0.11`)
- **Android SDK 36** (`compileSdk 36`, `minSdk 24`, `targetSdk 36`)
- **Gradle 8.14.5**

### Build Commands
```bash
# Clone the repository
git clone https://github.com/lagtastic-legends/zenotap.git
cd zenotap

# Set JDK 21 environment (PowerShell example)
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio1\jbr"

# Verify Kotlin compilation
./gradlew compileDebugKotlin

# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease
```

Generated APKs will be output to:
- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/apk/debug/app-debug.apk`

---

## 🤝 Ecosystem Integration: ZenoDeck Monorepo

ZenoTap is the official native keyboard companion to **[ZenoDeck](https://github.com/lagtastic-legends/zenodeck)**, the full-stack creator suite built with Next.js 16, Capacitor 7, and OKLCH color spaces.

When paired with ZenoDeck:
- Curate and drag-and-drop vault reaction packs directly in your desktop or mobile browser.
- Preview keystrokes in real-time with the **Interactive 1:1 Live Keyboard Simulator**.
- Synchronize custom media and categories over secure local broadcast or LAN sync.

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for complete details.

Copyright (c) 2026 Lagtastic Legends.
