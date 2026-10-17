# AGENTS.md — GrowUrDhan native redevelopment

**Re-read this file before every prompt, code change, test, troubleshooting session, and GitHub Actions decision.** Chat memory is not a source of requirements.

Authoritative requirements live in [docs/GROK-FULL-NATIVE-REDEVELOPMENT-SPEC.md](docs/GROK-FULL-NATIVE-REDEVELOPMENT-SPEC.md) (GrowUrDhan Grok Specification **v1.2**). If this file and the specification conflict, the specification wins. Rules **G01–G15**, requirements **P01–P39** and **E01–E18**, **Section 6A**, and **Section 17** are binding.

## Repository boundaries

| Role | Location |
|---|---|
| Original product | `https://github.com/shahvikask/kosha` |
| Frozen functional reference | branch `GUD-Android-Gmail` at commit `af96dcc8967ab595e1d9acee78e29e500cb99a9e` |
| This work | branch `GUD-Android-Redevelopment` only |
| Forbidden | commits on `GUD-Android-Gmail`, `GUD-Android`, `GUD-Android-APK`, `main`, or `vikaskunjbiharishah-ui/GUD` |

Do not merge the separate native repository `vikaskunjbiharishah-ui/GUD` into this experiment.

## Binding rules (read the specification for the full text)

- **G01** Build independently from the frozen legacy source. Never commit on the reference branch.
- **G02** Treat original `src/`, `src-tauri/`, tests, fixtures, documents, and branded assets as read-only parity evidence. New native source goes under `android-native/`. Preserve `src/casparser-main/**` and the protected parser scripts **byte-for-byte**. Do not edit parser algorithms to fix a parse bug.
- **G03** P01–P39 and E01–E18 are compulsory. Map every original screen, field, table, command, and error state. A mismatch needs an owner-approved deviation in `docs/implementation/DECISION-LOG.md`.
- **G04** Production UI is Kotlin + Jetpack Compose + Material 3. No WebView financial UI and no new Tauri/Capacitor shell. Python stays only as the frozen CASPARSER via Chaquopy. No cloud book of household finances.
- **G05** Scaled-integer money (scale 10,000) and quantity (scale 100,000,000). Append-only ledger. Do not invent costs, prices, tax law, ownership, or purchases.
- **G06** Evidence cycle: audit, checklist, implement the whole scope, local tests, diff review, integration tests, coherent commit, then a deliberately dispatched workflow on the exact SHA. Watch that run to a terminal result.
- **G07** Do not fake green. Do not delete or loosen failing tests. An unrun, skipped, cancelled, or older-SHA gate is not a pass.
- **G08** Local checks before costly emulator CI. Avoid push-triggered expensive Android emulator jobs. **Owner standing authorization (2026-10-17):** the owner made `shahvikask/kosha` public and explicitly authorized relevant GitHub Actions development builds and tests without a fresh per-run approval. Deliberately dispatch source-SHA-pinned, milestone-relevant workflows when useful; prefer targeted JVM/build checks before expensive emulator runs, group tests, monitor each run to a terminal result, and record evidence. Public visibility does not itself guarantee every runner or workflow is free. This authorization does **not** permit release publishing, changing protected/frozen branches, exposing secrets, or waiving security/device/owner acceptance.
- **G09** No secrets, PAN, tokens, PDF bytes, vault keys, or real customer statements in Git, logs, CI artifacts, or prompts.
- **G10** Gmail OAuth, Play Billing, signing, and real-device proof stay **BLOCKED** until the owner supplies the service or device. Do not claim those integrations work from unit tests.
- **G11** When CI is authorized, inspect the real run, logs, and artifacts. A queued or running workflow is `RUNNING—NOT VERIFIED`. A new commit needs a new run.
- **G12** Label the stage: `SOURCE MAPPED`, `IMPLEMENTED`, `LOCAL TESTED`, `CI JVM PASSED`, `CI EMULATOR PASSED`, `REAL DEVICE PASSED`, `SECURITY ACCEPTED`, `OWNER ACCEPTED`.
- **G13** Rehydrate from this file, the specification, `docs/implementation/STATUS.md`, `SESSION-HANDOFF.md`, and `DECISION-LOG.md` at the start of every session.
- **G14** Section 6A “Heritage Modern Wealth” is a release gate. Keep the approved GrowUrDhan logo and navy/gold/teal identity. A compiling screen is not a finished screen.
- **G15** Start with the Section 17.3 receipt. End with the Section 17.4 delivery report, an updated `SESSION-HANDOFF.md`, and a next prompt that says to re-read `AGENTS.md`.

## Startup and finish

Before editing:

1. Read this file, the specification sections that apply, `docs/implementation/STATUS.md`, `docs/implementation/SESSION-HANDOFF.md`, `docs/implementation/CI-RUNS.md`, and `docs/implementation/DECISION-LOG.md`.
2. Verify `git remote -v`, branch, `git rev-parse HEAD`, and `git status --short`.
3. Print the Section 17.3 STARTUP COMPLIANCE RECEIPT from observed evidence. Use `UNVERIFIED` or `TO CREATE IN M00` rather than inventing a read.

Before ending a substantial session, update the handoff files and give the Section 17.4 report. Do not mark a milestone accepted without the required tests.

## Current stage

M00 is owner-accepted with limitations. M24 remains STARTED / NOT ACCEPTED. M24 current source **JVM 24/24 and Android application Kotlin compilation PASSED** on exact source SHA `38d349a2676a4c99cc9d80dfcf07a1a03efa793d`, targeted run [#39023384556](https://github.com/shahvikask/kosha/actions/runs/39023384556), attempt 2. That JVM/compile run is not device runtime acceptance. **Android API 35 x86_64 emulator instrumentation 7/7 PASSED** at exact SHA `c5ead91ebc332c9a35c3e8e8803dd266039db84a` ([run #39024627068](https://github.com/shahvikask/kosha/actions/runs/39024627068)); a strengthened **7/7 PASSED** at exact SHA `900fa54bf7e73f5e17612dda01a08ed95abd64ec` ([run #39025190962](https://github.com/shahvikask/kosha/actions/runs/39025190962)), including real SQLCipher typed-row commit/reopen, fail-closed duplicate and foreign-key rollback. Emulator != physical device, and full backup-to-new-vault restore/Keystore promotion/owner acceptance remain OPEN. Actual frozen TypeScript exporter/JCE interoperability passed locally with synthetic data, but replay-safe atomic financial restore, Android build/device tests and owner acceptance are still open. **GitHub Actions standing authorization granted 2026-10-17:** relevant targeted M24 and subsequent development builds/tests on the redevelopment branch may be deliberately dispatched without asking again for each run. Preserve cost discipline and exact-SHA verification; M24 remains NOT ACCEPTED until all mandatory gates pass.

## Loop check

End every response with one line, exactly `Loop: no` or `Loop: yes — <reason>`. A loop is repeating a blocked M00 step, inventorying another screen, or making a documentation-only commit that does not change product behavior. Stopping with no commit is not a loop. Do not continue a loop.
