import React from 'react';

interface BrandLogoProps {
  className?: string;
  showTagline?: boolean;
  size?: 'sm' | 'md' | 'lg';
  compact?: boolean;
  /** Splash stacks the mark, the name, then the tagline. Header stays in a row. */
  layout?: 'row' | 'stack';
}

const ICON = {
  sm: 'w-8 h-8',
  md: 'w-10 h-10',
  lg: 'w-14 h-14',
};

const NAME = {
  sm: 'text-lg',
  md: 'text-xl',
  lg: 'text-3xl',
};

/** Approved mark: navy tile, gold rupee, teal growth tip. */
export const BrandMark: React.FC<{ className?: string }> = ({ className = '' }) => (
  <svg className={className} viewBox="0 0 100 100" aria-hidden="true">
    <rect x="1.2" y="1.2" width="97.6" height="97.6" rx="22" fill="#07131f" stroke="#f5c14a" strokeWidth="2.4" />
    <rect x="27" y="31.2" width="46" height="5.4" rx="1.15" fill="#f5c14a" />
    <rect x="27" y="40.6" width="36" height="5.4" rx="1.15" fill="#f5c14a" />
    <path d="M36.6 23.2h6.6V56.5h-6.6z" fill="#f5c14a" />
    <path d="M36.6 54H43.2L62.4 74.6H55.8z" fill="#f5c14a" />
    <path d="M50.06 67.42L55.8 74.6h6.6l-5.74-7.18z" fill="#2dd4bf" />
  </svg>
);

/** One lockup for the header, the unlock screen, and onboarding. */
export const BrandLogo: React.FC<BrandLogoProps> = ({
  className = '',
  showTagline = true,
  size = 'md',
  compact = false,
  layout = 'row',
}) => {
  const stack = layout === 'stack';
  return (
    <div className={`${stack ? 'flex flex-col items-center text-center gap-3' : 'flex items-center gap-2 md:gap-3'} ${className}`}>
      <BrandMark className={`${ICON[size]} flex-shrink-0`} />
      <div className={compact ? 'hidden md:block' : undefined}>
        <div className={`font-bold ${NAME[size]} tracking-tight leading-none`}>
          <span style={{ color: 'var(--text)' }}>Grow</span>
          <span style={{ color: 'var(--accent)', borderBottom: '2px solid var(--accent)' }}>Ur</span>
          <span style={{ color: 'var(--accent-2)' }}>Dhan</span>
        </div>
        {showTagline && (
          <p className={`${stack ? 'mt-2' : 'mt-1'} text-[11px]`} style={{ color: 'var(--muted)' }}>
            Private Indian Family Wealth & Records
          </p>
        )}
      </div>
    </div>
  );
};
