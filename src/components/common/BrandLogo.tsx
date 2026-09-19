import React from 'react';

interface BrandLogoProps {
  className?: string;
  showTagline?: boolean;
  size?: 'sm' | 'md' | 'lg';
}

export const BrandLogo: React.FC<BrandLogoProps> = ({
  className = '',
  showTagline = true,
  size = 'md',
}) => {
  const iconSizes = {
    sm: 'w-8 h-8 rounded-lg',
    md: 'w-10 h-10 rounded-xl',
    lg: 'w-12 h-12 rounded-2xl',
  };

  const textSizes = {
    sm: 'text-lg',
    md: 'text-xl',
    lg: 'text-2xl',
  };

  return (
    <div className={`flex items-center gap-3 group cursor-pointer ${className}`}>
      {/* App Icon Mark: Fused U and Indian Rupee Symbol (₹) */}
      <div className={`${iconSizes[size]} bg-gradient-to-br from-emerald-500/20 via-slate-900 to-amber-500/30 border border-amber-500/40 flex items-center justify-center shadow-lg group-hover:border-amber-400 group-hover:shadow-amber-500/20 transition-all flex-shrink-0 relative overflow-hidden`}>
        {/* Subtle background glow */}
        <div className="absolute inset-0 bg-amber-500/10 blur-sm pointer-events-none" />
        
        {/* Custom SVG Fusing U and Rupee Symbol ₹ */}
        <svg
          className="w-6 h-6 relative z-10 text-amber-400 filter drop-shadow(0 0 6px rgba(245, 158, 11, 0.5))"
          viewBox="0 0 24 24"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
        >
          {/* U bowl structure */}
          <path
            d="M5 4V12C5 15.3137 7.68629 18 11 18C14.3137 18 17 15.3137 17 12V4"
            stroke="url(#rupeeU वालाGradient)"
            strokeWidth="2.2"
            strokeLinecap="round"
          />
          {/* Rupee horizontal parallel bars */}
          <path d="M5 8H14" stroke="#F59E0B" strokeWidth="2" strokeLinecap="round" />
          <path d="M5 4H14" stroke="#F59E0B" strokeWidth="2" strokeLinecap="round" />
          {/* Rupee diagonal leg extending down */}
          <path d="M11 12L18 20" stroke="#F59E0B" strokeWidth="2.4" strokeLinecap="round" />
          
          <defs>
            <linearGradient id="rupeeU वालाGradient" x1="5" y1="4" x2="18" y2="20" gradientUnits="userSpaceOnUse">
              <stop stopColor="#10B981" />
              <stop offset="0.5" stopColor="#F59E0B" />
              <stop offset="1" stopColor="#FBBF24" />
            </linearGradient>
          </defs>
        </svg>
      </div>

      {/* Brand Title with Prominent Integrated U-Rupee Symbol */}
      <div>
        <div className="flex items-center">
          <span className={`font-bold ${textSizes[size]} tracking-tight font-serif`} style={{ color: 'var(--text)' }}>
            Grow<span style={{ color: 'var(--accent)' }}>Ur</span><span style={{ color: 'var(--accent-2)' }}>Dhan</span>
          </span>
        </div>
        {showTagline && (
          <p className="text-[11px] hidden sm:block" style={{ color: 'var(--muted)' }}>Private Indian Family Wealth & Records</p>
        )}
      </div>
    </div>
  );
};
