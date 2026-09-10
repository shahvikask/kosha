import React from 'react';

interface BrandLogoProps {
  className?: string;
  showTagline?: boolean;
  size?: 'sm' | 'md' | 'lg';
}

export const BrandLogo: React.FC<BrandLogoProps> = ({
  className = '',
  size = 'md',
}) => {
  const height = { sm: 'h-8', md: 'h-10', lg: 'h-12' }[size];

  return (
    <div
      className={`flex items-center rounded-xl px-1.5 py-0.5 ${className}`}
      style={{ background: '#fffdf8' }}
    >
      <picture>
        <source srcSet="/brand/growurdhan-logo.webp" type="image/webp" />
        <img
          src="/brand/growurdhan-logo.png"
          alt="GrowUrDhan — Private Indian Family Wealth & Records"
          className={`${height} w-auto max-w-[min(52vw,280px)] object-contain object-left`}
        />
      </picture>
    </div>
  );
};
