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
  className = 'font-semibold text-slate-100',
  compactClassName = 'text-xs text-slate-400 font-medium ml-1.5',
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
      {compact && <span className={compactClassName}>({compact})</span>}
    </span>
  );
};
