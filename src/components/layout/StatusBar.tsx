import React, { useMemo } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { formatStatementDate } from '../../lib/casPython';
import { isOpenPosition } from '../../lib/holdingView';
import { Shield, CheckCircle2 } from 'lucide-react';

export const StatusBar: React.FC = () => {
  const { holdings, lastStatementAsOf, pricesAsOf, derived } = useKosha();
  const openCount = useMemo(
    () => holdings.filter(h => isOpenPosition(h, derived)).length,
    [holdings, derived]
  );

  return (
    <footer
      className="h-10 border-t text-[11px] sm:text-xs flex items-center justify-between px-4 sm:px-6 fixed bottom-0 left-0 right-0 z-40"
      style={{ background: 'var(--chrome)', borderColor: 'var(--border)', color: 'var(--muted)' }}
    >
      <div className="flex items-center gap-2 truncate">
        <Shield className="w-3.5 h-3.5 flex-shrink-0" style={{ color: 'var(--good)' }} />
        <span className="truncate">Money stays on this device</span>
      </div>
      <div className="flex items-center gap-3 flex-shrink-0">
        <span>
          {lastStatementAsOf
            ? `Statement as of ${formatStatementDate(lastStatementAsOf)}`
            : 'No statement imported yet'}
        </span>
        {pricesAsOf && (
          <span className="hidden sm:inline">Prices as on {formatStatementDate(pricesAsOf)}</span>
        )}
        <span className="hidden sm:inline">{openCount} holdings</span>
        <span className="flex items-center gap-1 font-semibold" style={{ color: 'var(--good)' }}>
          <CheckCircle2 className="w-3.5 h-3.5" />
          Vault OK
        </span>
      </div>
    </footer>
  );
};