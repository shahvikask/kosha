# Phase 3 / G11 first-market-release gate plan — 2026-10-27 UTC

**State: G11 NOT DISPATCHED / PHASE 3 NOT ACCEPTED.** M26 automated in-scope engineering gate independently verified SUCCESS (73/73 API35, 144/144 JVM; exact-source 26ffcea1; run 40457888895, original artifact 12714530051). M27 narrower custody-provenance update independently focused-verified (3/3 API35, 144/144 JVM; exact-source d6fadea9; run 40463137619, original artifact 12716415027). Neither establishes broader G11, private issuer P18/P19, manual G14/owner approval, signed release, AppSec or providers. Specification v1.3 §§6A, 17 and G11; [first-release scope](BASE-RELEASE-SCOPE.md) owner decision D57 controls. This is a plan for the *broader release gate after M26*, not evidence that any external provider, private CAS or signed release is available.

## Do not start G11 before
1. Complete and audit [full 73-case M26/G14 source gate 40457888895](https://github.com/shahvikask/kosha/actions/runs/40457888895) exact `26ffcea1aa470af97565d3768540b19d58bf536c`: all expected API35 ADB transcripts and 144 original JVM XML PASS, both new APK original bytes/hash TXT match, two safe 360dp light/dark PNGs visually inspected. If any fail, repair and rerun a targeted test then exactly one corrected full milestone gate.
2. Resolve real G14 owner visual acceptance and outstanding source implementation/functional gaps. Source-only/synthetic screenshot green is not owner signoff.
3. Independently confirm frozen legacy parser hashes, M26 cross-member writes, sealed Android Keyguard, SQLCipher tamper/rollback, correct account-specific CAS owner assignment, and no D57 archive UI.

## Technical release config MUST be corrected and verified BEFORE market acceptance
Current observed `android-native/app/build.gradle.kts` (2026-10-27):
- Debug/native `applicationId = "in.growurdhan.redevelopment"` vs **spec-required production `in.growurdhan.app`**.
- `versionCode = 1`, `versionName = "0.0.1-m00"` (prototype values, not market release).
- `release { isMinifyEnabled = false }`; production R8/minification, resource shrinking, signing, obfuscation, Play AAB/delivery and rules need independent verification and owner-controlled signing. Never publish a debug/internal namespace as production.
- **Do not switch IDs mid-milestone**: authentication instrumentation, app-package selectors, test runner, Play/OAuth callbacks, update compatibility and account storage all require a deliberate scoped release transition followed by new exact-SHA gate.
- Historical `.github/workflows/android-aab.yml` builds a separate legacy Tauri app on another branch; its success is NOT a native Android shipping AAB.

## G11 first-release in-scope consolidated technical matrix
| Subgate | Minimum required independently auditable evidence | Status |
|---|---|---|
| Build/source provenance | Exact git SHA, frozen parser diff, source-pinned Chaquopy wheels and correct arm64-v8a/x86_64 ELF, app and instrumentation APK SHA, no source mismatch | Partial focused only |
| JVM/model/DB | Complete original JUnit, every test PASS, migration and money precision math | 144 focused PASS on prior SHA; repeat on G11 |
| In-scope Android functional | Complete account/lifecycle/SQLCipher security; family/custody/nominee/goal/holdings/ledger, interest/AMFI, statement imports, UI/Terms/password reset, CAS review with isolation and no-owner leakage | NOT CONSOLIDATED |
| CAS cold-start + security | Isolated no-Chaquopy-before-rejection plus positive synthetic parser account-specific matching, independent original private issuer document on consented **physical ARM64** for genuine P18/P19 positive | Private positive OPEN |
| D57 release scope | `android:allowBackup="false"`, no financial archive UI/picker, scheduler, export, publication; ordinary statement import and offline account password recovery remain functional | Focused PASS; repeat after package transition |
| G14 responsive/a11y | Real encrypted-book 360dp/200%-font light+dark + long names, tablet, 48dp, measured contrast, manual TalkBack and owner screenshots/brand signoff; public artifacts synthetic only | PARTIAL |
| Android OS/API and AppSec | Real Android Keyguard admission, lifecycle locks, process deaths, low storage, API26/30/35, physical ARM64, independent AppSec findings remediated | OPEN |
| Provider/legal | Real signed Play Billing trial/entitlement, Gmail multi-OAuth consent provider, terms/privacy/data-safety, prices/network provenance, official signing trust | OWNER INPUT / OPEN |
| Production output | Native `in.growurdhan.app` signed, minified AAB with reproducible versionCode, certificate fingerprint/provenance/SBOM; no silent store publish, owner approval | OPEN |

## Out-of-scope enforcement
P03/P28/P29, E05 financial restore, E12 and archive-only M24/M25/M26/M31/M34 tests are **DEFERRED NEXT UPGRADE** under D57. Do not count as passed, do not include in base release mandatory aggregate. Retain historical source, original test evidence and security invariants. No actual CAS/PAN, owner IDs, OAuth tokens, account financial values or device keys in public CI/logs.

## Exact next decision
- If 73-case M26 gate passes: audit original evidence, record **M26/G14 automated in-scope engineering gate PASSED** with tested SHA, and separate manual visual/owner acceptance pending. Then implement/correct native production package/signing config on its own exact SHA with focused tests, before deliberately dispatching **one G11 in-scope release candidate** and completing required external approvals.
- If any of the 73 cases fail: do not dispatch G11. Diagnose the original ADB first; make a focused source repair and reverify.
- Owner or device must supply the private issuer CAS, physical-device tests, Play signing/Billing and real Gmail OAuth before market-ready status. No agent may fabricate these proofs.
