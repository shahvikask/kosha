# GrowUrDhan implementation status

Updated: 2026-10-16 after the dependency review. The pins were not changed.

## Repository

| Item | Value |
|---|---|
| Remote | `https://github.com/shahvikask/kosha` |
| Working branch | `GUD-Android-Redevelopment` |
| Frozen reference | `GUD-Android-Gmail` @ `af96dcc8967ab595e1d9acee78e29e500cb99a9e` |
| GitHub tip at the start of this review | `b949c1d26cd8145b5ecf9861b0190bb27273c0e8` |
| Last accepted SHA | none |
| Native app | scaffold only, application id `in.growurdhan.redevelopment` |
| CI | not run |

## Milestone

**Current:** M00 — not accepted.

| Milestone piece | Status |
|---|---|
| Instruction files and legacy inventory | on GitHub before this change |
| Screen, command, schema v9, and parser-hash inventory | present. HoldingDetails keys are listed. Widget-by-widget form inventory is not done |
| `android-native/` scaffold | debug APK assembled locally. Locked, sample home, and sample holding concept screens |
| Lock behavior | `ConceptLock.vaultOpen` is always false. There is no unlock method and no password field |
| Section 6A screenshots | NOT CAPTURED. Compose semantics for the three screens passed. Owner approval is pending |
| Dependency review | written in `docs/implementation/DEPENDENCY-REVIEW.md`. Pins were not changed |
| SQLCipher, Chaquopy, financial ledger | not started |
| M24–M34 | NOT STARTED |

## Requirements

P01–P39 and E01–E18 remain **NOT STARTED**. See `parity-register.csv`.

## UI status, separate from financial status

| Surface | Status |
|---|---|
| Locked, sample home, sample holding | Compose source compiled. Semantics tested. Not a finished screen |
| Other legacy surfaces | mapped, not built |
| Brand mark | SVG snippets checked against `MarkGeometry`. Rendered pixels were not captured |

## Gates

| Gate | Status |
|---|---|
| Local debug assemble | PASS for `:app:assembleDebug`. Unsigned. SHA-256 recorded in the session handoff after this change |
| Model JVM tests | 10 tests PASS via `org.junit.runner.JUnitCore` |
| Compose semantics | `ConceptVisualTest` 3 tests, 0 failures, via `:app:testDebugUnitTest` |
| Bitmap capture | FAILED to produce an image. See decision D04 |
| `./gradlew :core:model:test` | NOT PASSED. The Gradle test worker still drops its socket |
| GitHub Actions | NOT RUN — not authorized |
| Emulator and device | NOT RUN |
| Gmail and Play Billing | BLOCKED |
| Security signoff and owner visual acceptance | PENDING |

## Next authorized work

Do not rework the lock flow. Do not upgrade AGP, Kotlin, or Compose unless the owner asks. Do not start M24. Do not add a database. Screenshot pixels remain blocked in the last environment. Do not dispatch Actions unless the owner authorizes them.
