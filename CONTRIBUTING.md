# Contributing to ZenoTap

We welcome contributions to ZenoTap! Whether you want to improve rich content injection compatibility, add new themes, optimize memory and GIF decoding, or enhance accessibility, your help is appreciated.

## Development Setup

1. **Prerequisites**:
   - Android Studio Meerkat or newer
   - JDK 21+ (`JavaVersion.VERSION_21`)
   - Android SDK API 36 with Build-Tools installed

2. **Clone & Build**:
   ```bash
   git clone https://github.com/lagtastic-legends/zenotap.git
   cd zenotap
   ./gradlew assembleDebug
   ```

3. **Install on Device/Emulator**:
   ```bash
   ./gradlew installDebug
   ```

## Contribution Workflow

1. Fork the repository and create a feature branch (`git checkout -b feature/amazing-feature`).
2. Follow standard Kotlin style guidelines and Android architecture principles.
3. Test compatibility with target messaging apps (WhatsApp, Telegram, Google Messages, Signal).
4. Commit your changes with clear semantic commit messages (`feat: ...`, `fix: ...`).
5. Open a Pull Request targeting the `main` branch.

## Code Standards
- Zero external heavy dependencies: Keep image/GIF parsing memory-safe and lean.
- Sandboxed storage: Never request broad storage permissions (`MANAGE_EXTERNAL_STORAGE`). Keep all media scoped under internal files directory.
- Respect IME lifecycle: Clean up receivers, avoid memory leaks in `onDestroy()`.
