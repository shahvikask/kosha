import React from 'react';

interface BrandLogoProps {
  className?: string;
  showTagline?: boolean;
  size?: 'sm' | 'md' | 'lg';
}

export const BrandLogo: React.FC<BrandLogoProps> = ({
  className = '',
  showTagline = false,
  size = 'md',
}) => {
  const mark = { sm: 'h-8 w-8', md: 'h-10 w-10', lg: 'h-12 w-12' }[size];
  const word = { sm: 'text-lg', md: 'text-[1.35rem]', lg: 'text-2xl' }[size];
  const tag = { sm: 'text-[9px]', md: 'text-[10px]', lg: 'text-xs' }[size];

  return (
    <div className={`flex items-center gap-2.5 min-w-0 ${className}`}>
      <img
        src="/brand/growurdhan-mark.png"
        alt=""
        className={`${mark} object-contain flex-shrink-0`}
      />
      <div className="min-w-0 leading-none">
        <div
          className={`font-brand font-bold ${word} tracking-tight truncate`}
          style={{ color: 'var(--brand)' }}
        >
          Grow<span style={{ color: 'var(--brand-gold)' }}>Ur</span>Dhan
        </div>
        {showTagline && (
          <p
            className={`${tag} mt-1 tracking-wide hidden sm:block`}
            style={{ color: 'var(--muted)' }}
          >
            Private Indian Family Wealth & Records
          </p>
        )}
      </div>
    </div>
  );
};
