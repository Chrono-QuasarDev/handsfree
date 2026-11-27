# ADR 0001 — Pure Kotlin engine module, separate from Android

**Status**: Accepted (2026-11-27)

## Context
The flow engine is the safety-critical part: it decides what gets tapped on a USSD dialog and when to freeze at the PIN field. The builder (an AI) is fast but has no judgment. Code review alone cannot guarantee the engine never accidentally depends on Android framework state (clocks, logging of screen text, telephony APIs) that would make it fragile, untestable, or leaky.

## Decision
`:core:engine` and `:core:model` are pure Kotlin/JVM modules — no Android Gradle plugin. `import android.*` fails to compile. The engine takes only plain data in (`UssdScreen`) and emits plain data out (`EngineResult`), with an injected clock. All Android integration lives in `:app` behind a thin adapter.

## Consequences
- Engine tests run on the JVM in seconds; no emulator needed in CI.
- Purity is compiler-enforced, not review-enforced.
- Slight overhead: `:app` must translate accessibility events into `UssdScreen` and execute `EngineResult` — which we wanted anyway (non-negotiable #8).
- Cost: none meaningful at this scale.

## Alternatives rejected
- Single-module app with a "pure" package: relies on discipline; compiler can't help.
- Robolectric for engine tests: slower, and it invites Android deps into logic.
