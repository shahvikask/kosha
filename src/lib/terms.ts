/**
 * GrowUrDhan Terms and Conditions.
 * Versioned so a later change re-opens the scroll-to-accept gate.
 * Acceptance is stored on this device only (version + timestamp).
 */
import { isNativeShell, nativeCommand } from './license';

export const TERMS_VERSION = '2026-10-06';
export const TERMS_EFFECTIVE = '6 October 2026';
export const TERMS_TEXT_ID = 'GUD-TOS-2026-10-06-v4';
export const TERMS_TITLE = 'GrowUrDhan Terms and Conditions';

const LS_ACCEPTED = 'gud.terms.acceptedVersion';
const LS_ACCEPTED_AT = 'gud.terms.acceptedAt';
const LS_ACCEPTED_ID = 'gud.terms.acceptedTextId';
const memoryStore = new Map<string, string>();

function lsGet(key: string): string | null {
  try {
    if (typeof localStorage !== 'undefined') {
      const v = localStorage.getItem(key);
      if (v != null) return v;
    }
  } catch {
    /* private mode */
  }
  return memoryStore.get(key) ?? null;
}

function lsSet(key: string, value: string): void {
  memoryStore.set(key, value);
  try {
    if (typeof localStorage !== 'undefined') localStorage.setItem(key, value);
  } catch {
    /* private mode */
  }
}

export interface TermsSection {
  id: string;
  heading: string;
  body: string;
}

export const TERMS_SECTIONS: TermsSection[] = [
  {
    id: 'parties',
    heading: '1. Parties, product, and binding agreement',
    body:
      'These Terms and Conditions (“Terms”) are a legally binding agreement between you (“you”, “User”) and GrowUrDhan (the “Company”, “we”, “us”), the publisher of the software product named GrowUrDhan on Google Play (package in.growurdhan.app). The licence to use the App is granted and distributed solely by GrowUrDhan. GrowUrDhan is a local-first family wealth tracker for recording, organising, and viewing holdings and statements that you yourself enter or import on a device you control (the “App”). ' +
      'By tapping Accept after scrolling these Terms to the end, or by creating a vault, restoring a backup, or otherwise using the App after being presented with these Terms, you agree to be bound by them. If you tap Don’t Accept, you must not use the App and the App will close. ' +
      'These Terms are an electronic record under the Information Technology Act, 2000, including Sections 4, 5 and 10A, and the rules made thereunder. No physical signature is required. Your tap on Accept, together with the version identifier ' +
      TERMS_TEXT_ID +
      ' stored on this device with a timestamp, is intended as your electronic acceptance. Keep a copy. A public copy may also be published at the GrowUrDhan marketing site linked from the App.',
  },
  {
    id: 'eligibility',
    heading: '2. Eligibility and who may use the App',
    body:
      'The App is only for natural persons who are 18 years of age or older and who have legal capacity to contract under the Indian Contract Act, 1872. The App is not directed at children, is not a Designed for Families product, and must not be used by anyone under 18. ' +
      'If you add a family member who is a minor, you do so only as their parent or legal guardian, solely to track holdings you already manage for them, and you remain fully responsible for that data. ' +
      'You represent that you are not barred from receiving the software under the laws of India or any other country whose laws apply to you.',
  },
  {
    id: 'not-advice',
    heading: '3. The App is a tracker, not an adviser, broker, or fiduciary',
    body:
      'THIS IS THE CENTRAL LIMITATION OF THE PRODUCT. GrowUrDhan is a record-keeping and visualisation tool. It is not a bank, non-banking financial company, payment system, depository, depository participant, stock broker, portfolio manager, mutual fund, AMFI-registered distributor, insurance repository, PFRDA point of presence, or credit rating agency. ' +
      'GrowUrDhan is not registered with the Securities and Exchange Board of India as an Investment Adviser under the SEBI (Investment Advisers) Regulations, 2013, and is not registered as a Research Analyst under the SEBI (Research Analysts) Regulations, 2014. Nothing in the App is investment advice, a research report, a recommendation, a solicitation, or an offer to buy or sell any security, commodity, currency, insurance product, or other financial instrument. ' +
      'Figures, charts, “returns”, tax buckets, “annual return”, unrealised gain, asset mix, and similar displays are mechanical calculations from data you supplied or that a parser extracted from a statement you supplied. They are not a recommendation to hold, buy, or sell. ' +
      'GrowUrDhan owes you no fiduciary duty, no duty of care as an investment adviser, and no duty to monitor your portfolio, warn you about concentration, tax, estate, or market events, or contact you. You agree that you will not treat any screen, number, colour, label, or notification as advice. ' +
      'You remain solely responsible for every investment, tax, estate, and legal decision, and you will take those decisions only after independent verification against original statements and, where appropriate, after consulting a SEBI-registered investment adviser, a chartered accountant, and/or an advocate of your own choosing.',
  },
  {
    id: 'no-professional',
    heading: '4. Not tax, legal, or accounting advice',
    body:
      'Tax lots, capital-gains classifications, grandfathering, slab labels, financial-year buckets, and any “tax summary” in the App are illustrative arithmetic only. They are not an opinion under the Income-tax Act, 1961, not a computation for a return, and not advice of a chartered accountant or tax practitioner. Rates and rules change. The App may apply a simplified or outdated rule, miss an exemption, or mis-classify a holding. ' +
      'Nothing in the App is legal advice, an emergency-succession plan with legal effect, or a will, nomination, or trust instrument. A “family printout” or “emergency sheet” is a convenience copy of what is already in the vault. It does not create rights against a company, depository, or insurer. ' +
      'You will not file a tax return, pay tax, claim a refund, or take a legal position in any court, tribunal, or tax proceeding in reliance on the App without independent professional verification.',
  },
  {
    id: 'accuracy',
    heading: '5. Data accuracy, CAS parsing, and prices — you verify',
    body:
      'The App may import a Consolidated Account Statement (CAS) from CDSL, NSDL, CAMS, KFintech, an NPS statement, or similar PDF you choose. Parsing is best-effort. PDFs vary by registrar, broker, password, scan quality, and layout. The parser may skip a folio, duplicate a line, mis-read a quantity, ISIN, NAV, or value, drop an AIF or unlisted line, or stop early. A “match” or a small printed-versus-parsed difference does not mean the vault is complete or correct. ' +
      'You agree that you will compare the App against the original PDF and against your broker, registrar, depository, NPS, bank, and issuer portals before relying on any total. If a number looks wrong, you will not use it. ' +
      'End-of-day or other prices, if shown, may be delayed, missing, rounded, or from a third-party source that can fail. Stale prices are not a valuation. Unlisted, PPF, EPF, property, gold-in-hand, loans, and similar items are whatever you typed. ' +
      'GrowUrDhan does not audit your books, does not contact CDSL/NSDL/CAMS/KFin/NPSCRA on your behalf, and does not warrant that any import is complete.',
  },
  {
    id: 'your-data',
    heading: '6. Your data, family members, and lawful use',
    body:
      'You represent that every name, PAN, date of birth, folio, demat identifier, holding, and document you enter is accurate to the best of your knowledge and that you have the right to store it on this device. ' +
      'If you enter another person’s PAN, statements, or holdings (spouse, child, parent, HUF, joint), you represent that you are authorised to do so — for example as that person, as a parent or guardian of a minor, as karta of an HUF, or with their informed consent — and that you will delete that data if that authority ends. You will not use the App to collect, profile, or store third-party financial data you have no right to hold. ' +
      'You will not use the App to commit fraud, evade tax, launder money, violate sanctions, or mislead any person, court, tax authority, or regulator. You will not reverse engineer the App in order to bypass licence, integrity, or encryption controls, except to the limited extent a non-waivable law allows interoperability. ' +
      'You will keep your vault password, security answers, device PIN, and cloud-folder access under your exclusive control. Anyone who can unlock this phone, your Google or Microsoft account, or the folder you chose for backups may be able to copy an encrypted backup file. The file still needs your vault password; protect both.',
  },
  {
    id: 'local-first',
    heading: '7. Local storage, encryption, and no password recovery',
    body:
      'The vault is designed to live on the device, encrypted. On Android the wrapping key is intended to live in the hardware-backed Android Keystore. A copied APK, another app on the phone, or a remote-access tool is not supposed to read the vault without that key and your password. No security is perfect. You accept residual risk of device theft, malware, backups you yourself create, forensic extraction, operating-system bugs, and user error. ' +
      'GrowUrDhan does not operate a cloud login for holdings, does not receive your CAS on a server as a condition of using the App, and cannot reset or recover your vault password. If you forget the password and the security-question answers, the data on this device is gone. That is the privacy design, not a defect. ' +
      'Email is a username on this device. It is not an inbox we write to. We do not ask for a mobile number. Google Play, if you buy a licence, sees what Google sees for any Play purchase. That is Google’s processing, described in Google’s policies, not a GrowUrDhan account system.',
  },
  {
    id: 'backups',
    heading: '8. Backups and cloud folders (Google Drive, OneDrive, and others)',
    body:
      'You are solely responsible for having a working backup. Devices fail. Uninstall, “start over”, a factory reset, or a lost phone can destroy the only copy. ' +
      'A GrowUrDhan backup is an encrypted .kosha file locked with your vault email and password. GrowUrDhan cannot open it. ' +
      'If you switch on scheduled backup, you choose a folder through the Android document tree (or a browser directory picker on web). That folder may be Google Drive, OneDrive, Downloads, or any other provider the system lists. You are instructing YOUR cloud or file provider to store files you created. GrowUrDhan does not operate Google Drive or OneDrive, does not see those files, does not hold those accounts, and is not responsible for their outages, quota, sharing settings, ransomware, or for anyone who can open your Google or Microsoft account. ' +
      'Scheduled backup keeps only the last five .kosha files in that folder and deletes older ones it recognises. Five is not a substitute for an off-site copy you control. ' +
      'The vault password is held in memory only while the App is unlocked. Scheduled backup therefore runs after you unlock, and after a CAS import while the App is in the foreground. It does not run while the App is killed, because that would require storing your password on disk, which these Terms refuse to do. If you lock the vault or the process is reclaimed, scheduled backup waits until the next unlock. ' +
      'You grant the App permission only to write and prune backup files in the folder you picked. You can revoke that folder in system settings. You will not hold GrowUrDhan liable for a backup that did not run, a folder permission that expired, a cloud provider that moved or encrypted the file, or a restore that you performed against the wrong file.',
  },
  {
    id: 'licence',
    heading: '9. Software licence, trial, and Google Play charges',
    body:
      'GrowUrDhan grants you a personal, non-exclusive, non-transferable, revocable licence to use the App on devices where it was legitimately installed, solely to maintain your own (and authorised family) records, subject to these Terms and to Google Play’s terms. The licence is distributed by GrowUrDhan. You do not acquire ownership of the software, trademarks, parsers, or documentation. ' +
      'A genuine Google Play install may start a thirty (30) day trial on that device. During that trial every feature is enabled, including Gmail sync and features that later require the annual service. After the trial the vault file may remain on the device but the App may require a perpetual licence sold only through Google Play Billing. Buying the ₹249 licence turns Gmail sync off, even if it was used in the trial, and closes the ₹499 product. Gmail sync comes back only after the ₹250 upgrade. Buying the ₹499 licence includes perpetual access and Gmail sync, and closes both the ₹249 product and the ₹250 upgrade. Paid products are: (1) a perpetual manual licence at ₹249, which includes statement import by hand and does not include Gmail sync; (2) a perpetual Gmail-sync licence at ₹499, which includes the manual licence and Gmail statement sync; (3) a one-time upgrade of ₹250 if you already hold the ₹249 licence and later turn on Gmail sync; and (4) an optional annual service subscription at ₹199 per year for security updates, enhancements, and app improvements. The annual service is a recurring Google Play subscription and can be bought only after a perpetual licence (₹249, ₹499, or ₹249 plus ₹250) is on the same Play account. It may be added at the same purchase, but Play registers it as a subscription, not as part of the one-time licence. If you buy the ₹499 plan, the ₹249 plan is not required. Play, not GrowUrDhan, processes the payment. Indicative prices are inclusive of applicable Play billing, subject to change in Play Console and to tax. ' +
      'A sideloaded or extracted APK is not licensed unless a genuine Play licence for the same Play account is already present. You will not circumvent Play Integrity, billing, or sideload locks. ' +
      'Refunds, chargebacks, and payment disputes are governed by Google Play policy. Support is an optional subscription of enhancements and is not a warranty, not an SLA, and not investment support. You may cancel it in Google Play. After cancel, or if you do not renew, paid support continues until that subscription period ends; then new features and improvements stop. The app update itself remains available to a perpetual licence. Cancelling support does not cancel a lifetime licence already purchased. ' +
      'All licences are for personal family use, not for running a commercial bookkeeping bureau, redistribution, or SaaS wrapping of the App.',
  },
  {
    id: 'ip',
    heading: '10. Intellectual property',
    body:
      'GrowUrDhan, the logos, screen layouts, and documentation are GrowUrDhan’s intellectual property or are used under licence. Third-party libraries (including parsers, SQLite, and system components) remain the property of their owners and are licensed under their own terms. You will not remove proprietary notices. You will not use GrowUrDhan marks to suggest a partnership with CDSL, NSDL, CAMS, KFintech, NPS, SEBI, RBI, or any issuer. ' +
      'Feedback you send may be used by GrowUrDhan without obligation to you.',
  },
  {
    id: 'third-parties',
    heading: '11. Third-party services and statements',
    body:
      'The App may call, display, or rely on Google Play, Android, the device file picker, Google Drive, OneDrive, other document providers, and PDF statements issued by depositories, registrars, PRAN custodians, and brokers. Those parties have their own terms and privacy policies. GrowUrDhan is not their agent and is not liable for their acts, outages, or errors. ' +
      'A statement password you type to unlock a PDF is used on the device to open that file and is not intended to be sent to GrowUrDhan.',
  },
  {
    id: 'privacy',
    heading: '12. Privacy',
    body:
      'The Privacy Policy published with the App (including at the Play listing URL) is incorporated into these Terms. In short: vault contents are designed to stay on the device; GrowUrDhan does not run a holdings cloud; Play Billing is processed by Google; a backup folder you pick is your processing on that provider. ' +
      'Because vault contents are on-device, deleting the vault on the device is deletion everywhere GrowUrDhan can reach. Cloud copies you yourself stored remain until you delete them there. GrowUrDhan cannot remotely wipe your Drive or OneDrive.',
  },
  {
    id: 'warranty',
    heading: '13. No warranty — the App is provided “as is”',
    body:
      'TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE LAW, THE APP IS PROVIDED “AS IS” AND “AS AVAILABLE”, WITHOUT WARRANTY OF ANY KIND, WHETHER EXPRESS, IMPLIED, STATUTORY, OR OTHERWISE, INCLUDING MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, TITLE, QUIET ENJOYMENT, AND NON-INFRINGEMENT. ' +
      'GrowUrDhan does not warrant that the App will be uninterrupted, secure, free of defects, free of viruses, or that data will not be lost, that parsing will be complete, that prices will be current, that tax labels will match law, or that the App is suitable for any regulated filing. ' +
      'You assume the entire risk of using the App as a tracker. You are responsible for backups and for independent verification.',
  },
  {
    id: 'liability',
    heading: '14. Limitation of liability',
    body:
      'TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE LAW, GROWURDHAN (AND ITS DIRECTORS, OFFICERS, EMPLOYEES, AGENTS, SUCCESSORS, AND LICENSORS) SHALL NOT BE LIABLE FOR ANY INDIRECT, INCIDENTAL, SPECIAL, CONSEQUENTIAL, EXEMPLARY, OR PUNITIVE DAMAGES, OR FOR ANY LOSS OF PROFITS, REVENUE, DATA, GOODWILL, BUSINESS INTERRUPTION, INVESTMENT LOSS, TAX INTEREST OR PENALTY, OPPORTUNITY COST, OR COST OF SUBSTITUTE SOFTWARE, WHETHER IN CONTRACT, TORT (INCLUDING NEGLIGENCE), STRICT LIABILITY, OR OTHERWISE, EVEN IF ADVISED OF THE POSSIBILITY. ' +
      'TO THE MAXIMUM EXTENT PERMITTED BY LAW, GROWURDHAN’S TOTAL AGGREGATE LIABILITY ARISING OUT OF OR RELATING TO THE APP OR THESE TERMS SHALL NOT EXCEED THE GREATER OF (A) THE AMOUNT YOU ACTUALLY PAID TO GROWURDHAN FOR THE APP VIA GOOGLE PLAY IN THE TWELVE (12) MONTHS BEFORE THE CLAIM, AND (B) RUPEES ONE THOUSAND (₹1,000). IF YOU PAID NOTHING, THE CAP IS ₹1,000. ' +
      'These limits do not apply to liability that applicable law does not allow to be limited, including liability for fraud, wilful misconduct, or death or personal injury caused by negligence where such a limit is prohibited. They do apply, to the extent allowed, to parsing mistakes, missed holdings, wrong prices, failed backups, licence checks, and any decision you made after looking at the App. ' +
      'You agree that this allocation of risk is a fundamental part of the bargain and that the App would not be offered at the stated price without it. You further agree that any claim must be filed within one (1) year after the cause of action accrued, or the shortest period allowed by mandatory law if longer is required.',
  },
  {
    id: 'indemnity',
    heading: '15. Indemnity',
    body:
      'You will indemnify, defend, and hold harmless GrowUrDhan from and against any claim, demand, loss, damage, cost, and reasonable legal fee arising out of or related to: (a) your use or misuse of the App; (b) data you entered or imported, including another family member’s PAN or statements; (c) your violation of these Terms or of law; (d) a backup you stored on a third-party cloud; (e) any allegation that your content infringes a third party’s rights; and (f) tax, investment, or legal positions you took in reliance on the App. GrowUrDhan may assume exclusive defence at your expense. This duty survives termination.',
  },
  {
    id: 'prohibited',
    heading: '16. Prohibited conduct and enforcement',
    body:
      'You will not: scrape or overload the App; probe encryption except on a copy you own for a lawful security test you report in good faith; resell licence tokens; misrepresent a Play Integrity result; use the App to store data of persons who have not authorised you; or use the App after Don’t Accept. ' +
      'GrowUrDhan may refuse support, disable a compromised build, or terminate the licence if you materially breach these Terms. On-device data remains yours to export or delete subject to encryption.',
  },
  {
    id: 'termination',
    heading: '17. Term, account deletion, and start over',
    body:
      'These Terms apply from Accept until you delete the App and your backups, except clauses that by nature survive (including 3–6, 10, 13–15, 18–21). ' +
      'You may delete the vault on the device using the in-App controls (including Start over / delete accounts on this device) and the public deletion instructions linked from the Play listing. Deletion on the device does not delete copies in a Drive or OneDrive folder you chose, does not reverse a Play purchase, and does not require GrowUrDhan to hold a server-side account — there is none for holdings.',
  },
  {
    id: 'changes',
    heading: '18. Changes to the App and to these Terms',
    body:
      'GrowUrDhan may change features, parsers, prices (via Play Console), and these Terms. A new TERMS version identifier will be shown in-App. Continued use after you Accept the new version is acceptance. If you do not Accept, the App will close and you should uninstall and delete backups if you do not agree. Material changes will not apply retroactively to facts that already occurred to the extent the law forbids that.',
  },
  {
    id: 'law',
    heading: '19. Governing law, venue, and dispute process',
    body:
      'These Terms are governed by the laws of India, without regard to conflict-of-law rules. Subject to any non-excludable rights under the Consumer Protection Act, 2019, you and GrowUrDhan submit to the exclusive jurisdiction of the competent courts in India, and the venue shall be the court of competent territorial jurisdiction over GrowUrDhan’s principal place of business in India. ' +
      'Before filing a claim (other than for interim relief to protect intellectual property or to prevent irreversible data misuse), you agree to send a written notice describing the dispute in reasonable detail and to wait thirty (30) days for a good-faith attempt to resolve it. Notices to GrowUrDhan may be sent to the support email published on the Google Play listing for package in.growurdhan.app. ' +
      'If a consumer forum or other mandatory venue has non-waivable jurisdiction, that mandatory law prevails for that claim only. These Terms do not limit a consumer’s statutory rights that cannot be waived. ' +
      'If you access the App from outside India, you still agree to Indian law and Indian courts for disputes about the App, to the maximum extent permitted where you live.',
  },
  {
    id: 'misc',
    heading: '20. Miscellaneous',
    body:
      'Entire agreement: these Terms, the Privacy Policy, the in-App licence screen, and the Play listing descriptions are the entire agreement and supersede prior oral or written understandings about the App. They do not supersede Google Play’s terms between you and Google. ' +
      'Severability: if a clause is held unenforceable, it is modified to the minimum extent required, and the rest remains in force. A finding that a liability cap is too low may be replaced by the maximum cap the court will enforce, not by unlimited liability. ' +
      'Waiver: a failure to enforce is not a waiver. Assignment: you may not assign these Terms; GrowUrDhan may assign them to any successor that continues the App under the GrowUrDhan name, including after incorporation or reorganisation. Force majeure: GrowUrDhan is not liable for delay or failure caused by events beyond reasonable control, including platform outages, changes to Android or Play policy, registrar PDF format changes, and internet or cloud-provider failure. ' +
      'Language: these Terms are in English. Any Hindi or other summary is a convenience. The English text controls. ' +
      'Interpretation: headings are for reading only. “Including” means “including without limitation”. Examples do not narrow the clause. ' +
      'Relationship: nothing here creates a partnership, joint venture, employment, or agency. ' +
      'Open source: third-party licences that accompany the App remain in force and, where they conflict for that component only, those licences control for that component.',
  },
  {
    id: 'ack',
    heading: '21. Acknowledgement you make by tapping Accept',
    body:
      'You acknowledge that you have scrolled to the end, that you have had an opportunity to read these Terms, that you understand GrowUrDhan is a local tracker and not an investment adviser, that GrowUrDhan cannot recover a forgotten password, that backups and verification against original statements are your responsibility, that liability is capped as stated, that you are 18 or older, and that you enter this agreement freely. ' +
      'If you do not agree to any part of these Terms, tap Don’t Accept. The App will close. Do not create a vault and do not restore a backup.',
  },
];

export function termsPlainText(): string {
  return [
    `${TERMS_TITLE}`,
    `Effective ${TERMS_EFFECTIVE}. Version ${TERMS_VERSION}. Text id ${TERMS_TEXT_ID}.`,
    ...TERMS_SECTIONS.map(s => `${s.heading}\n\n${s.body}`),
  ].join('\n\n');
}

export function hasAcceptedCurrentTerms(): boolean {
  return lsGet(LS_ACCEPTED) === TERMS_VERSION && lsGet(LS_ACCEPTED_ID) === TERMS_TEXT_ID;
}

export function acceptCurrentTerms(): { version: string; textId: string; acceptedAt: string } {
  const acceptedAt = new Date().toISOString();
  lsSet(LS_ACCEPTED, TERMS_VERSION);
  lsSet(LS_ACCEPTED_ID, TERMS_TEXT_ID);
  lsSet(LS_ACCEPTED_AT, acceptedAt);
  return { version: TERMS_VERSION, textId: TERMS_TEXT_ID, acceptedAt };
}

export function termsAcceptedAt(): string | null {
  return lsGet(LS_ACCEPTED_AT);
}

let declineHandler: (() => void) | null = null;

export function setTermsDeclineHandler(fn: (() => void) | null): void {
  declineHandler = fn;
}

/** Native: finish the task. Web: show the closed screen. */
export async function closeApplication(): Promise<void> {
  if (isNativeShell()) {
    try {
      await nativeCommand('app_exit');
    } catch {
      /* fall through to in-app closed screen */
    }
  }
  declineHandler?.();
}
