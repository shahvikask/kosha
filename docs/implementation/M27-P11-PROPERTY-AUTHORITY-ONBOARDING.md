# M27 P11 — Registered-property and valuation authority onboarding (OPEN; no owner approval)

**Status:** Engineering design/acceptance contract, not an approved provider or functioning live registry integration. See specification v1.3 Sections 4.1–4.3, 12, 17; D57 remains binding.

## What has been verified

- `NativePropertySignedEvidence` defines domain-separated Ed25519 proof envelopes for testable registered-property identity and independent dated gross valuation. The signed comparison claim binds the existing native holding, owner, custody, property ID, source reference and SHA-256 digest.
- `NativePropertyProofRepository` persists original signed bytes, signers, owner/custody/holding, allocation and gross valuation inside the authenticated SQLCipher vault only. Its extension rows are insert-only (update/delete abort), and authenticated reads recheck signatures and source/owner state. One registered-property ID contributes its gross valuation once, with member attribution separately accounted for.
- Synthetic signers are only verification fixtures. `productionRoots` has **no approved authority keys**; production registration and monetary preview fail closed. Previous synthetic API35 passes must not be described as authentic legal title or licensed appraisal evidence.
- SQLCipher v9's frozen canonical tables and user_version remain untouched. The proof store currently creates an extra table/triggers transactionally on the first successfully verified admission, then checks the extension schema on reads/writes; reject damaged definitions instead of silently recreating missing constraints. This is a physical v9 extension, **not** an owner-approved versioned schema-v10 migration or migration release signoff.

## External evidence / trust-root decision required (OPEN)

1. Identify the **jurisdiction-specific actual land/registrar source** (India is not a uniform national title cryptographic issuer), supported document/API formats, coverage limitations, authoritative status, revocation/supersession signals and provenance as of date. Record the issuer's independently obtained public verification key or authenticated transport trust anchor, key pin/rotation/revocation policy and response expiry. A PDF uploaded by the owner, a filename, human-readable stamp, CAS, URL or SHA-256 alone is **not an issuer verification**.
2. Identify the **independent registered/licensed property valuer or verifiable valuation source**, exact subject property identifier, valuation date/currency/units/gross amount and issuer-signature/trust metadata. It must be independently checked, not a user-entered price or another title-document copy. Declare allowed staleness and valuation revocation policy.
3. The existing synthetic signing protocol expects **the issuer to sign app-generated holding/owner/custody IDs**. Real authorities normally do not sign those app-local IDs. A production adapter must **first verify the external canonical issuer-signed source bytes**, then separately create an authenticated, reviewed binding from the original registered-property/party identity to an active local SQLCipher owner/holding/custody, without forging or re-signing the source as if the registrar authored the app's fields. Implement this mapping and its original-row immutable digest before provisioning trust roots.
4. Define shared/joint registration proof: each owner must be named or reliably linked in the external original source, and each attributable percentage must be evidenced. Exclude inferred shares, stale title, ambiguous custodian links, or shares summing above 100%. Reject conflicting appraisals and source rows for the same ID, missing owner, closed custody and deleted/archived holdings, with **atomic no-write** results.
5. Define provider failure and changes (offline, revoked signer, expired source, joint-title amendment, replaced appraisal, release key rotation). A v9 insert-only row must never be overwritten or silently erased; any future correction requires a migration-reviewed append-only superseding evidence model and re-evaluation of the entire comparison before display. No implicit ownership rights inferred from the existing holding metadata.
6. Obtain real privacy, data use and owner consent approvals before enrolling sources: financial/PAN/address/document bytes must stay encrypted, never enter CI/logs/third-party telemetry, and availability must not create a cloud financial book. Test only synthetic issuer fixtures in public GitHub Actions.

## Release acceptance gates (not yet passed)

- Pin and independently verify actual trusted registry/appraisal authority keys, original signed source rows, response/request proof, dates, stable property ID and verified registered owners. Do not let app code inject arbitrary issuer keys as a production replacement.
- Document physical v9 extension compatibility with pre-existing encrypted vaults, v1–v8 migration, restart, SQLCipher `foreign_key_check`, schema integrity, DDL trigger tampering, atomic rollback, and future supersession/rotation. Preserve original ledger, holdings and source history and any legacy migration witnesses.
- Execute positive **genuine** owner-matched source proofs (not synthetic) only in private authorized acceptance, then negative cross-owner, duplicate, conflicting valuation/percentage, wrong digest, revoked/stale/mismatched source, tampered trigger and encrypted restart cases.
- Bind any successful **read-only household preview** to authenticated SQLCipher state with single property gross per registered identifier, separately displayed owner attribution/unallocated residual and explicit “indicative; not legal-title or real-time market data” language. No new ledger events, prices or claims about verified net worth.
- Complete G14 real screens at 360dp / 200% text / light-dark / TalkBack, independent AppSec and owner acceptance. Current synthetic UI-less repository tests do not satisfy this gate.

## Scope/dependency

**P11 M27 remains PARTIAL/NOT ACCEPTED.** Continue issuer-verified PPF/SSY, P09/P10 wizard, P12 holding metadata, P13/P14 events, P15 navigation and G14 in separate appropriately verified slices. Genuine P18/P19 CAS, physical ARM64, G11, OAuth, Billing, signed/minified AAB and market approval stay OPEN.

**D57:** Financial backup/export/restore/recovery and SAF backup scheduling **DEFERRED NEXT UPGRADE**, NOT PASSED. Retain `android:allowBackup="false"` and explicit permanent device-loss/uninstall financial-data-loss disclosure. This document grants no new owner decision; do not edit the decision log.
