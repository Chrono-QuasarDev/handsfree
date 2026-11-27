## Card
- **Card ID:** `CARD-ID`
- **Diff size (excluding frozen tests):** `___ lines`

## Builder Checklist
- [ ] Frozen tests unmodified (byte-identical to card)
- [ ] No new dependencies or permissions beyond the card
- [ ] Diff under 400 lines

## Reviewer Checklist (WORKFLOW §6)
- [ ] Read the full diff (engine diffs reviewed line by line)
- [ ] PIN gate checked first?
- [ ] Timeouts injected, not ambient?
- [ ] No coordinate taps?
- [ ] No `getText()` on password fields?
- [ ] No new deps/SDKs?
- [ ] Tests byte-identical to card?
- [ ] Anything outside card scope?

**Verdict:** `APPROVE` / `REQUEST CHANGES`
