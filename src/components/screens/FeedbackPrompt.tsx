import React, { useState } from 'react';
import { Star, X } from 'lucide-react';
import { dismissFeedbackPrompt, rateOnGooglePlay } from '../../lib/feedback';

interface FeedbackPromptProps {
  open: boolean;
  onClose: () => void;
}

export const FeedbackPrompt: React.FC<FeedbackPromptProps> = ({ open, onClose }) => {
  const [busy, setBusy] = useState(false);

  if (!open) return null;

  const closeWithoutRating = () => {
    if (busy) return;
    dismissFeedbackPrompt();
    onClose();
  };

  const submitToPlay = () => {
    if (busy) return;
    setBusy(true);
    void rateOnGooglePlay();
    onClose();
  };

  return (
    <div
      className="fixed inset-0 z-[80] flex items-end sm:items-center justify-center p-4"
      style={{ background: 'rgba(4, 12, 20, 0.62)' }}
      role="dialog"
      aria-modal="true"
      aria-labelledby="gud-feedback-title"
    >
      <div className="card w-full max-w-md p-5 sm:p-6 space-y-4" style={{ color: 'var(--text)' }}>
        <div className="flex items-start justify-between gap-3">
          <div
            className="w-11 h-11 rounded-2xl flex items-center justify-center shrink-0"
            style={{ background: 'color-mix(in srgb, var(--accent) 18%, transparent)', color: 'var(--accent)' }}
          >
            <Star className="w-5 h-5" />
          </div>
          <button
            type="button"
            onClick={closeWithoutRating}
            className="p-2 -mr-1 -mt-1 rounded-xl min-w-11 min-h-11 flex items-center justify-center"
            aria-label="Close"
            style={{ color: 'var(--muted)' }}
          >
            <X className="w-5 h-5" />
          </button>
        </div>
        <div className="space-y-2">
          <h2 id="gud-feedback-title" className="text-lg font-semibold tracking-tight">
            How is GrowUrDhan on this phone?
          </h2>
          <p className="text-sm leading-relaxed" style={{ color: 'var(--muted)' }}>
            A rating on Google Play posts to our Play listing. It helps other families find a tracker that stays on the device. Your vault is not sent.
          </p>
        </div>
        <button
          type="button"
          disabled={busy}
          onClick={submitToPlay}
          className="w-full py-3 rounded-2xl text-sm font-bold min-h-11"
          style={{ background: 'var(--accent)', color: 'var(--accent-fg)', opacity: busy ? 0.6 : 1 }}
        >
          Rate on Google Play
        </button>
        <button
          type="button"
          disabled={busy}
          onClick={closeWithoutRating}
          className="w-full py-3 rounded-2xl text-sm font-semibold min-h-11"
          style={{ color: 'var(--muted)' }}
        >
          Not now
        </button>
      </div>
    </div>
  );
};
