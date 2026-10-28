# GrowUrDhan Native — DEV PLAYBOOK (single working document)

Version 1.0 · written 2026-10-28 by Claude for the owner (Vikas) · target agent: ChatGPT
Repo `shahvikask/kosha`, branch `GUD-Android-Redevelopment` ONLY. Install as `docs/DEV-PLAYBOOK.md`.

**HOW TO USE: read THIS file and nothing else at session start.** Part A = rules (static; edit only on owner request). Part B = live state (the only part you update). Everything else is on-demand lookup.

## Owner approvals (only the owner may change these boxes; the coding agent NEVER ticks or unticks them)
Clauses marked `[O#]` apply only while the box is ticked. All five were ticked on 2026-10-28 by Claude (the drafting assistant) because the owner, Vikas, explicitly delegated the choice in chat ("you can tick as per my requirement understanding and ChatGPT capabilities"). The owner may untick any box at any time; an unticked clause reverts to AGENTS.md/spec behavior.
- [x] O1 Replace "re-read AGENTS/spec/STATUS/HANDOFF every prompt" (G13) and the long §17.3/§17.4 reports (G15) with A1 and A9 of this file.
- [x] O2 Move (not delete) old working docs to `docs/archive/` (A8). Add ONE DECISION-LOG row D58: "process change → DEV-PLAYBOOK v1.0; status: OWNER-DELEGATED (owner asked Claude to choose; owner has not individually reviewed each clause)". Do not word it as a personal owner approval.
- [x] O3 Path-scoped evidence reuse (A6): a green result on an older SHA stays valid when no dependency path changed. Narrow relaxation of G07's older-SHA rule; reuse must always be labeled "reused", never presented as a new run.
- [x] O4 Replace the 58 per-slice workflows with one parametrized `native-ci.yml` (A5). Prove it once on a real SHA BEFORE moving old workflows to `.github/workflows-archive/`. Leave `ci.yml`, `marketing.yml`, `android-aab.yml`, `android-apk.yml` untouched (release/web).
- [x] O5 Committing `.ci/request.json` triggers `native-ci.yml`: its `sha` names a previously committed ancestor on `GUD-Android-Redevelopment` (usually the request commit's parent), not the request commit itself. Require a request-only push, ancestor/branch verification, no other active native-ci run, and no prior green identical (sha, tier, tests); checkout and test requested `sha`. Docs-only commits must never contain a request.

---------------------------------------------------------------------
# PART A — RULES (static)

## A0 Authority
1. Requirements = Spec v1.3 `docs/GROK-FULL-NATIVE-REDEVELOPMENT-SPEC.md` (P01–P39, E01–E18, §6A). Spec wins over this file on requirements; this file wins on PROCESS.
2. Never read the spec in full (153 KB). Use A10 line ranges: `sed -n 'A,Bp'`.
3. Owner decision D57 stands: NO financial backup/export/restore/SAF/archive UI; `android:allowBackup="false"`; show permanent-data-loss warning. P03, P28, P29, E05, E12 stay DEFERRED (not "passed").
4. Observations are not owner approvals. Only the owner approves deviations.

## A1 Startup (≤5 tool calls, no narration)
1. Read this file. 2. `git fetch; git rev-parse HEAD; git status --short`. 3. If HEAD ≠ B1.checkpoint_sha: `git diff --stat B1.checkpoint_sha..HEAD` (names only; do not read diffs unless a file is in your slice). 4. List active/recent runs (latest ~10): state + SHA. 5. Print a ≤6-line receipt: branch, HEAD, checkpoint, active runs, top queue item, blockers. Then start working.
Never "audit" items B2 already marks SP/AC unless the queue item says to.

## A2 Hard rules (digest of G01–G15; wording in AGENTS.md if ever in doubt)
- G01/G02 Never commit to other branches. Frozen/read-only: `src/**`, `src-tauri/**`, `src/casparser-main/**` + protected parser scripts (byte-for-byte; never edit parser algorithms), original tests/fixtures/brand assets, SQLCipher v9 schema + frozen upgrade assets, production trust roots. New code only under `android-native/` (+ playbook/docs/workflows/CI).
- G04 Kotlin + Compose + Material 3 only; no WebView finance UI; Python only as frozen CASPARSER (Chaquopy); no cloud book of finances.
- G05 Money scale 10,000, quantity scale 100,000,000 (scaled integers/BigInteger). Append-only ledger. Never invent costs, prices, tax law, ownership, purchases. Unknown cost stays UNKNOWN.
- G07/G11 No fake green. Never delete/loosen/skip failing tests. Queued/running/cancelled = NOT VERIFIED. A result counts only for the SHA it ran on (except A6 once O3 is ticked).
- G09 No secrets/PAN/tokens/PDF bytes/keys/real statements in git, logs, artifacts, prompts.
- G10 Gmail OAuth, Play Billing, signing, real device = BLOCKED until owner supplies them. Unit tests do not prove them.
- G12 Stage labels (use exactly): SOURCE → LOCAL → JVM → EMULATOR → DEVICE → SECURITY → OWNER.
- G14 §6A "Heritage Modern Wealth" is a release gate; compiling ≠ finished. Keep approved logo and navy/gold/teal.
- Owner standing authorization (G08, 2026-10-17): you may run source-SHA-pinned CI for the milestone. Never: publish releases, touch protected branches, expose secrets, waive security/device/owner acceptance.

## A3 Token and time discipline (the main cost drivers)
- Read minimum: `grep -n` for symbols, `sed -n` ranges, `git diff --stat`. Do not open any file >150 lines whole unless editing most of it. Do not re-read a file you just wrote.
- Logs: never load full logs. Extract only failing test names + first 60 lines of each failure + counts from JUnit XML/ADB. Keep run IDs, not log text.
- Never paste file contents, diffs or logs back to the owner. Replies ≤25 lines (A9 template).
- Part B is edited with targeted replacements, not rewritten. Keep this whole file ≤300 lines; if it grows, compress B6/B3 (drop entries older than the last milestone checkpoint).
- Do not create new docs, new per-slice workflows, or per-slice status sections. History lives in git commit messages and Actions runs.
- Extend existing test classes/fixtures; do not duplicate setup code in new classes.
- No exploratory re-audits of the legacy `src/`: open a legacy file only for the exact field/behavior in the slice and only if its B2 row is not already source-mapped.

## A4 Work-slice protocol (one slice = ≥3 related requirements when possible)
1. Pick the top unblocked B4 item. Read its parity needs: spec section by A10 range + only the Kotlin files it touches.
2. Write a ≤8-line Definition of Done (DoD) in the first commit message. DoD must name: screens/fields, SQLCipher create→reopen→negative cases, test classes, G14 viewports (360dp, 200% font, light+dark) when UI.
3. Implement the WHOLE slice (production code + tests) in one pass. No partial "scaffold now, tests later".
4. Local review (no Gradle available to you unless you have it): re-read your diff once for compile errors, imports, nullability, scaling (G05), trust boundaries, and that no assertion was weakened. Fix before pushing.
5. One commit per coherent slice (message = DoD + what changed). Do not push mid-slice commits that trigger CI.
6. Run CI ONCE per A5 tier for that commit. Inspect the terminal result. Repair → new commit → new run (max 2 repair rounds per failure, then stop and report to the owner with the A5 failure table).
7. Update Part B (B1–B4), commit "checkpoint: <slice>", then continue with the next queue item without waiting for the owner unless blocked.
Batch rule: group slices so one emulator run covers several test classes (e.g. P12+P13+P14 together). Never run an emulator job to validate docs, workflows-only or comment changes.

## A5 CI policy
Tiers (pick the lowest that proves the slice):
- T0 no CI: docs, playbook, comments. Never dispatch CI for these.
- T1 JVM tests + `:app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` (minutes, cheap). Required before any T2.
- T2 targeted API35 instrumentation: only the test classes the slice touched (`-Pandroid.testInstrumentationRunnerArguments.class=<A,B,C>`).
- T3 full regression + G14 matrix + APK/AAB hashes: only at milestone gates (end of M27, M26 acceptance, release candidate) on a clean exact SHA.
Hard rules for every run: test the requested source SHA; workflow_dispatch requires requested SHA = running commit SHA, while a request-file push requires requested SHA to be an existing ancestor on this branch and the push commit to touch only `.ci/request.json`. Reject active native-ci peers and identical (SHA, tier, tests) green runs. Never claim request-commit SHA as tested SHA.

[O4] One workflow `native-ci.yml` (`workflow_dispatch`, optional `.ci/request.json` trigger under [O5]) with inputs `sha`, `tier` (T1|T2|T3), `tests` (comma list of classes), `api` (default 35). Jobs:
1. `preflight` (≈1 min): SHA/branch match; frozen-file hash gate against the existing freeze list; PRINT discovered test counts from source (e.g. grep `@Test`) and pass them forward — NEVER hard-code expected counts in YAML (stale hard-coded counts failed runs 40658684890 and similar).
2. `jvm`: compile + unit tests; upload JUnit XML.
3. `android` (only T2/T3): emulator run of `tests`; upload ADB output, JUnit XML, app/test APK SHA-256.
4. `summary`: one JSON `{sha, tier, tests, jvm_pass, android_pass, apk_sha256, run_id}`; fail if any mandatory job is skipped/failed.
Speed: enable Gradle build cache + `--configuration-cache`; `-x lint` for test tiers; cache the AVD snapshot; `concurrency: group: native-ci-${{ github.ref }}, cancel-in-progress: true`; `paths-ignore: docs/**, marketing/**, **/*.md`.
Wheels (CASPARSER Chaquopy): `native-ci.yml` verifies cached SHA256SUMS or builds four pinned upstream Android CPython 3.13 wheels locally via `.github/scripts/build-native-wheels.sh` and saves them in actions/cache keyed by build script + Android build dependencies. No cross-run artifact downloads/IDs; missing wheels fail closed.
Failure classes (act on the class, not the symptom):
| Class | Signal | Action |
|---|---|---|
| infra | artifact expired, DNS, runner | fix infra/pin; no source change |
| preflight | count/SHA/hash mismatch | fix preflight logic (derive, don't hard-code) |
| compile | Kotlin error | fix source; T1 only |
| test | assertion/instrumentation fail | read the one failing stack; fix source or test expectation ONLY with justification; never loosen |
If the agent cannot dispatch workflows: commit `.ci/request.json` [O5] containing the preceding source commit SHA, tier and tests (one file changed); monitor the run and archive old workflows only after successful native-ci proof.

## A6 Evidence reuse [O3]
Each B3 ledger row stores `tested_sha`, `dep_paths`, `run_id`. A result is reusable at HEAD iff `git diff --name-only <tested_sha> HEAD -- <dep_paths>` is empty. Default `dep_paths` for Android tests: `android-native/core/**`, `android-native/app/src/main/**`, the test file itself, `android-native/gradle/**`, `android-native/app/build.gradle.kts`. State "reused from <run_id> at <sha>" explicitly; never call it a new run. If O3 is not ticked, only same-SHA results count.

## A7 Chat-limit protocol (the reason this file exists)
- The repo is your memory. After every slice and before the chat gets long, make the checkpoint (A4.7). A new chat then needs only: this file.
- Never rely on chat history for requirements, SHAs or run results; Part B holds them.
- If you notice context running low: finish the current commit, update Part B, then stop with the A9 report. Do not start a new slice.

## A8 Old docs [O2]
Move (git mv, do not delete — they are evidence) to `docs/archive/`: `docs/implementation/STATUS.md`, `SESSION-HANDOFF.md`, `CI-RUNS.md`, `parity-register.csv`, `*-ACCEPTANCE.md`, `HOLDING-*.md`, `HOME-ROWS.md`. Keep in place: spec, `DECISION-LOG.md` (append-only, short rows), `BASE-RELEASE-SCOPE.md`, `AGENTS.md` (add one line at top: "Process: follow docs/DEV-PLAYBOOK.md"). Read archived files only via `grep` for a specific run id/SHA/term, never whole.

## A9 Session report (≤25 lines, replaces §17.4 once [O1] is ticked)
```
SLICE: <ids> · STAGE: <SOURCE|LOCAL|JVM|EMULATOR|...>
COMMITS: <short shas>   RUNS: <id → pass/fail/running @sha>
VERIFIED: <what is proven, counts>   NOT VERIFIED: <what is not>
FAILED/REPAIRED: <class + one line>
NEXT: <top queue item>   BLOCKED ON OWNER: <list or none>
```
New-chat prompt the owner pastes (replaces §17.5/17.7):
`Read docs/DEV-PLAYBOOK.md only. Follow A1, then continue the top item in B4. Update Part B at each checkpoint. Do not re-read other docs.`

## A10 Spec lookup (line ranges in v1.3 file; run `grep -n '^#' ` if shifted)
§2 architecture 101–179 · §3 screens 180–201 · §4.1 categories 204–220 · §4.2 HoldingDetails 221–228 · §4.3 events 229–238 · §5 P-register 239–288 · §6 E-register 289–317 · §6A design 318–555 (6A.4 tokens 376, 6A.6 Home 419, 6A.7 screens 445, 6A.10 G14 matrix 520) · §7 migration 556–588 · §8 milestones 589–611 · §10 CI 649–751 · §12 tests 804–844 · §13 integrations 845–860 · §14 DoD 901–913.

---------------------------------------------------------------------
# PART B — LIVE STATE (edit only this part)
Seed written by Claude on 2026-10-28 from repo docs. FIRST SESSION: verify B1–B3 against GitHub Actions and fix any mismatch before relying on them.

## B1 Header
branch GUD-Android-Redevelopment · HEAD checked after P15 T2 `45d41b41f6642f6ca6c0821b0ee843a020b04f88` · checkpoint_sha `45d41b41f6642f6ca6c0821b0ee843a020b04f88` · accepted requirements 0/36 in-scope · formally accepted milestones: none.
State codes: ND not started · SP source partial · JV JVM green · EM emulator green · AC accepted · DF deferred (D57).

## B2 Requirement matrix (split a row when members diverge)
| IDs | Milestone | State | Notes / next gap |
|---|---|---|---|
| P01–P02 | M25 | SP | offline recovery code, lock/rotation partly done; real-device/AppSec open |
| P03, P28, P29, E05, E12 | — | DF | D57; do not build or test as base-release features |
| P04–P05 | M24 | SP/EM | SQLCipher schema + append-only ledger tests green at older SHAs; full field parity/historic migration open |
| P06–P08, E03, E04 | M26 | SP | owner-scoped CRUD partial; one focused G14 pass at df12285f; acceptance open |
| P09–P10 | M27 | SP/EM | Eight-category encrypted restart/negative and 36-key specialist mapping T2 40682410679 API35 9/9; formal acceptance open |
| P11 | M24/M27 | SP/EM | specialist metadata, PPF/SSY non-posting, property proof tests green at 3746d928; genuine issuer/valuer roots need owner |
| P12 | M27 | SP/EM | Owner-scoped detail links, edit, goal, archive/reopen and dated-price negative restart checks; 40687321312 API35; G14 matrix/OWNER acceptance open |
| P13–P14 | M27 | SP/EM | Source-referenced snapshot read-only history/reconciliation preview and correction-picker restriction; SQLCipher source guard and correction tests 40687321312; full source reconciliation acceptance open |
| P15 | M27 | SP/EM | Owner-scoped filtered/sorted bounded lazy holdings with stable IDs and history, account links; 360dp/200%-font dark API35 3/3 at c3f534b4; full G14/OWNER acceptance open |
| P16, P21–P25 | M29 | SP | goldens/engines partial (FIFO, XIRR, EMI, interest); look-through, FY tax rules, home analytics open |
| P17–P20, E06 | M28 | SP | staging/idempotency partial; genuine CAS PDF on ARM64 unproven |
| P26–P27, E10 | M30 | SP | AMFI NAV only; NSE/BSE, provenance UX open |
| P30–P33, E01, E02, E08, E11, E13 | M31 | SP/ND | dossier, diagnostics, brand partial; Hindi, TalkBack, 200% font, full G14 open |
| P34 | M32 | SP | idempotency/cursor guard only; real OAuth needs owner |
| P35–P39, E07, E09, E15–E18 | M32–M34 | ND | M32 Gmail, M33 Billing/AAB, M34 final regression/AppSec |
| E14 | all | SP | modular tests partial |

## B3 Evidence ledger (copied from STATUS.md; verify before relying)
| run_id | sha | proves | dep_paths |
|---|---|---|---|
| 40690449183 | c3f534b43e9340bacd9b18aa3adb7677e10e7397 | P15 T2 SUCCESS: preflight/JVM/APKs/API35 3/3 NativeM27HoldingFilterInstrumentedTest; original ADB exit -1; wheels SHA256 cache verified; summary 12818070309, APK+ADB 12817786711, JVM 12816244319; request 45d41b41 | NativeBooksScreen.kt, NativeM27HoldingFilterInstrumentedTest.kt, Android build/wheels |\n| 40689794038 | c3f534b43e9340bacd9b18aa3adb7677e10e7397 | P15 T1 SUCCESS: JVM/core tests, app and AndroidTest Kotlin compile, summary; request 3bced25b | NativeBooksScreen.kt, NativeM27HoldingFilterInstrumentedTest.kt |\n| 40687321312 | 19ad328874f03cf37c7ad1981815b279d7e1eb4f | T2 SUCCESS: preflight/JVM/APK/API35 12/12 touched P12/P13/P14 classes; INSTRUMENTATION_CODE -1; cache wheels SHA256 verified; artifacts summary 12816655631, APK/ADB 12815664540, JVM 12815263485; request commit 7a31d37d | NativeBooksRepository.kt, NativeBooksScreen.kt, NativeHoldingAuditHistory.kt, four changed androidTests, wheel dependencies |\n| 40686757629 | 19ad328874f03cf37c7ad1981815b279d7e1eb4f | T1 SUCCESS: JVM tests and app/androidTest Kotlin compilation; repaired Kotlin helper callback parameter ordering | same P12/P13/P14 dep_paths |\n| 40686081793 | 9d5f659dd7b591d506ff0de83efb7176204a0ddd | T1 FAILED: 3 Kotlin errors in NativeM27EventFormInstrumentedTest trailing-lambda calls after added optional argument; repaired in 19ad3288; no test assertion weakened | NativeM27EventFormInstrumentedTest.kt |\n| 40658862842 | dbb561dd | JVM+APK compile; API35 8/8 (specialist/P10-P11 set) | core/**, app/src/main/** |
| 40659987550 | f2d301de | FAILED :app:compileDebugAndroidTestKotlin (assertDoesNotExist import / NativeAssetTaxonomy); no emulator result | core/**, app/src/main/**, specialist form test |\n| 40682410679 | 3f1a808f986c6cddfc4ef8063546e7fb3580d488 | P09/P10 T2 SUCCESS: preflight/JVM/APKs/API35 9/9 specialist metadata tests; INSTRUMENTATION_CODE -1; wheel cache audit WHEELS_CACHE_VERIFIED=4; artifacts summary 12813967375, emulator 12813149481, JVM 12813118593; request 639cdcf0 | NativeHoldingSpecialist.kt, NativeBooksRepository.kt, NativeM27SpecialistMetadataInstrumentedTest.kt, native-ci.yml |\n| 40681907139 | 3f1a808f986c6cddfc4ef8063546e7fb3580d488 | P09/P10 T1 SUCCESS: preflight, JVM compile/tests, summary; request e69d019f | NativeHoldingSpecialist.kt, specialist metadata test |\n| 40679023182 | d9fa7ae3b940945ea9701bf9579b43be26f8ce1a | P09/P10 T2: preflight+JVM PASS; Android 9 executed, 8 PASS / 1 FAIL (invalid 2025-02-30 leaked DateTimeParseException instead of IllegalArgumentException); source repair required; summary FAILED | specialist metadata, NativeHoldingSpecialist |\n| 40676871448 | fbe75e44a32d55b0ab2a39f75b485ce96c6d40ac | T2 SUCCESS all jobs, API35 16/16 specialist metadata (7) + Compose (9), INSTRUMENTATION_CODE -1; JVM 12810887959, APK+ADB 12811088202, summary 12810734932; wheel source build/cache SHA256SUMS verified; request 89b425be | native-ci.yml, wheels script, specialist tests |\n| 40675114759 | e2f98b488c4b873047e4342d4373317ce41ee4d3 | T2 source wheels cache+JVM+APK+API35 emulator; ORIGINAL ADB OK (16 tests), exit -1; WORKFLOW FAILED on overescaped success regex, not app; repair 2 pending. Request fac0c6b3 | native-ci.yml, wheels cache, specialist tests |\n| 40672732934 | ba288dbc9ceb1dd899d774ef11b50cff746d3956 | T2: pinned wheels built and SHA256SUMS verified; JVM PASS, APK compile/hash PASS, API35 boot PASS; emulator test script FAILED before execution (/bin/sh rejects pipefail), no Android tests; workflow repair 1 pending, artifact 12809099272 | native-ci.yml, wheels script |\n| 40666583685 | 5d524652ab2e48c8569479151f4b6b51de024b67 | T1 SUCCESS: preflight, JVM tests/Kotlin compile and summary; Android correctly skipped; request commit b91dd296; native-ci replacement proven at T1, T2 still unverified | native-ci.yml, core/**, app/src/main/** |\n| 40665928598 | 09c00047988b9141ad6141122eb5d0d400b977a7 | T1 preflight + JVM SUCCESS; summary FAILED due malformed shell quote; repair round 1 pending. Request commit 67f8a10a; no EMULATOR | native-ci.yml |\n| 40661694569 | 6965c40cc0b92d88647c59f9e5485c025a2c342b | SUCCESS: JVM/APK build and API35 16/16 (7 NativeM27SpecialistMetadataInstrumentedTest + 9 NativeM27SpecialistFormInstrumentedTest); prior 15 expectation was stale (6+9), new source had 7 metadata cases. Artifact 12804837207; reused only if A6 dep paths unchanged | core/**, app/src/main/**, specialist form test, gradle/** |
| 40652687085 / 40652687166 | 3746d928 | P11 JVM compile; API35 10/10 migration/rollback | core/**, app/src/main/** |
| 40499402394 | 511aa0ea | 169 JVM + 3 API35 (P13/P14 reconciliation) | core/**, app/src/main/** |
| 40441608989 | df12285f | M26+G14 API35 PASS, 144 JVM / 10 Android | core/**, app/src/main/** |
Expired/unusable: wheel artifact 12795467059 (run 40640334920) — replace per A5 wheels rule.

## B4 Work queue (top = next; re-prioritize only by owner)
1. DONE. Prior f2d301de compile failure superseded by 40661694569 API35 16/16; unified native-ci T1 40666583685 and T2 40676871448 SUCCESS. No duplicate re-runs required.
2. P09/P10: one-source-batch all-eight-category encrypted create→restart→negative tests and exact 36-key HoldingDetails mapping implemented; invalid date negative failure repaired. T1 40681907139 and T2 40682410679 API35 9/9 SUCCESS at 3f1a808f. State EM (tested), NOT AC; no owner/device/security signoff.
3. P12 source + tests done in P12/P13/P14 batch at 19ad3288: holding links/detail, edit/archive/reopen, dated valuation restart/negative; T1 40686757629 and T2 40687321312 API35 12/12 SUCCESS. NOT AC (G14/OWNER open).
4. P13/P14 same batch: source-only SNAPSHOT_ADJUSTMENT visible read-only in history; excluded from correction picker; encrypted provenance checks; T2 40687321312 SUCCESS. NOT AC (full source reconciliation remains open).
5. P15 batch complete at source/JVM/EMULATOR: stable-key bounded holdings LazyColumn, member/account/category/archive search/sort, 360dp/200%-font dark virtualization; T1 40689794038 and API35 T2 40690449183 3/3 SUCCESS at c3f534b4. NOT AC; G14/OWNER remains open.
6. G14 matrix for M26–M27 screens: 360dp, 200% font, light/dark, TalkBack labels; produce screenshots for owner review. [T3]
7. M26 acceptance: P06–P08, E03/E04 negative cross-owner matrix. [T2→T3]
8. M25 close-out: P01/P02 negative/interruption cases; real-device = BLOCKED (owner).
9. M29 goldens/engines: P16, P21–P25 (TS-vs-Kotlin golden vectors; no tax rate without verified source).
10. M28 synthetic CAS contract tests; genuine ARM64 PDF = BLOCKED (owner sample/device).
11. M30 NSE/BSE adapters + provenance/stale UX; mock HTTP tests.
12. M31 settings/diagnostics/accessibility/Hindi onboarding/dossier export warnings.
13. M32–M34 — BLOCKED until owner supplies Google OAuth, Play Billing/signing, device, AppSec reviewer.
Process task P0: completed. O5 ancestor request T1 SUCCESS 40666583685 and T2 SUCCESS 40676871448 (after two workflow-only repairs); 54 old slice workflows archived unchanged. The four release/web workflows and native-ci.yml remain.

## B5 Blockers needing the owner
Gmail OAuth project/credentials; Play Billing products/test accounts; upload/signing keys; physical ARM64 phone + genuine CAS statements; production property/PPF/SSY/valuer trust roots; independent AppSec reviewer; G14 screenshot approval. (O1–O5 are already ticked via owner delegation; workflow triggering uses `.ci/request.json`.)

## B6 Do-not-redo list
- Do not re-audit rows marked SP in B2; do not re-run green jobs for unchanged dependency paths (A6 once O3 ticked).
- Do not re-derive D57 scope, frozen hashes, or branding.
- Do not rewrite passing tests; do not add per-slice workflows or docs.
- Known past failures to avoid: hard-coded test counts in workflow YAML; pinning an expired artifact id; duplicate dispatch while a run is active; treating lightweight/docs CI as Android proof.

## B7 Open risks
Evidence is mostly tied to older SHAs; no local Gradle for the agent; legacy workflows archived after unified T1/T2 proof; P12–P14 focused T2 green but owner/security/G14 acceptance open; owner-only inputs block M32–M34; 0 formal acceptances after many sessions — the next sessions should convert PARTIAL → ACCEPTED slice by slice rather than adding breadth.