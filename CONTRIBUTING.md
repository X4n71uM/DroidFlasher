# Contributing to DroidFlasher

We welcome contributions from firmware developers, repair technicians, and Android enthusiasts! Please take a moment to review this document to ensure smooth collaboration.

---

## 🛠️ Development Setup

1. **Clone & Open**:
   ```bash
   git clone https://github.com/your-username/DroidFlasher.git
   ```
2. Open the project in the latest version of **Android Studio**.
3. Ensure you have the Android SDK for **API 36** installed.

---

## 🧪 Testing Guidelines

Before submitting a Pull Request, please ensure all tests pass:

```bash
# Run JVM & Robolectric unit tests
./gradlew testDebugUnitTest

# Build debug APK
./gradlew assembleDebug
```

### Adding New Protocol Implementations
- Protocol parsers (such as LOKE packet handlers or PIT decoders) must include unit test coverage under `app/src/test/java/net/xantium/droidflasher/`.
- Ensure boundary cases (zero-length packets, truncated frames, corrupted headers) are handled safely with clear log messages.

---

## 📋 Pull Request Process

1. Fork the repo and create your feature branch:
   ```bash
   git checkout -b feature/my-new-feature
   ```
2. Write clean, readable Kotlin code following the Android Kotlin Style Guide.
3. Keep Jetpack Compose UI declarative and testable (`Modifier.testTag` on primary interactive elements).
4. Commit your changes with descriptive commit messages.
5. Push to your fork and submit a Pull Request.

---

## ⚖️ Safety & Compliance

- **No Malicious Payloads**: Do not submit PRs containing exploit chains, lock bypassers, or security circumvention tools. DroidFlasher is built strictly for legitimate maintenance, testing, repair, and firmware development.
