import React from 'react';
import { useKosha } from '../../context/KoshaContext';
import { MoneyEngine } from '../../lib/money';

interface PrivacyValueProps {
  minor: bigint | number | string;
  showCompact?: boolean;
  className?: string;
  compactClassName?: string;
  includePaise?: boolean;
  prefix?: string;
}

export const PrivacyValue: React.FC<PrivacyValueProps> = ({
  minor,
  showCompact = false,
  className = 'font-semibold',
  compactClassName = 'text-xs font-medium ml-1.5',
  includePaise = true,
  prefix = '',
}) => {
  const { isPrivacyMasked } = useKosha();

  let bigMinor: bigint;
  if (typeof minor === 'string') {
    bigMinor = BigInt(minor || '0');
  } else if (typeof minor === 'number') {
    bigMinor = BigInt(Math.round(minor));
  } else {
    bigMinor = minor;
  }

  if (isPrivacyMasked) {
    return (
      <span className={`tracking-wider select-none font-mono ${className}`}>
        {prefix}₹ ••••••••
      </span>
    );
  }

  const formatted = MoneyEngine.formatRupees(bigMinor, includePaise);
  const compact = showCompact ? MoneyEngine.formatCompact(bigMinor) : null;

  return (
    <span className="inline-flex items-baseline flex-wrap">
      <span className={className}>
        {prefix}{formatted}
      </span>
      {compact && (
        <span className={compactClassName} style={{ color: 'var(--muted)' }}>
          ({compact})
        </span>
      )}
    </span>
  );
};

export const PrivacyDelta: React.FC<{
  minor: bigint;
  className?: string;
}> = ({ minor, className = 'font-semibold' }) => {
  const { isPrivacyMasked } = useKosha();
  const pos = minor >= 0n;
  if (isPrivacyMasked) {
    return <span className={`select-none font-mono ${className}`}>₹ •••••</span>;
  }
  const abs = pos ? minor : -minor;
  return (
    <span className={className} style={{ color: pos ? 'var(--good)' : 'var(--bad)' }}>
      {pos ? '+' : '−'}
      {MoneyEngine.formatRupees(abs, false)}
    </span>
  );
};
