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
branch GUD-Android-Redevelopment · HEAD checked after M31 P31/P32/E11 settings T2 request `d08461f42eb8e21b7e8f42d15ddf22f1e344c82a` · checkpoint_sha `d08461f42eb8e21b7e8f42d15ddf22f1e344c82a` · accepted requirements 0/36 in-scope · formally accepted milestones: none.
State codes: ND not started · SP source partial · JV JVM green · EM emulator green · AC accepted · DF deferred (D57).

## B2 Requirement matrix (split a row when members diverge)
| IDs | Milestone | State | Notes / next gap |
|---|---|---|---|
| P01–P02 | M25 | SP/EM | Enrollment admission recheck before vault creation; PAN/DOB/Terms negative no-file, duplicate registration no-overwrite, Terms re-consent, one-time recovery replacement, restart/clock/lock guards; targeted API35 15/15 at 6f5cb3c5. Real-device/AppSec/OWNER acceptance open |
| P03, P28, P29, E05, E12 | — | DF | D57; do not build or test as base-release features |
| P04–P05 | M24 | SP/EM | SQLCipher schema + append-only ledger tests green at older SHAs; full field parity/historic migration open |
| P06–P08, E03, E04 | M26 | SP/EM | Cross-owner custody, goal, HUF/minor/guardian and primary protections encrypted rollback→restart; targeted API35 3/3 at b38de8be; complete lens/T3/device/security/OWNER acceptance open |
| P09–P10 | M27 | SP/EM | Eight-category encrypted restart/negative and 36-key specialist mapping T2 40682410679 API35 9/9; formal acceptance open |
| P11 | M24/M27 | SP/EM | specialist metadata, PPF/SSY non-posting, property proof tests green at 3746d928; genuine issuer/valuer roots need owner |
| P12 | M27 | SP/EM | Owner-scoped detail links, edit, goal, archive/reopen and dated-price negative restart checks; 40687321312 API35; G14 matrix/OWNER acceptance open |
| P13–P14 | M27 | SP/EM | Source-referenced snapshot read-only history/reconciliation preview and correction-picker restriction; SQLCipher source guard and correction tests 40687321312; full source reconciliation acceptance open |
| P15 | M27 | SP/EM | Owner-scoped filtered/sorted bounded lazy holdings with stable IDs and history, account links; 360dp/200%-font dark API35 3/3 at c3f534b4; full G14/OWNER acceptance open |
| P16, P21, P24 | M29 | SP/JV | FIFO, reviewed FMV, source returns and strict owner-scoped exact hybrid exposure splits; T1 40748714360 and 40757210296 PASS; P24 persistence/UI and complete financial/tax acceptance open |
| P22–P23, P25 | M29 | SP/EM | Interest coupon and EMI due-date/one-post goldens; owner-scoped Home health/unknown-cost/stale-price indicators and G14 tests; T1 40750235052 SUCCESS, T2 40751028900 API35 6/6; published rates, full analytics, owner acceptance open |
| P17–P20, E06 | M28 | SP/EM | Non-demat CAS preview exact ISIN/folio identity, duplicate rejection and multi-folio negatives at e21966a7 API35 15/15 (40759156459); SQLCipher staging/idempotency prior partial; genuine CAS PDF, full positive approval and ARM64 source proof unverified |
| P26–P27, E10 | M30 | SP/EM | Frozen-aligned NSE ZIP/BSE CSV allowlisted dated-close adapters, manual owner-only refresh and per-holding unknown/stale/source disclosure now mounted on real Holdings; prior SQLCipher restart/manual precedence T2 40790101250 17/17 and new integrated source a1fedcd6 T1 40808560210, API35 T2 40809097105 14/14 PASS. Actual official network/device, full G14 and OWNER sign-off open; NOT AC |
| P30 | M31 | SP/EM | In-memory masked dossier now wired into real Family owner lens with separate warning acknowledgement and reveal, consent revocation on lens/snapshot change, fail-closed custody conflicts, no plaintext output. Prior owner scope/label-redaction T2 40804666855 7/7; new source 2d006e52 T1 40810969200 and API35 T2 40811450454 12/12 PASS (real SQLCipher screen at 360dp/200% light+dark). Physical device, real TalkBack and OWNER signoff open; NOT AC |
| P31 | M31 | SP/EM | System/Light/Dark now authentically changes GudTheme, stored in SQLCipher and reset at lock. SGB recorded market vs RBI-required fail-closed presentation; IBJA owner-review flag never fabricates price. Compiled source 4f13f465 and targeted API35 40841339082 7/7 PASS. Real verified RBI/IBJA rate/input, account controls, full theme/UI matrix and OWNER signoff open; NOT AC |
| P32, E11 | M31 | SP/EM | Encrypted opt-in/revoke persists restart; redacted owner-invoked health text preview, explicit clipboard copy and clear, no retained event logs or network. Prior API35 40802606999 11/11 reused only where deps unchanged; new 40841339082 API35 7/7 PASS including settings negative/locked restart. Clipboard remains external until owner clears; wider retention/delete/export UX/security acceptance open, no financial export (D57) |
| P33, E01, E02, E08, E13 | M31 | SP/EM | Hindi on-device-loss warning and 360dp/200%-font light/dark onboarding tests; theme/settings real-screen test now API35 40841339082 7/7. Real TalkBack, full device/contrast G14 matrix and OWNER visual signoff open |
| P34 | M32 | SP | idempotency/cursor guard only; real OAuth needs owner |
| P35–P39, E07, E09, E15–E18 | M32–M34 | ND | M32 Gmail, M33 Billing/AAB, M34 final regression/AppSec |
| E14 | all | SP | modular tests partial |

## B3 Evidence ledger (copied from STATUS.md; verify before relying)
| run_id | sha | proves | dep_paths |
|---|---|---|---|
| 40841339082 | 4f13f465301d28b0723482a15896ad464db88bce | M31 P31/P32/E11 T2 SUCCESS: preflight/JVM/APKs/API35 7/7 NativePrivateDiagnosticsInstrumentedTest + NativeM26RealBooksAccessibilityInstrumentedTest; original ADB `OK (7 tests)`, `INSTRUMENTATION_CODE: -1`; four cached wheels SHA256 verified. App APK `b74142e13aa01eab4b4fc0f87e2f4acb95075b749307c4113f82a4dcd13e7e99`, androidTest APK `80c75ee7b18bddbbf79b897ed8b2f40085c22b2d01330c087bda101750685907`; artifacts emulator 12883232734, summary 12883143476, JVM 12882484086; request d08461f4 | NativeUserSettingsRepository.kt, NativeUserSettingsCard.kt, MainActivity.kt, NativeBooksScreen.kt, NativePrivateDiagnosticsInstrumentedTest.kt, NativeM26RealBooksAccessibilityInstrumentedTest.kt, app/main, gradle |
| 40840805803 | 4f13f465301d28b0723482a15896ad464db88bce | M31 repaired T1 SUCCESS: preflight/JVM model tests + app/androidTest Kotlin compile; request a8e7f070; Android skipped by design, artifacts JVM 12882642761 and summary 12882613299 | same M31 settings deps |
| 40840075861 | 712c91e6e19207e04f0a94c91ad3a22729a3a7d1 | M31 initial T1 FAILED on androidTest compile: two older owner-lens tests use positional NativeBooksScreen arguments, shifted by newly required settings. Repaired by backward-compatible optional tail setting in 4f13f465; no tests weakened. | NativeBooksScreen.kt and legacy test callers |---|
| 40811450454 | 2d006e529fd60b9dde2d08a0aea875b16299fadd | M31 P30 owner-lens real Family preview T2 SUCCESS: preflight/JVM/APKs/API35 12/12 NativeEmergencyDossierInstrumentedTest and NativeM26RealBooksAccessibilityInstrumentedTest; original ADB `OK (12 tests)`, `INSTRUMENTATION_CODE: -1`; 4 cached wheels verified. app APK SHA256 `237811e0911818ac55b50627708723231f03f4e59835b062b29c59e533fbdd3c`, androidTest APK `08d19ec3a6def6a4c1b919e048ef2072020939fee56c5d4f3acf6ce4103696af`; artifacts emulator 12870608433, summary 12870459229, JVM 12870278885; request d6d7cf8d | NativeFinancialReviewCards.kt, NativeBooksScreen.kt, NativeEmergencyDossierInstrumentedTest.kt, NativeM26RealBooksAccessibilityInstrumentedTest.kt, app/src/main/**, core/**, gradle/** |
| 40810969200 | 2d006e529fd60b9dde2d08a0aea875b16299fadd | M31 P30 in-app dossier T1 SUCCESS: preflight/JVM core tests and app/androidTest Kotlin compilation, Android skipped as designed; request 3a2955b3 | same P30 app main and two touched androidTest paths |
| 40809097105 | a1fedcd6113efd73b6b9faf595179548cbd5229f | M30 mounted Holdings T2 SUCCESS: preflight/JVM/APKs/API35 14/14 NativeM26RealBooksAccessibility, NativeM27HoldingFilter, NativeM27HoldingArchiveJourney; original ADB `OK (14 tests)`, `INSTRUMENTATION_CODE: -1`; wheels cache 4, app APK SHA256 `42021443dcfde99a6006997a032c8a18e6594019fa144de99535762f1ad66ad2`, androidTest APK `026de2dc440c66e9fceda8c18c82e878240f52e30a362eab919bcdd2101ca79e`; artifacts summary 12869897780, emulator 12869847159, JVM 12869662410; request 2bd1a527 | NativeBooksScreen.kt, NativeExchangePriceCard.kt, NativeExchangePriceDisclosure.kt, NativeM26RealBooksAccessibilityInstrumentedTest.kt, NativeM27HoldingFilterInstrumentedTest.kt, NativeM27HoldingArchiveJourneyInstrumentedTest.kt, android-native/app/src/main/**, core/**, gradle/** |
| 40808560210 | a1fedcd6113efd73b6b9faf595179548cbd5229f | M30 mounted Holdings T1 SUCCESS: preflight/JVM tests/app+androidTest Kotlin compile; Android skipped by design; artifacts summary 12869208259, JVM 12868809976; request a17a0c6f | same M30 source and test paths |
| 40804666855 | 5d826aa9f5bd23ec9df161b4d2970f163e1880bc | M31 P30 T2 SUCCESS: preflight/JVM/APK/API35 7/7 NativeEmergencyDossierInstrumentedTest; ADB -1, SHA256 4 cached wheels; artifact emulator 12867339711, summary 12868111798, JVM 12868290481; request 5040b1e2; newer UI changes are NOT covered by this exact-SHA run | NativeEmergencyDossier.kt, NativeEmergencyDossierInstrumentedTest.kt, app Kotlin/gradle |
| 40804139262 | 5d826aa9f5bd23ec9df161b4d2970f163e1880bc | M31 P30 T1 SUCCESS: preflight, JVM tests, Android app/androidTest Kotlin compilation and summary; request b50e277b | NativeEmergencyDossier.kt and its existing androidTest |
| 40802606999 | 2930f7a1f932b421a5e73fc7ecff145a33aade48 | M31 T2 SUCCESS: preflight/JVM/APK/API35 11/11 NativePrivateDiagnostics, NativeEmergencyDossier, NativeM26RealBooksAccessibility; ADB -1; WHEELS_CACHE_VERIFIED=4; summary artifact 12867202501, emulator 12866958109, JVM 12866414322; request 33ff093f | NativePrivateDiagnosticsRepository.kt, NativeEmergencyDossier.kt, NativeOnboardingLanguageNotice.kt, NativeAuthenticationScreens.kt, three touched androidTests, app/gradle | 
| 40792211047 | 2930f7a1f932b421a5e73fc7ecff145a33aade48 | M31 T1 SUCCESS preflight, JVM :core:model:test, app/androidTest compile, summary; request 7da16f99 | M31 P30/P32/P33 initial native sources and tests | 
| 40790101250 | e9b42535359e17d909599d572cb24609dbab04ed | M30 corrected-source T2 SUCCESS: preflight/JVM/APK/API35 17/17 across three touched classes, ADB exit -1, four wheels SHA256 verified; emulator artifact 12861423117, summary 12862021628, JVM 12861123039; request 9f92b96e | NativeExchangeEodCatalog/Downloader/Repository, NativeDatedPriceRecorder, NativeBooksRepository, NativeAmfiNavCatalogInstrumentedTest, NativeM27HoldingFilterInstrumentedTest, NativeM27HoldingArchiveJourneyInstrumentedTest, app/gradle | 
| 40789629666 | e9b42535359e17d909599d572cb24609dbab04ed | M30 corrected-source T1 SUCCESS: JVM tests + app/androidTest Kotlin compile, summary; request f7d90291 | same M30 source and test deps |
| 40788268661 | 0045cc2ef390d4e2c99861d48eb82d1015bcf63f | M30 initial synthetic T2 SUCCESS: API35 12/12, ADB -1, but source mismatched frozen BSE plain CSV (original expected ZIP) and omitted exchange security-type filter; superseded by e9b42535 and 40790101250 | M30 initial nine-file slice |
| 40787777683 | 0045cc2ef390d4e2c99861d48eb82d1015bcf63f | M30 initial T1 SUCCESS: JVM+Android Kotlin compilation; no source-format acceptance | initial M30 source and tests |
| 40759156459 | e21966a7eaca6c6c01b26be5eac435ad3014952e | M28 targeted T2 SUCCESS: preflight/JVM/APK/API35 15/15 NativeCasOutputReviewGateInstrumentedTest, ADB exit -1, emulator 12848301613, summary 12847563458, JVM 12846113994; request eb34356a; no real CAS | NativeCasOutputReviewGate.kt, NativeCasOutputReviewGateInstrumentedTest.kt, app/main and gradle/wheels | 
| 40758465483 | e21966a7eaca6c6c01b26be5eac435ad3014952e | M28 T1 SUCCESS: preflight/JVM+Android Kotlin compilation/summary; request b1c24953 | NativeCasOutputReviewGate.kt, NativeCasOutputReviewGateInstrumentedTest.kt | 
| 40757210296 | 7acd13f3d7fa10d6fb258bed53cac3fda78566e5 | M29 P24 lookthrough T1 SUCCESS: source apportion exact 10000bp, largest-slice remainder, unknown price exclusion, estimated value label, owner isolation and invalid split negatives; :core:model:test, app/androidTest Kotlin PASS, 4 cached wheels SHA256; JVM artifact 12846747118, summary 12847840029; request 18b08dfc; Android skipped expected T1 | NativeExposureAllocation.kt, NativePortfolioMathTest.kt, core/model, app Kotlin deps | 
| 40751028900 | 5a9a5f4ee9e6aebe22b7affc5459027971342f53 | M29 P22/P23/P25 T2 SUCCESS at source SHA: preflight/JVM/APK/API35 6/6, ADB exit -1, wheels cache SHA256 4; artifacts emulator 12845106225, summary 12844887246, JVM 12844580985; request f7f0916e | NativeBooksHomeHealth.kt, NativeBooksScreen.kt, NativeM26RealBooksAccessibilityInstrumentedTest.kt, NativeM27HoldingFilterInstrumentedTest.kt, app/gradle | 
| 40750235052 | 5a9a5f4ee9e6aebe22b7affc5459027971342f53 | M29 P22/P23/P25 T1 SUCCESS preflight/JVM/app/androidTest compile and summary; artifacts JVM 12842764389, summary 12843588215; request f2bec88a | NativeEmiPlannerTest.kt, NativeInterestPayoutPlannerTest.kt, Home health and related Kotlin | 
| 40748714360 | 34bbf6330b4d6f531202875b67dce46343ad316d | M29 T1 SUCCESS: five source-golden JVM cases added, app Kotlin compilation PASS, SHA256 wheel check 4; JVM artifact 12841324668 and summary 12843216433; request 82d12eab | NativeTaxLotTrace, NativeMoneyWeightedReturn, model JVM tests |
| 40737480182 | 6f5cb3c5f323742d443e9e4ef20ab881cb68b536 | M25 P01/P02 T2 SUCCESS: preflight/JVM/APK/API35 15/15 NativeAccountRepositoryInstrumentedTest; original INSTRUMENTATION_CODE -1; 4 wheels SHA256 verified; artifacts summary 12839136251, APK+ADB 12838946565, JVM 12838426825; request d2829701 | NativeAccountRepository.kt, NativeAccountRepositoryInstrumentedTest.kt, native-ci.yml, app/gradle/wheels |
| 40736723667 | 6f5cb3c5f323742d443e9e4ef20ab881cb68b536 | M25 P01/P02 T1 SUCCESS: preflight, JVM/core tests and app/androidTest Kotlin compilation, summary; artifacts JVM 12837596822, summary 12837128887; request 8d5ece1f | NativeAccountRepository.kt, NativeAccountRepositoryInstrumentedTest.kt |
| 40705791566 | b38de8beab324163bce73e01edbd6f6b7f897d13 | M26 targeted T2 SUCCESS: API35 3/3 owner-lens mutation tests, preflight/JVM/APK/summary green; wheel cache SHA256 verified (4); artifacts summary 12824278855, emulator 12823989615, JVM 12823742181; request ca48b861 | NativeM26OwnerLensMutationInstrumentedTest.kt, NativeBooksRepository.kt, NativeBooksScreen.kt, app/gradle, wheels |
| 40705038884 | b38de8beab324163bce73e01edbd6f6b7f897d13 | M26 repair T1 SUCCESS JVM + Android Kotlin compile; original test locator corrected to m27-review-event without loosening 3 assertions | NativeM26OwnerLensMutationInstrumentedTest.kt |
| 40702769850 | 752ee75689c0efafd5d42c45400c918c019b3985 | M26 targeted T2 FAILED: 2/3 new negatives PASS; pre-existing UI test used removed 'Append money event' label (failed to find action); repaired by tag b38de8be | NativeM26OwnerLensMutationInstrumentedTest.kt |
| 40702095652 | 752ee75689c0efafd5d42c45400c918c019b3985 | M26 original T1 SUCCESS JVM + Android Kotlin compilation, summary | NativeM26OwnerLensMutationInstrumentedTest.kt |
| 40696534614 | 2445eb761f0ded107717ac994c8c8024d8434939 | G14 targeted T2 SUCCESS: 10/10 API35 synthetic Compose tests, 8 PNGs collected and SHA256SUMS OK; preflight/JVM/APK/summary green; emulator artifact 12820965616, summary 12820002776, JVM 12819089518; request 9ccccf17 | NativeM26VisualEvidenceInstrumentedTest.kt, native-ci.yml, GudTheme/Brand tokens, native UI |
| 40694153945 | 4e4e0ee2cfdca4910643f8b6a496df90e9f2b7a9 | G14 T1 SUCCESS: JVM tests and app/androidTest Kotlin compilation; no emulator | G14 visual test, core/designsystem, native UI |
| 40694922710 | 4e4e0ee2cfdca4910643f8b6a496df90e9f2b7a9 | G14 T2 FAILED workflow-only: original ADB OK 10 tests but shell unexpected EOF (expecting fi); fixed 2445eb76 without loosening assertions | native-ci.yml |
| 40690449183 | c3f534b43e9340bacd9b18aa3adb7677e10e7397 | P15 T2 SUCCESS: preflight/JVM/APKs/API35 3/3 NativeM27HoldingFilterInstrumentedTest; original ADB exit -1; wheels SHA256 cache verified; summary 12818070309, APK+ADB 12817786711, JVM 12816244319; request 45d41b41 | NativeBooksScreen.kt, NativeM27HoldingFilterInstrumentedTest.kt, Android build/wheels |
| 40689794038 | c3f534b43e9340bacd9b18aa3adb7677e10e7397 | P15 T1 SUCCESS: JVM/core tests, app and AndroidTest Kotlin compile, summary; request 3bced25b | NativeBooksScreen.kt, NativeM27HoldingFilterInstrumentedTest.kt |
| 40687321312 | 19ad328874f03cf37c7ad1981815b279d7e1eb4f | T2 SUCCESS: preflight/JVM/APK/API35 12/12 touched P12/P13/P14 classes; INSTRUMENTATION_CODE -1; cache wheels SHA256 verified; artifacts summary 12816655631, APK/ADB 12815664540, JVM 12815263485; request commit 7a31d37d | NativeBooksRepository.kt, NativeBooksScreen.kt, NativeHoldingAuditHistory.kt, four changed androidTests, wheel dependencies |
| 40686757629 | 19ad328874f03cf37c7ad1981815b279d7e1eb4f | T1 SUCCESS: JVM tests and app/androidTest Kotlin compilation; repaired Kotlin helper callback parameter ordering | same P12/P13/P14 dep_paths |
| 40686081793 | 9d5f659dd7b591d506ff0de83efb7176204a0ddd | T1 FAILED: 3 Kotlin errors in NativeM27EventFormInstrumentedTest trailing-lambda calls after added optional argument; repaired in 19ad3288; no test assertion weakened | NativeM27EventFormInstrumentedTest.kt |
| 40658862842 | dbb561dd | JVM+APK compile; API35 8/8 (specialist/P10-P11 set) | core/**, app/src/main/** |
| 40659987550 | f2d301de | FAILED :app:compileDebugAndroidTestKotlin (assertDoesNotExist import / NativeAssetTaxonomy); no emulator result | core/**, app/src/main/**, specialist form test |
| 40682410679 | 3f1a808f986c6cddfc4ef8063546e7fb3580d488 | P09/P10 T2 SUCCESS: preflight/JVM/APKs/API35 9/9 specialist metadata tests; INSTRUMENTATION_CODE -1; wheel cache audit WHEELS_CACHE_VERIFIED=4; artifacts summary 12813967375, emulator 12813149481, JVM 12813118593; request 639cdcf0 | NativeHoldingSpecialist.kt, NativeBooksRepository.kt, NativeM27SpecialistMetadataInstrumentedTest.kt, native-ci.yml |
| 40681907139 | 3f1a808f986c6cddfc4ef8063546e7fb3580d488 | P09/P10 T1 SUCCESS: preflight, JVM compile/tests, summary; request e69d019f | NativeHoldingSpecialist.kt, specialist metadata test |
| 40679023182 | d9fa7ae3b940945ea9701bf9579b43be26f8ce1a | P09/P10 T2: preflight+JVM PASS; Android 9 executed, 8 PASS / 1 FAIL (invalid 2025-02-30 leaked DateTimeParseException instead of IllegalArgumentException); source repair required; summary FAILED | specialist metadata, NativeHoldingSpecialist |
| 40676871448 | fbe75e44a32d55b0ab2a39f75b485ce96c6d40ac | T2 SUCCESS all jobs, API35 16/16 specialist metadata (7) + Compose (9), INSTRUMENTATION_CODE -1; JVM 12810887959, APK+ADB 12811088202, summary 12810734932; wheel source build/cache SHA256SUMS verified; request 89b425be | native-ci.yml, wheels script, specialist tests |
| 40675114759 | e2f98b488c4b873047e4342d4373317ce41ee4d3 | T2 source wheels cache+JVM+APK+API35 emulator; ORIGINAL ADB OK (16 tests), exit -1; WORKFLOW FAILED on overescaped success regex, not app; repair 2 pending. Request fac0c6b3 | native-ci.yml, wheels cache, specialist tests |
| 40672732934 | ba288dbc9ceb1dd899d774ef11b50cff746d3956 | T2: pinned wheels built and SHA256SUMS verified; JVM PASS, APK compile/hash PASS, API35 boot PASS; emulator test script FAILED before execution (/bin/sh rejects pipefail), no Android tests; workflow repair 1 pending, artifact 12809099272 | native-ci.yml, wheels script |
| 40666583685 | 5d524652ab2e48c8569479151f4b6b51de024b67 | T1 SUCCESS: preflight, JVM tests/Kotlin compile and summary; Android correctly skipped; request commit b91dd296; native-ci replacement proven at T1, T2 still unverified | native-ci.yml, core/**, app/src/main/** |
| 40665928598 | 09c00047988b9141ad6141122eb5d0d400b977a7 | T1 preflight + JVM SUCCESS; summary FAILED due malformed shell quote; repair round 1 pending. Request commit 67f8a10a; no EMULATOR | native-ci.yml |
| 40661694569 | 6965c40cc0b92d88647c59f9e5485c025a2c342b | SUCCESS: JVM/APK build and API35 16/16 (7 NativeM27SpecialistMetadataInstrumentedTest + 9 NativeM27SpecialistFormInstrumentedTest); prior 15 expectation was stale (6+9), new source had 7 metadata cases. Artifact 12804837207; reused only if A6 dep paths unchanged | core/**, app/src/main/**, specialist form test, gradle/** |
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
6. G14 synthetic M26–M27 screenshot slice: source/T1/T2 done at 2445eb76. T2 40696534614 API35 10/10; 8 PNGs + hashes, 360/412/840dp light/dark and 200% font. NOT AC: owner must review screenshots, real TalkBack/focus/contrast across remaining states and milestone T3 still open; no new CI for owner-only approval.
7. M26 P06–P08/E03/E04 negative cross-owner matrix: source 752ee756, focused repair b38de8be; T1 40705038884 PASS and targeted API35 T2 40705791566 3/3 PASS. Encrypted cross-owner custody/goal rollback, minor/guardian/HUF and primary identity restart verified. NOT AC: broader navigation/G14 T3, physical device, AppSec and owner acceptance remain open.
8. M25 P01/P02 source/JVM/EMULATOR negative/interruption batch completed at 6f5cb3c5: re-admission before vault creation, invalid identity/Terms no-file, duplicate enrollment no-overwrite, re-consent SQLCipher restart, recovery replacement and one-use. T1 40736723667 SUCCESS; T2 40737480182 API35 15/15 SUCCESS. NOT AC: physical device/actual Keyguard interruption, AppSec, OWNER acceptance remain blocked.
9. M29 PARTIAL: 34bbf633 FIFO/FMV/returns goldens T1 40748714360 PASS; 5a9a5f4e coupon/EMI/Home T1 40750235052 + T2 40751028900 6/6 PASS; 7acd13f3 exact reviewed/estimated hybrid lookthrough and owner-lens JVM T1 40757210296 PASS. Next: connect reviewed splits and full household financial/tax reports; no invented prices/tax rates, no claim of M29 AC. If lacking verified source/year/rates, continue B4 item 10 without faking compliance.
10. M28 synthetic CAS gate slice done at e21966a7: require exact unique ISIN/folio for CAMS/KFINTECH/NPS preview, preserve multi-folio/DP identifiers; T1 40758465483 PASS, API35 T2 40759156459 15/15 PASS. Remaining P17–P20 encrypted positive approval, parser reconciliation and genuine ARM64 CAS = OPEN/BLOCKED on owner sample/device; NOT AC.
11. M30 PARTIAL but MOUNTED UI VERIFIED: source-corrected e9b42535 API35 40790101250 17/17 and integrated a1fedcd6 T1 40808560210 + API35 T2 40809097105 14/14 PASS with original APK hashes/ADB. Real Holdings official manual refresh, source/age unknown-value disclosure, member-lens refresh guard and prior SQLCipher restart/negative/manual priority tested. Next official live-source/physical ARM64, full G14 and OWNER review; no owner approval yet, NOT AC.
12. M31 PARTIAL: earlier dossier/Hindi API35 40811450454 12/12; next source 712c91e6 adds real System/Light/Dark, SQLCipher SGB/RBI vs market and IBJA review choices, masked privacy and redacted support clipboard/clear. Initial T1 40840075861 compile FAILED (positional test callers) repaired source 4f13f465; T1 40840805803 and API35 T2 40841339082 7/7 PASS. NEXT: verified IBJA owner-input/rate boundary, full account controls and E11 retention/export/delete UX, G14/device/security/OWNER. D57 no financial backup/export. NOT AC.
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