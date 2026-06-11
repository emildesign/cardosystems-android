# Device Connectivity SDK — Android

A small Android SDK that simulates a connection to a remote device, with a sample app that exercises the public API. No real Bluetooth or hardware is required — the transport layer is a mock.

---

## How to Build and Run

**Requirements:** Android Studio Hedgehog (2023.1.1) or newer, JDK 17, Android SDK 34.

```bash
git clone <repo-url>
cd cardosystems-android
./gradlew assembleDebug          # build SDK + app
./gradlew :sdk:test              # run unit tests
./gradlew :app:installDebug      # install sample app on device or emulator
```

Or open in Android Studio: `File → Open → select project root`, then run the `app` configuration.

---

## API Evolution and Backward Compatibility

The public surface is intentionally small: one interface, two `StateFlow`s, and four methods. This makes evolution manageable.

**Non-breaking changes (safe to ship as minor versions):**
- Adding new methods with default implementations on the interface.
- Adding new `ConnectionState` subclasses — consumers using exhaustive `when` with an `else` branch are safe; those without will get a compile error prompting them to handle it.
- Adding new fields to `DeviceData` with default values.
- Adding new `DisconnectReason` subclasses.

**Breaking changes (require a major version bump):**
- Removing or renaming existing `ConnectionState` subclasses.
- Changing the type of `connectionState` or `deviceData`.
- Changing the signature of `connect()`, `disconnect()`, `setVolume()`, or `release()`.

**Strategy:** deprecate before removing. Any method or type marked `@Deprecated` should survive for at least one minor version before being removed in a major version.

---

## Telemetry and Logging

**Would collect:**
- State transition events with timestamps (e.g. Connecting started, Connected, Failed + reason).
- Transport error types and frequency.
- SDK version and Android API level.
- Command latency — time between `setVolume()` call and transport acknowledgment.

**Would intentionally exclude:**
- Device ID — this could identify a specific user's physical hardware and is therefore PII.
- Volume levels — user behavior data that the consumer may consider private.
- Battery levels — device health data that belongs to the device owner, not the SDK.
- Any content from `DeviceData` payloads.

The rule is simple: log what helps debug the SDK, never log what the user is doing or what device they own.

---

## Testing Strategy

Tests focus on the **SDK core only** — state machine transitions, error cases, and no-op guards. The sample app is a test harness, not a product, so UI tests were not written.

**What is tested:**
- All state machine transitions (Idle → Connecting → Connected, timeout, unexpected disconnect, consumer disconnect).
- `deviceData` updates on connected state, and clearing on disconnect.
- `setVolume()` returns failure when not connected.
- `connect()` is a no-op when already connecting or connected.
- `release()` cancels the internal scope so future transport events are ignored.
- Reconnect after a failed state.
- Multiple concurrent `setVolume()` commands are processed sequentially.

**What is not tested:**
- Compose UI — low risk, manually verified.
- ViewModel — thin layer with no logic of its own; tested indirectly through the SDK tests.
- Mock transport timing — non-deterministic delays are not unit tested; only the scenario outcomes are verified.

**Tools:**
- `kotlinx-coroutines-test` with `StandardTestDispatcher` for deterministic coroutine control.
- `Turbine` for `StateFlow` / `Flow` emission assertions.
- `FakeTransport` — a hand-rolled test double in its own file, giving full control over emitted events without timing dependencies.

A hand-rolled fake was chosen over a mock framework (Mockito, etc.) because it makes the test intent clearer and avoids timing dependencies.

---

## Distribution

### Current approach — private GitHub repository (internal use)

The SDK is consumed as a Gradle module within the same repository. This is the right choice while the SDK is for internal use only.

**Why:** Setting up Maven Central requires a Sonatype account, GPG key signing, and a multi-step verification process that can take days. This overhead is not justified for an internal SDK.

**Implications of staying private:**
- Consumers must have access to the GitHub org.
- No versioning enforcement — consumers can reference a branch instead of a tagged release.
- No discovery — other teams can't find the SDK unless told about it.

### If the SDK goes public — Maven Central

```kotlin
implementation("com.emildesign:device-connectivity-sdk:1.0.0")
```

**Implications on API surface and versioning:**
- Semantic versioning becomes a hard contract. A breaking change to the public API surface requires a major version bump (2.0.0).
- The `internal` keyword becomes critical — anything accidentally made `public` becomes part of the API contract and is hard to remove without a breaking change.
- Use the [Binary Compatibility Validator](https://github.com/Kotlin/binary-compatibility-validator) plugin in CI to catch unintentional public API changes.
- `MockDeviceTransport` should be moved to a separate `-testing` artifact so it does not ship in the production SDK but remains available for consumer UI tests.
