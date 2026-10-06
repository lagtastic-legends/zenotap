# ⚡ ZenoTap — Rich Content Android Keyboard

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android_8.0+_(API_26+)-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Language-Kotlin_2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Target_SDK-API_36-00897B?style=for-the-badge" alt="Target SDK" />
  <img src="https://img.shields.io/badge/Architecture-Clean_%2F_Zero--Dependency-blue?style=for-the-badge" alt="Architecture" />
  <img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" alt="License" />
</p>

---

## 📖 Overview

**ZenoTap** is a high-performance, open-source Android custom keyboard (`InputMethodService`) built from the ground up to inject animated GIFs and rich media directly into messaging apps using the Android **CommitContent API** (`InputConnectionCompat`).

Unlike bulky, commercial keyboards loaded with telemetry and background trackers, ZenoTap is:
- 🔒 **Privacy-First & Zero-Permission**: No internet access requested by the IME service, no keylogging, and zero third-party telemetry.
- ⚡ **Lightweight & Instantaneous**: Instant cold-start, custom downsampled thumbnail caching, and zero memory leaks.
- 🎯 **Universal Compatibility**: Works out of the box with WhatsApp, Telegram, Signal, Google Messages, Discord, Slack, and any app supporting rich content input.
- 🛡️ **Clipboard Fallback**: Graceful fallback to system clipboard with temporary URI permissions when target apps don't support direct content injection.

---

## ✨ Features

- **Rich Content Insertion Engine**:
  - Implements `InputConnectionCompat.commitContent` with `FLAG_INPUT_METHOD_EDITOR_CONTENT`.
  - Negotiates MIME types with active input fields to ensure seamless `.gif` insertion.
  - Automatically provisions temporary `FLAG_GRANT_READ_URI_PERMISSION` via secure `FileProvider`.

- **Memory-Safe GIF Deck (`GifThumbnailLoader`)**:
  - Zero external image libraries (Glide/Picasso-free architecture).
  - Native frame extraction with downsampled bitmap decoding and automatic sample-size calculations (`inSampleSize`).
  - Dual-layer memory safety with 15MB bounded `LruCache` to avoid OOM errors even with hundreds of stored GIFs.

- **Fast Onboarding Dashboard (`MainActivity`)**:
  - Real-time IME state detection: Instantly reports if ZenoTap is enabled in system settings and whether it is currently selected as the active input method.
  - One-tap deep links directly to Android Language & Input settings.
  - Input Method Picker quick-launch button.
  - Live deck statistics counter showing currently loaded media assets.

- **Dynamic Shared Deck Storage (`DeckStorageManager`)**:
  - Pre-seeded with built-in reaction GIFs for instant out-of-the-box delight.
  - Atomic `.tmp` file writing prevents partially written or corrupted media files.
  - Broadcast synchronization (`ACTION_DECK_UPDATED`) for real-time grid refreshes when new media is added.

---

## 🏗️ Architecture

```
com.zenotap.keyboard/
├── DeckStorageManager.kt    # Sandboxed storage manager with atomic writes & seeding
├── GifDeckAdapter.kt        # 3-column RecyclerView adapter with DiffUtil
├── GifThumbnailLoader.kt    # Zero-dependency, memory-safe GIF thumbnail extractor & LruCache
├── MainActivity.kt          # Setup & onboarding dashboard with live IME status badges
└── ZenoTapKeyboardService.kt# Core InputMethodService implementing CommitContent & Fallback
```

### Media Flow
```
[Local Deck Storage]
       │
       ▼
[ZenoTapKeyboardService] ──► Checks EditorInfo.contentMimeTypes
       │
       ├─► (Supported) ──► FileProvider.getUriForFile()
       │                         │
       │                         ▼
       │                   InputConnectionCompat.commitContent() ──► [WhatsApp / Messages]
       │
       └─► (Unsupported) ──► ClipData.newUri() + ClipboardManager ──► [Toast Notification]
```

---

## 🚀 Getting Started

### Download APK
Download the latest pre-compiled release APK from the **[Releases](../../releases)** tab:
- `zenotap-v1.0.0.apk` (Production Release)
- `zenotap-v1.0.0-debug.apk` (Debug build with logging enabled)

### Sideload Installation
1. Transfer the `.apk` file to your Android device.
2. Tap the APK to install (enable "Install unknown apps" if prompted).
3. Open **ZenoTap** from your app drawer.
4. Follow the interactive setup cards:
   - **Step 1**: Enable ZenoTap in System Settings.
   - **Step 2**: Switch active keyboard to ZenoTap.
5. Open any chat app (e.g., WhatsApp, Telegram) and start tapping GIFs to inject!

---

## 🛠️ Building from Source

### Prerequisites
- Android Studio Meerkat or newer
- Java Development Kit (JDK 21 or 25)
- Android SDK 36 (`compileSdk 36`, `minSdk 24`)

### Build Commands
```bash
# Clone the repository
git clone https://github.com/lagtastic-legends/zenotap.git
cd zenotap

# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease
```

Compiled APKs will be located at:
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`

---

## 🔒 Security & Privacy

ZenoTap is engineered to protect user privacy:
- ❌ **No Internet Permission**: The keyboard service contains no network communication code and requires no network permissions.
- ❌ **No Key Logging**: Does not read, log, or persist keystrokes.
- 📁 **Scoped Storage**: All media is strictly contained within the app's internal sandbox (`context.filesDir/zenotap_deck/`) and served exclusively via a locked-down Android `FileProvider`.

---

## 🤝 Contributing

Contributions, bug reports, and feature requests are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct and development guidelines.

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
