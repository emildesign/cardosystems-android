# Device Connectivity SDK — Design Document (Android)

---

## Public API Surface

The SDK exposes a single entry point: `DeviceConnector`.
The goal was to keep the surface as small as possible — if it's not needed by a consumer, it's not public.

```kotlin
interface DeviceConnector {
    val connectionState: StateFlow<ConnectionState>
    val deviceData: StateFlow<DeviceData?>
    suspend fun connect(deviceId: String)
    suspend fun disconnect()
    suspend fun setVolume(level: Int): Result<Unit>
    fun release()
}
```

**Rationale per method:**

- `connectionState` — always-available stream of the current lifecycle state. Starts as `Idle`. `StateFlow` replays the latest value to new collectors so the UI never misses the current state.
- `deviceData` — nullable because there is no data until the device sends its first update after connecting.
- `connect(deviceId)` — suspends until the transport has processed the request. No-op if already connecting or connected.
- `disconnect()` — suspends until teardown is complete. No-op if already idle or failed.
- `setVolume(level)` — returns `Result` rather than throwing. A failure means the consumer called it at the wrong time (not connected), which is a recoverable situation not a crash.
- `release()` — cleans up internal resources. Should be called when the consumer is done with the SDK (e.g. in `ViewModel.onCleared()`).

**Why an interface instead of a concrete class:**
It makes it easy to swap the real implementation for a test double without changing any consumer code. The consumer always holds `DeviceConnector`, never `DeviceConnectorImpl`.

**Factory:**
```kotlin
val connector = DeviceConnector.create()
```
The implementation class (`DeviceConnectorImpl`) is `internal` — consumers cannot instantiate it directly.

**Public types:**

- `ConnectionState` — sealed class with five states: `Idle`, `Connecting`, `Connected(deviceId)`, `Disconnecting`, `Failed(reason)`.
- `DeviceData` — data class with `volume: Int` (0–10) and `battery: Int` (0–100).
- `DisconnectReason` — sealed class with `Timeout`, `UnexpectedDisconnect`, `ConsumerDisconnected`.

---

## Concurrency Model

- All `StateFlow` emissions are delivered on the **main thread** (`Dispatchers.Main.immediate`).
- `connect()`, `disconnect()`, and `setVolume()` are `suspend` functions — the caller controls which coroutine they run on.
- Internal transport work (delays, simulated IO) runs on `Dispatchers.IO`.
- The SDK holds a `CoroutineScope(SupervisorJob() + Dispatchers.Main)`. `SupervisorJob` means a failure in one child coroutine does not cancel the whole SDK scope.
- `setVolume()` uses a `Mutex` to serialize concurrent calls — if two volume commands arrive at the same time, they are processed one after the other, not interleaved.

**What happens on concurrent calls:**
- `connect()` while already connecting or connected → no-op, guarded by state check.
- `disconnect()` while idle or failed → no-op, guarded by state check.
- Multiple `setVolume()` calls in parallel → serialized via `Mutex`.

---

## Error Model

Runtime errors are **state transitions**, not exceptions.

- `connect()` does not throw. A timeout or unexpected drop transitions state to `Failed(reason)`.
- `setVolume()` returns `Result.failure` if called while not connected — this is a usage error the consumer should handle, but it should not crash the app.
- There are no callbacks or error delegates on the public API. The consumer observes `connectionState` and reacts to `Failed`.

**Why state transitions instead of exceptions:**
Errors as state transitions are safer — the consumer reacts by observing state, not by catching exceptions in the right place. Missing a `try/catch` is silent; missing a `Failed` state in a `when` block produces a compiler warning.

---

## State Model

```
Idle ──connect()──► Connecting ──success──► Connected
                        │                       │
                     timeout                disconnect()
                     or error                    │
                        │                        ▼
                        └──────────────► Disconnecting ──► Idle
                        │
                        ▼
                      Failed
                        │
                     connect()
                        ▼
                     Connecting
```

**Legal transitions:**

| From          | To            | Trigger                   |
|---------------|---------------|---------------------------|
| Idle          | Connecting    | `connect()`               |
| Connecting    | Connected     | transport success         |
| Connecting    | Failed        | timeout / transport error |
| Connected     | Disconnecting | `disconnect()`            |
| Connected     | Failed        | unexpected disconnect     |
| Disconnecting | Idle          | teardown complete         |
| Failed        | Connecting    | `connect()` retry         |

**Deliberately disallowed:**
- `Connected → Connecting` — must disconnect first.
- `Idle → Disconnecting` — nothing to tear down.
- `Connecting → Disconnecting` — not supported in v1; cancel and retry instead.

---

## Lifecycle and Resource Handling

The SDK does not manage its own lifecycle. The consumer (ViewModel) is responsible for calling `release()` when done.

**Why the consumer controls lifecycle:**
Some apps need the connection to stay alive when the screen is backgrounded — for example an app that monitors a device in the background. If the SDK shut itself down automatically, that use case would be impossible. Giving the consumer full control means they decide whether to tie the connector to a screen or keep it alive at the app level.

**What happens in each scenario:**
- Consumer calls `disconnect()` → graceful teardown, state goes to `Idle`.
- Consumer forgets to call `release()` → the mock transport will eventually fire an `UnexpectedDisconnect`. A real transport would rely on OS-level socket teardown.
- Process is killed → OS cleans up all coroutines and sockets. No persistent state exists.
- App goes to background → connection stays alive. The ViewModel is not destroyed on background, so the connector keeps running. The consumer should decide in `onCleared()` whether to disconnect or leave the connection alive for a foreground service to take over.

---

## v2 Change

**Separate volume and battery into independent observables.**
Currently a battery update re-emits `DeviceData` with an unchanged volume, triggering an unnecessary recomposition for the volume component. In v2 I'd expose `val volumeState: StateFlow<Int>` and `val batteryState: StateFlow<Int>` separately.

I didn't do this in v1 because the single `DeviceData` class is simpler and perfectly adequate for a mock SDK. It's an optimization, not a correctness issue.
