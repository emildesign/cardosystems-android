# AI Usage

## Tools Used

- **Claude (Anthropic)** — used throughout as a pair programmer for architecture design, code generation, and documentation.
- **Google Gemini (Android Studio inline)** — used for inline code suggestions and quick fixes while writing code directly in the IDE.
- **OpenAI Codex** — used for inline code suggestions during development.

---

## What I Reviewed, Kept, or Rejected

I treated Claude as a first draft generator — everything it produced was read before being added to the project.

**Kept after review:**
- The overall state machine design and the interface pattern for the public API surface.
- The transport layer structure — `DeviceTransport` as an internal interface with `MockDeviceTransport` as the implementation.
- The `FakeTransport` test double pattern (hand-rolled, not a mock framework).
- `DeviceData` as a single bundled type, accepting the v2 trade-off documented in DESIGN.md.

**Modified after review:**
- Coroutine scope management — adjusted the `SupervisorJob` setup after reviewing how scope cancellation interacts with `release()`.
- Several files had access modifier issues (`public` vs `internal`) that needed correction after review.
- `SharingStarted.Companion.WhileSubscribed` corrected to `SharingStarted.WhileSubscribed` in the ViewModel.

**Rejected:**
- Claude suggested a `DeviceConnectorFactory` class — rejected in favour of a simple `companion object` factory method which is more idiomatic Kotlin.
- Some over-engineered abstractions in early drafts that added layers without adding value.

---

## Where AI Fell Short

**Project configuration:**
The first generated Android project had multiple errors in the Gradle configuration and module setup — wrong dependency versions, incorrect module references, and missing configurations. 
These were fixed using Gemini and manual configuration inside Android Studio, which has better awareness of the local project context than a chat-based AI.

---

## How I Verified Correctness

- Read every generated file before adding it to the project. Traced the state machine transitions manually against the diagram in DESIGN.md.
- Built and ran the app on an emulator and manually verified all scenarios — connect, disconnect, volume change, battery drain, timeout, and unexpected disconnect.
- Ran all unit tests with to confirm they pass.
- Used Gemini and Codex to review the code for improvements after the initial implementation.
- Treated compiler errors and test failures as signals that something was wrong with the design or the AI output.
