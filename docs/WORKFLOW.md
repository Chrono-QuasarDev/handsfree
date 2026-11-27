# WORKFLOW — how this shop actually runs

Three people: **Supervisor** (product, device QA, merges), **Planner** (me: architecture, cards, frozen tests, review), **Builder** (Antigravity: code, one card at a time).

## 1. The loop

```
card drafted → ISSUED → branch → red tests → implement → PR → CI green
     → planner review → supervisor merge (squash) → device QA → tag → next card
```

**WIP limit: exactly one card in flight.** No parallel branches. No "while I'm here" work.

## 2. Card states (= PR states)

| State | Owner | Exit condition |
|---|---|---|
| DRAFTED | Planner | — |
| ISSUED | Planner | Pasted to builder |
| IN PROGRESS | Builder | PR opened with card id in title |
| IN REVIEW | Planner | CI green + diff <400 lines + tests unmodified (engine) |
| APPROVED | Supervisor | Squash-merge to `main` |
| VERIFIED | Supervisor | Device QA passed (if card touches device behavior) |
| DONE | — | Tag if milestone |

## 3. Branch, commit, PR rules

- Branch: `card/E3-engine-happy-path` (lowercase, card id first).
- Commits: Conventional Commits — `feat(engine): tap option on contains match`, `test(engine): frozen happy-path suite`, `fix(app): parser null node guard`.
- PR title: `card(E3): FlowEngine happy path` — the card id is mandatory, it's how we audit that nothing else snuck in.
- Squash merge only. `main` is protected: no direct pushes, CI required, linear history.
- **Diff budget: <400 changed lines** (excluding the frozen test file). Over budget → the card was too big; split it.

## 4. CI gates (all must pass)

1. `:core:model:test` + `:core:engine:test` (JVM, seconds — runs first, fails fast).
2. `lint` + `assembleDebug`.
3. **Permission audit** (`scripts/check-manifest-permissions.sh`): only `INTERNET` allowed; any new permission fails the build.
4. **Engine purity**: grep for `import android` under `core/` → fail if found (belt and braces on top of the no-Android-plugin setup).
5. Detekt on `core/` (default ruleset; style only, not a blocker to argue about — fix, don't configure).

## 5. Frozen tests — the obedience mechanism

For every engine card, the planner supplies the **complete literal test file**. The builder:
- must copy it in **byte-for-byte**,
- may not edit, delete, skip, or "improve" it,
- makes it pass with the minimum correct implementation.
If a test appears wrong: **stop, report to supervisor, do not modify.** A builder with no judgment is only as safe as the walls around it. These tests are the walls.

## 6. Planner review protocol (every PR)

- Read the full diff. Engine diffs are reviewed line by line.
- Checklist: PIN gate checked first? timeouts injected, not ambient? no coordinate taps? no `getText()` on password fields? no new deps/SDKs? tests byte-identical to card? anything outside card scope?
- Verdict: APPROVE / REQUEST CHANGES with card-id-labelled comments. No "LGTM, minor stuff later."

## 7. Supervisor device QA (cards that touch the phone)

On a real device, on a real MTN SIM:
1. Run the flow twice. Check: correct option tapped, PIN gate froze at the PIN screen, nothing typed after.
2. Screenshot every dialog into `flows/mtn/`.
3. Update the flow's VERIFIED line (date, device, screenshots).
4. Anything weird → send me the HUD log; I amend the card, not the tests.

## 8. Releases

- Tags at milestones: `v0.1.0-engine` (E9), `v0.2.0-device` (S4), `v0.3.0-beta` (P-series done).
- Release builds: R8 on, debug HUD off, **no logging of dialog text in release**, PIN gate test suite runs in CI for every tag.
- Store submission (T-series) uses the demo-video evidence captured during device QA.

## 9. Escalation

| Situation | Action |
|---|---|
| Builder blocked >20 min | Stop. Report error text + files attempted. Wait for amended card. |
| Test fails for unclear reason | Do not weaken the test. Report. Planner diagnoses. |
| Card needs something outside scope | Builder proposes in PR description only. Never implements it. |
| Supervisor wants scope change | New card, prioritized. Never a mid-card pivot. |

## 10. Definition of done (every card)

- [ ] CI green (all five gates)
- [ ] Diff <400 (excluding frozen tests)
- [ ] Frozen tests byte-identical
- [ ] Planner review approved
- [ ] Device QA passed (if device-touching)
- [ ] No new permission, dependency, or module (or an approved ADR)
- [ ] Card id in PR title and merge commit
