# Gmail statement trial

Package name: `in.growurdhan.app`

SHA-1 for the Android OAuth client: see [GMAIL-TEST-KEY.md](GMAIL-TEST-KEY.md).

The phone signs in with Google Play services. The OAuth client must be an **Android** client for that package and SHA-1. A Desktop client will not sign in on the phone. The client id supplied for this trial is `116886959433-3ja58ca8ioul1mllm5qdlni84revdinr.apps.googleusercontent.com` in project `cs-poc-8pzsb1yuro4llecvjmacafu`. That project id looks like a temporary Cloud lab project. If Google deletes it, create the Android client again in a normal project and keep the same package and SHA-1.

Scope: `gmail.readonly`. Mail is read on the phone. PDFs are not uploaded.

| Source | Sender | File name | How a member is chosen |
|---|---|---|---|
| CDSL | `eCAS@cdslstatement.com` | `AUG2026_AA07970379_TXN.pdf` or `_HLD.pdf` | CAS ID in the name, recorded on the first hand import |
| NPS | `nps-statements@mailer.proteantech.in` | `1120330238_Aug2026.pdf` | PRAN in the name (10 or 12 digits), recorded on the first hand import |
| NSDL | `NSDL-CAS@nsdl.co.in` | not published | PAN inside the PDF |
| CAMS | `donotreply@camsonline.com` | not published | PAN inside the PDF |
| KFintech | any `@kfintech.com` | not published | PAN inside the PDF. The PDF password is the one chosen on the KFin site, not the PAN |

NSDL, CAMS, and KFintech do not publish a CAS ID in the attachment name. Their own pages only name the sender and the PDF password (NSDL: PAN in capital letters; CAMS: the password chosen on the request, often the PAN; KFintech: the password chosen on the KFin site). The phone downloads those PDFs and keeps a file only when the PAN inside it matches a member who has Gmail sync on.

Gmail lists newest mail first. For CDSL and NPS the phone imports only the latest month for each CAS ID or PRAN, and prefers the transaction file over the holdings file from that same month. An older statement is not allowed to replace a newer one.

First sync looks back to 1 January 2019, then only mail after the previous sync. Already imported files are not downloaded again. Each tap reads at most four pages and opens at most twelve PDFs. If more mail remains, tap Sync again. Sync does not move the date forward while a CDSL or NPS file is still waiting for a CAS ID or PRAN from a hand import.
