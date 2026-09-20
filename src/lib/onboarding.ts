/**
 * First-open bilingual walkthrough. Shown once, before signup.
 * Copy is written as store marketing, not as a feature dump.
 */
export const ONBOARDING_SEEN_KEY = 'gud.onboarding.seen';

export interface OnboardingSlide {
  id: string;
  icon: 'shield' | 'phone' | 'lock' | 'file' | 'users' | 'rupee';
  enTitle: string;
  enBody: string;
  hiTitle: string;
  hiBody: string;
}

export const ONBOARDING_SLIDES: OnboardingSlide[] = [
  {
    id: 'why',
    icon: 'shield',
    enTitle: 'A family vault. Not another brokerage login.',
    enBody: 'GrowUrDhan is built so Indian families can see every member’s holdings in one private place. We do not sell a demat account. We do not call you. The books live on this phone.',
    hiTitle: 'परिवार की तिजोरी। ब्रोकर का लॉगिन नहीं।',
    hiBody: 'GrowUrDhan भारतीय परिवारों के लिए है — एक ही जगह, सबके निवेश। हम डीमैट नहीं बेचते, कॉल नहीं करते। हिसाब इसी फ़ोन पर रहता है।',
  },
  {
    id: 'quiet',
    icon: 'phone',
    enTitle: 'No mobile number. No inbox that never rests.',
    enBody: 'Most “free” money apps want your number and email so someone can pitch you a SIP. GrowUrDhan signs you in with a vault email that never leaves the device. No OTP. No “relationship manager”.',
    hiTitle: 'न मोबाइल नंबर, न बेचैन इनबॉक्स।',
    hiBody: 'ज़्यादातर मुफ़्त ऐप आपका नंबर इसलिए माँगते हैं ताकि SIP बेच सकें। यहाँ ईमेल सिर्फ़ ताला है — डिवाइस से बाहर नहीं जाता। OTP नहीं। कॉल नहीं।',
  },
  {
    id: 'fraud',
    icon: 'lock',
    enTitle: 'Harder to steal, because there is nothing in the cloud to steal.',
    enBody: 'If a site holds your CAS, a leak is their problem and yours. GrowUrDhan keeps statements on the phone, encrypted. Remote-access apps and other Android apps cannot open the vault. We cannot read it either.',
    hiTitle: 'चोरी मुश्किल — बादल में रखने को कुछ है ही नहीं।',
    hiBody: 'CAS अगर किसी साइट पर है, तो लीक आपका और उनका दोनों का नुकसान है। यहाँ विवरण फ़ोन पर, एन्क्रिप्टेड। दूसरे ऐप तिजोरी नहीं खोल सकते। हम भी नहीं पढ़ सकते।',
  },
  {
    id: 'cas',
    icon: 'file',
    enTitle: 'CAS is the monthly snapshot of everything you already own.',
    enBody: 'A Consolidated Account Statement from CDSL, NSDL, CAMS or KFin lists your demat and mutual funds. Import that PDF each month. Add NPS, PPF, gold, property and loans yourself. That is the whole ritual.',
    hiTitle: 'CAS वही मासिक तस्वीर है जो आपके पास पहले से है।',
    hiBody: 'CDSL, NSDL, CAMS या KFin का Consolidated Account Statement डीमैट और म्यूचुअल फंड दिखाता है। हर महीने वही PDF लगाएँ। NPS, PPF, सोना, घर और ऋण आप जोड़ें। बस इतना काम है।',
  },
  {
    id: 'family',
    icon: 'users',
    enTitle: 'One house. Every PAN. One picture.',
    enBody: 'Self, spouse, children, HUF — each person keeps their own statements, yet Home shows the family. Broker apps stop at one login. This is the view they do not sell, because they are not your family office.',
    hiTitle: 'एक घर। हर PAN. एक तस्वीर।',
    hiBody: 'स्वयं, जीवनसाथी, बच्चे, HUF — बयान अलग, होम पर परिवार एक साथ। ब्रोकर ऐप एक लॉगिन पर रुक जाते हैं। यह नज़रिया वे नहीं देते — वे आपका परिवार नहीं हैं।',
  },
  {
    id: 'price',
    icon: 'rupee',
    enTitle: 'Thirty days on us. Then a licence you buy once.',
    enBody: 'Try every feature for 30 days through Google Play. After that, a perpetual licence is ₹249 — once, tied to your Play account, valid on a new phone. Optional annual support is ₹250 the first year, ₹299 later. No surprise in-app store.',
    hiTitle: 'तीस दिन हमारे साथ। फिर एक बार का लाइसेंस।',
    hiBody: 'Google Play से 30 दिन सब कुछ आज़माएँ। उसके बाद स्थायी लाइसेंस ₹249 — एक बार, आपके Play खाते से बँधा, नए फ़ोन पर भी। वार्षिक सहयोग पहले साल ₹250, बाद में ₹299। कोई छुपी दुकान नहीं।',
  },
];

export function hasSeenOnboarding(): boolean {
  try {
    return localStorage.getItem(ONBOARDING_SEEN_KEY) === '1';
  } catch {
    return false;
  }
}

export function markOnboardingSeen(): void {
  localStorage.setItem(ONBOARDING_SEEN_KEY, '1');
}

export function resetOnboarding(): void {
  localStorage.removeItem(ONBOARDING_SEEN_KEY);
}
