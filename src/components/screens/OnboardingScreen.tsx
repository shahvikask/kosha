import React, { useState } from 'react';
import { Shield, PhoneOff, Lock, FileText, Users, IndianRupee, ChevronRight } from 'lucide-react';
import { ONBOARDING_SLIDES, markOnboardingSeen, OnboardingSlide } from '../../lib/onboarding';
import { BrandLogo } from '../common/BrandLogo';

const ICONS: Record<OnboardingSlide['icon'], typeof Shield> = {
  shield: Shield,
  phone: PhoneOff,
  lock: Lock,
  file: FileText,
  users: Users,
  rupee: IndianRupee,
};

interface OnboardingScreenProps {
  onDone: () => void;
}

export const OnboardingScreen: React.FC<OnboardingScreenProps> = ({ onDone }) => {
  const [i, setI] = useState(0);
  const slide = ONBOARDING_SLIDES[i];
  const Icon = ICONS[slide.icon];
  const last = i === ONBOARDING_SLIDES.length - 1;

  const next = () => {
    if (last) {
      markOnboardingSeen();
      onDone();
      return;
    }
    setI(n => n + 1);
  };

  return (
    <div
      className="onboard min-h-[100dvh] flex flex-col relative overflow-hidden select-none"
      style={{ background: 'var(--app)', color: 'var(--text)', touchAction: 'manipulation' }}
      onClick={next}
      role="button"
      tabIndex={0}
      onKeyDown={e => {
        if (e.key === 'Enter' || e.key === ' ') next();
      }}
    >
      <div className="flex-1 grid grid-cols-2 min-h-0 relative">
        <article className="px-3 sm:px-10 pt-12 pb-40 flex flex-col justify-center gap-3 sm:gap-4">
          <p className="text-[10px] font-bold uppercase tracking-[0.22em]" style={{ color: 'var(--muted)' }}>
            English
          </p>
          <div
            className="w-12 h-12 rounded-2xl flex items-center justify-center"
            style={{ background: 'color-mix(in srgb, var(--accent) 16%, transparent)', color: 'var(--accent)' }}
          >
            <Icon className="w-6 h-6" />
          </div>
          <h1 className="text-[1.15rem] sm:text-3xl font-semibold leading-snug tracking-tight">{slide.enTitle}</h1>
          <p className="text-[13px] sm:text-base leading-relaxed" style={{ color: 'var(--muted)' }}>
            {slide.enBody}
          </p>
        </article>

        <article className="px-3 sm:px-10 pt-12 pb-40 flex flex-col justify-center gap-3 sm:gap-4" lang="hi">
          <p className="text-[10px] font-bold uppercase tracking-[0.22em]" style={{ color: 'var(--muted)' }}>
            हिन्दी
          </p>
          <div
            className="w-12 h-12 rounded-2xl flex items-center justify-center"
            style={{ background: 'color-mix(in srgb, var(--accent-2) 16%, transparent)', color: 'var(--accent-2)' }}
          >
            <Icon className="w-6 h-6" />
          </div>
          <h1 className="text-[1.15rem] sm:text-3xl font-semibold leading-snug tracking-tight font-[Noto_Sans_Devanagari,system-ui,sans-serif]">
            {slide.hiTitle}
          </h1>
          <p className="text-[13px] sm:text-base leading-relaxed font-[Noto_Sans_Devanagari,system-ui,sans-serif]" style={{ color: 'var(--muted)' }}>
            {slide.hiBody}
          </p>
        </article>

        <div className="onboard-spine" aria-hidden="true" />
      </div>

      <div className="absolute bottom-0 left-0 right-0 flex flex-col items-center pb-[max(1.1rem,env(safe-area-inset-bottom))] pt-4 pointer-events-none">
        <p className="text-[11px] mb-3" style={{ color: 'var(--subtle)' }}>
          {last ? 'Tap to create your vault · टैप करें' : 'Tap for the next note · अगली बात'}
          <ChevronRight className="inline w-3 h-3 ml-0.5" />
        </p>
        <div className="flex gap-1.5 mb-4">
          {ONBOARDING_SLIDES.map((s, idx) => (
            <span
              key={s.id}
              className="h-1 rounded-full"
              style={{
                width: idx === i ? 22 : 8,
                background: idx === i ? 'var(--accent)' : 'var(--border)',
              }}
            />
          ))}
        </div>
        <BrandLogo size="lg" showTagline={false} />
      </div>
    </div>
  );
};
