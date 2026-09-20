import React, { useEffect, useRef, useState } from 'react';
import {
  TERMS_EFFECTIVE,
  TERMS_SECTIONS,
  TERMS_TEXT_ID,
  TERMS_TITLE,
  TERMS_VERSION,
  acceptCurrentTerms,
  closeApplication,
} from '../../lib/terms';

interface TermsGateProps {
  reason: 'register' | 'restore' | 'login';
  onAccept: () => void;
}

export const TermsGate: React.FC<TermsGateProps> = ({ reason, onAccept }) => {
  const scroller = useRef<HTMLDivElement>(null);
  const [reachedEnd, setReachedEnd] = useState(false);
  const [closing, setClosing] = useState(false);

  const purpose =
    reason === 'register'
      ? 'Creating a vault requires that you accept these Terms.'
      : reason === 'restore'
        ? 'Restoring a backup requires that you accept these Terms.'
        : 'Unlocking this version requires that you accept these Terms.';

  const checkEnd = () => {
    const el = scroller.current;
    if (!el) return;
    if (el.scrollHeight <= el.clientHeight + 12) {
      setReachedEnd(true);
      return;
    }
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - 28) setReachedEnd(true);
  };

  useEffect(() => {
    const id = window.setTimeout(checkEnd, 50);
    return () => window.clearTimeout(id);
  }, []);

  const decline = async () => {
    if (!reachedEnd || closing) return;
    setClosing(true);
    await closeApplication();
  };

  const accept = () => {
    if (!reachedEnd || closing) return;
    acceptCurrentTerms();
    onAccept();
  };

  return (
    <div
      className="fixed inset-0 z-[80] flex items-end sm:items-center justify-center p-0 sm:p-4"
      style={{ background: 'color-mix(in srgb, var(--app) 72%, transparent)' }}
      role="dialog"
      aria-modal="true"
      aria-labelledby="gud-terms-title"
    >
      <div
        className="w-full max-w-lg max-h-[92dvh] flex flex-col rounded-t-3xl sm:rounded-3xl overflow-hidden"
        style={{ background: 'var(--card)', color: 'var(--text)', boxShadow: 'var(--shadow)' }}
      >
        <div className="px-5 pt-5 pb-3 border-b" style={{ borderColor: 'var(--border)' }}>
          <h2 id="gud-terms-title" className="text-lg font-bold leading-snug">
            {TERMS_TITLE}
          </h2>
          <p className="text-[11px] mt-1" style={{ color: 'var(--muted)' }}>
            Effective {TERMS_EFFECTIVE} · {TERMS_VERSION} · {TERMS_TEXT_ID}
          </p>
          <p className="text-xs mt-2 leading-relaxed" style={{ color: 'var(--muted)' }}>
            {purpose} Scroll to the end. Accept and Don’t Accept stay off until then.
          </p>
        </div>

        <div
          ref={scroller}
          onScroll={checkEnd}
          className="flex-1 overflow-y-auto px-5 py-4 space-y-5"
          style={{ minHeight: '12rem' }}
        >
          {TERMS_SECTIONS.map(section => (
            <section key={section.id}>
              <h3 className="text-sm font-bold mb-1.5">{section.heading}</h3>
              <p className="text-xs leading-relaxed" style={{ color: 'var(--muted)' }}>
                {section.body}
              </p>
            </section>
          ))}
          <p className="text-[11px] font-semibold pt-2 pb-4" style={{ color: 'var(--accent-2)' }}>
            You have reached the end. Accept or Don’t Accept are now available.
          </p>
        </div>

        <div className="px-5 py-4 border-t grid grid-cols-2 gap-2" style={{ borderColor: 'var(--border)' }}>
          <button
            type="button"
            disabled={!reachedEnd || closing}
            onClick={() => void decline()}
            className="min-h-11 rounded-xl text-sm font-bold border"
            style={{
              borderColor: 'var(--bad)',
              color: reachedEnd ? 'var(--bad)' : 'var(--subtle)',
              opacity: reachedEnd ? 1 : 0.4,
            }}
          >
            Don’t Accept
          </button>
          <button
            type="button"
            disabled={!reachedEnd || closing}
            onClick={accept}
            className="min-h-11 rounded-xl text-sm font-bold"
            style={{
              background: 'var(--accent)',
              color: 'var(--accent-fg)',
              opacity: reachedEnd ? 1 : 0.4,
            }}
          >
            Accept
          </button>
        </div>
      </div>
    </div>
  );
};

export const ClosedScreen: React.FC = () => (
  <div className="min-h-[100dvh] flex items-center justify-center p-6" style={{ background: 'var(--app)', color: 'var(--text)' }}>
    <div className="card max-w-sm w-full p-8 text-center space-y-3">
      <h1 className="text-xl font-bold">GrowUrDhan is closed</h1>
      <p className="text-sm leading-relaxed" style={{ color: 'var(--muted)' }}>
        You declined the Terms and Conditions. The app cannot be used without accepting them.
      </p>
    </div>
  </div>
);
