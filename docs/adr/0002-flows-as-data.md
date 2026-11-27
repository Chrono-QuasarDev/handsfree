# ADR 0002 — USSD flows are data (JSON), not code

**Status**: Accepted (2026-11-27)

## Context
MTN reorganizes menus; other networks and OEM dialog variants will follow. Each new flow as Kotlin code means a release, a review, and a new chance for the builder to sneak logic into the engine. But every dialog-text decision on screen is a safety surface.

## Decision
Flows live in `assets/flows/<network>/<flow>.json`, parsed strictly by `kotlinx.serialization` (`ignoreUnknownKeys = false`). A flow must declare: id, network, entry dial code, params, ordered steps (expect → action), timeout, and a `verified` block (who, date, device, screenshot refs). Validation rejects: malformed dial codes, >12 steps, missing `verified` in release builds, unknown keys. Adding a flow = adding a JSON file whose values come only from supervisor device verification.

## Consequences
- New network/flow = asset change, reviewed as data (fast, low risk).
- The engine's rule set stays tiny and testable; matching semantics (text over index, fail-open) are engine responsibility, not per-flow.
- Strict parsing means typos fail loudly at load, not silently at a cash-out.
- If flows ever need conditional logic beyond matching, that's an engine change with a new ADR — not a JSON trick.

## Alternatives rejected
- Flows as Kotlin DSL: every flow edit becomes a code change; invites logic creep.
- Remote flow updates: adds network attack surface and contradicts "no dialog data leaves the device" trust posture. Revisit only with a signed-manifest ADR.
