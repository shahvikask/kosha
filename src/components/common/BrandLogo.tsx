import React from 'react';

interface BrandLogoProps {
  className?: string;
  showTagline?: boolean;
  size?: 'sm' | 'md' | 'lg';
  compact?: boolean;
}

/** Rupee tile + GrowUrDhan wordmark. Grow / Ur / Dhan stay the brand colours. */
export const BrandLogo: React.FC<BrandLogoProps> = ({
  className = '',
  showTagline = true,
  size = 'md',
  compact = false,
}) => {
  const iconSizes = {
    sm: 'w-8 h-8',
    md: 'w-10 h-10',
    lg: 'w-12 h-12',
  };

  const textSizes = {
    sm: 'text-lg',
    md: 'text-xl',
    lg: 'text-2xl',
  };

  return (
    <div className={`flex items-center gap-2 md:gap-3 ${className}`}>
      <img
        src="/brand/mark.png"
        alt=""
        className={`${iconSizes[size]} object-contain flex-shrink-0`}
      />
      <div className={compact ? 'hidden md:block' : undefined}>
        <div className={`font-bold ${textSizes[size]} tracking-tight`}>
          <span style={{ color: 'var(--text)' }}>Grow</span>
          <span style={{ color: 'var(--accent)' }}>Ur</span>
          <span style={{ color: 'var(--accent-2)' }}>Dhan</span>
        </div>
        {showTagline && (
          <p className="text-[11px] hidden sm:block" style={{ color: 'var(--muted)' }}>Private Indian Family Wealth & Records</p>
        )}
      </div>
    </div>
  );
};
