import React from 'react';
import { PrivacyValue } from '../common/PrivacyValue';
import { GrowthSlice, MonthPoint, IncomeYear } from '../../lib/growth';
import { MoneyEngine } from '../../lib/money';
import { StatementStep } from '../../lib/homeDashboard';
import { formatStatementDateShort } from '../../lib/casPython';

export const Donut: React.FC<{
  slices: GrowthSlice[];
  selectedId?: string | null;
  onSelect: (id: string) => void;
  center?: React.ReactNode;
}> = ({ slices, selectedId, onSelect, center }) => {
  const total = slices.reduce((s, x) => s + x.valueMinor, 0n);
  const r = 54;
  const c = 2 * Math.PI * r;
  let acc = 0;
  const size = 148;

  return (
    <div className="flex items-center gap-4 min-w-0">
      <div className="relative shrink-0" style={{ width: size, height: size }}>
        <svg viewBox="0 0 148 148" className="w-full h-full -rotate-90">
          <circle cx="74" cy="74" r={r} fill="none" stroke="var(--border)" strokeWidth="16" />
          {slices.map(sl => {
            const frac = total > 0n ? Number(sl.valueMinor) / Number(total) : 0;
            const dash = frac * c;
            const gap = c - dash;
            const offset = -acc * c;
            acc += frac;
            const dim = selectedId && selectedId !== sl.id;
            return (
              <circle
                key={sl.id}
                cx="74"
                cy="74"
                r={r}
                fill="none"
                stroke={sl.color}
                strokeWidth="16"
                strokeDasharray={`${dash} ${gap}`}
                strokeDashoffset={offset}
                strokeLinecap="butt"
                opacity={dim ? 0.28 : 1}
                className="cursor-pointer"
                onClick={() => onSelect(sl.id)}
              >
                <title>{sl.label}</title>
              </circle>
            );
          })}
        </svg>
        <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none px-6 text-center">
          {center}
        </div>
      </div>
      <ul className="min-w-0 flex-1 space-y-1.5">
        {slices.map(sl => {
          const pct = total > 0n ? (Number(sl.valueMinor) * 100) / Number(total) : 0;
          const on = selectedId === sl.id;
          return (
            <li key={sl.id}>
              <button
                type="button"
                onClick={() => onSelect(sl.id)}
                className="w-full flex items-center gap-2 text-left rounded-lg px-1.5 py-0.5"
                style={{ background: on ? 'var(--chip)' : 'transparent' }}
              >
                <span className="w-2.5 h-2.5 rounded-sm shrink-0" style={{ background: sl.color }} />
                <span className="text-xs truncate flex-1">{sl.label}</span>
                <span className="text-[11px] tabular-nums shrink-0" style={{ color: 'var(--muted)' }}>
                  {pct.toFixed(0)}%
                </span>
              </button>
            </li>
          );
        })}
      </ul>
    </div>
  );
};

export const HBars: React.FC<{
  slices: GrowthSlice[];
  selectedId?: string | null;
  onSelect: (id: string) => void;
}> = ({ slices, selectedId, onSelect }) => {
  const max = slices.reduce((s, x) => (x.valueMinor > s ? x.valueMinor : s), 0n);
  return (
    <div className="space-y-2.5">
      {slices.map(sl => {
        const pct = max > 0n ? Number((sl.valueMinor * 10000n) / max) / 100 : 0;
        const on = selectedId === sl.id;
        return (
          <button
            key={sl.id}
            type="button"
            onClick={() => onSelect(sl.id)}
            className="w-full text-left"
          >
            <div className="flex justify-between text-xs mb-1 gap-2">
              <span className="truncate font-medium">{sl.label}</span>
              <PrivacyValue minor={sl.valueMinor} includePaise={false} className="text-xs font-semibold" />
            </div>
            <div className="h-2.5 rounded-full overflow-hidden" style={{ background: 'var(--input)' }}>
              <div
                className="h-full rounded-full transition-all"
                style={{
                  width: `${Math.max(pct, sl.valueMinor > 0n ? 1.5 : 0)}%`,
                  background: sl.color,
                  opacity: selectedId && !on ? 0.35 : 1,
                }}
              />
            </div>
          </button>
        );
      })}
    </div>
  );
};

export const TimeStepChart: React.FC<{
  steps: StatementStep[];
  color: string;
  valueOf: (s: StatementStep) => bigint;
  empty: string;
  id: string;
}> = ({ steps, color, valueOf, empty, id }) => {
  const drawn = steps.filter(s => valueOf(s) > 0n || s.isLivePad);
  if (drawn.length === 0 || drawn.every(s => valueOf(s) <= 0n)) {
    return (
      <p className="text-xs py-8 text-center" style={{ color: 'var(--muted)' }}>{empty}</p>
    );
  }
  const w = 640;
  const h = 196;
  const pad = { l: 12, r: 14, t: 18, b: 30 };
  const vals = drawn.map(s => Number(valueOf(s)));
  const rawMin = Math.min(...vals);
  const rawMax = Math.max(...vals, 1);
  const spanV = Math.max(rawMax - rawMin, rawMax * 0.08, 1);
  const yMin = rawMin <= 0 ? 0 : Math.max(0, rawMin - spanV * 0.12);
  const yMax = rawMax + spanV * 0.08;
  const firstMs = Date.parse(drawn[0].date);
  const lastMs = Date.parse(drawn[drawn.length - 1].date);
  const spanT = Math.max(1, lastMs - firstMs);
  const xOf = (iso: string) => {
    if (drawn.length === 1) return pad.l + (w - pad.l - pad.r) / 2;
    const t = Date.parse(iso);
    const f = Number.isFinite(t) ? (t - firstMs) / spanT : 0;
    return pad.l + f * (w - pad.l - pad.r);
  };
  const yOf = (v: bigint) => {
    const n = Number(v);
    const f = (n - yMin) / (yMax - yMin || 1);
    return pad.t + (1 - f) * (h - pad.t - pad.b);
  };
  const pts = drawn.map(p => ({ x: xOf(p.date), y: yOf(valueOf(p)), p }));
  let d = `M ${pts[0].x.toFixed(1)} ${pts[0].y.toFixed(1)}`;
  for (let i = 1; i < pts.length; i++) {
    d += ` H ${pts[i].x.toFixed(1)} V ${pts[i].y.toFixed(1)}`;
  }
  const last = pts[pts.length - 1];
  const area = `${d} L ${last.x.toFixed(1)} ${h - pad.b} L ${pts[0].x.toFixed(1)} ${h - pad.b} Z`;
  const marks = pts.filter(pt => !pt.p.isLivePad);
  const tickIdx = [0];
  if (marks.length > 2) tickIdx.push(Math.floor((marks.length - 1) / 2));
  if (marks.length > 1) tickIdx.push(marks.length - 1);
  const ticks = [...new Set(tickIdx)].map(i => marks[i]).filter(Boolean);
  const grid = [0.25, 0.5, 0.75];

  return (
    <svg viewBox={`0 0 ${w} ${h}`} className="w-full h-auto" role="img">
      <defs>
        <linearGradient id={`fill-${id}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity="0.28" />
          <stop offset="100%" stopColor={color} stopOpacity="0.03" />
        </linearGradient>
      </defs>
      {grid.map(g => {
        const y = pad.t + g * (h - pad.t - pad.b);
        return (
          <line
            key={g}
            x1={pad.l}
            x2={w - pad.r}
            y1={y}
            y2={y}
            stroke="currentColor"
            strokeOpacity="0.08"
            strokeWidth="1"
          />
        );
      })}
      <line
        x1={pad.l}
        x2={w - pad.r}
        y1={h - pad.b}
        y2={h - pad.b}
        stroke="currentColor"
        strokeOpacity="0.16"
        strokeWidth="1"
      />
      <path d={area} fill={`url(#fill-${id})`} />
      <path d={d} fill="none" stroke={color} strokeWidth="2.4" strokeLinejoin="miter" />
      {marks.map(pt => (
        <g key={pt.p.date}>
          <circle cx={pt.x} cy={pt.y} r="5" fill="var(--card)" stroke={color} strokeWidth="2">
            <title>{formatStatementDateShort(pt.p.date)}</title>
          </circle>
        </g>
      ))}
      {pts.some(pt => pt.p.isLivePad) && (
        <circle
          cx={last.x}
          cy={last.y}
          r="3"
          fill={color}
          opacity="0.55"
        />
      )}
      {ticks.map((pt, i) => (
        <text
          key={`${pt.p.date}-t`}
          x={pt.x}
          y={h - 8}
          textAnchor={i === 0 ? 'start' : i === ticks.length - 1 ? 'end' : 'middle'}
          fontSize="11"
          fill="currentColor"
          opacity="0.5"
        >
          {formatStatementDateShort(pt.p.date)}
        </text>
      ))}
    </svg>
  );
};

export const StepFlow: React.FC<{ steps: StatementStep[] }> = ({ steps }) => (
  <TimeStepChart
    id="assets"
    steps={steps}
    color="var(--accent)"
    valueOf={s => s.valueMinor}
    empty="Read a later month's statement to see how reported value moved."
  />
);

export const AreaFlow: React.FC<{ months: MonthPoint[] }> = ({ months }) => {
  if (months.length < 2) {
    return (
      <p className="text-xs py-8 text-center" style={{ color: 'var(--muted)' }}>
        More months of records will draw this line.
      </p>
    );
  }
  const w = 640;
  const h = 180;
  const pad = { l: 8, r: 8, t: 12, b: 28 };
  const max = months.reduce((s, p) => {
    const a = p.investedMinor > p.incomeMinor ? p.investedMinor : p.incomeMinor;
    return a > s ? a : s;
  }, 1n);
  const maxN = Number(max);
  const n = months.length - 1;
  const x = (i: number) => pad.l + (i / n) * (w - pad.l - pad.r);
  const y = (v: bigint) => pad.t + (1 - Number(v) / maxN) * (h - pad.t - pad.b);
  const line = (key: 'investedMinor' | 'incomeMinor') =>
    months.map((p, i) => `${i === 0 ? 'M' : 'L'} ${x(i).toFixed(1)} ${y(p[key]).toFixed(1)}`).join(' ');
  const area =
    `${line('investedMinor')} L ${x(n).toFixed(1)} ${h - pad.b} L ${x(0).toFixed(1)} ${h - pad.b} Z`;
  const ticks = [0, Math.floor(n / 2), n];
  return (
    <svg viewBox={`0 0 ${w} ${h}`} className="w-full h-auto">
      <path d={area} fill="#f59e0b" opacity="0.18" />
      <path d={line('investedMinor')} fill="none" stroke="#f59e0b" strokeWidth="2.5" />
      <path d={line('incomeMinor')} fill="none" stroke="#10b981" strokeWidth="2.5" />
      {ticks.map(i => (
        <text
          key={months[i].month}
          x={x(i)}
          y={h - 8}
          textAnchor={i === 0 ? 'start' : i === n ? 'end' : 'middle'}
          fontSize="11"
          fill="currentColor"
          opacity="0.55"
        >
          {months[i].month}
        </text>
      ))}
    </svg>
  );
};

export const StackedFy: React.FC<{
  years: IncomeYear[];
  selectedFy?: string | null;
  onSelect: (fy: string) => void;
}> = ({ years, selectedFy, onSelect }) => {
  if (years.length === 0) {
    return (
      <p className="text-xs py-6 text-center" style={{ color: 'var(--muted)' }}>
        Dividends and interest appear here after you record them, or when a statement includes them.
      </p>
    );
  }
  const max = years.reduce((s, y) => (y.totalMinor > s ? y.totalMinor : s), 1n);
  return (
    <div className="flex items-end gap-3 h-40">
      {years.map(y => {
        const h = Number((y.totalMinor * 10000n) / max) / 100;
        const on = selectedFy === y.fy;
        const d = Number(y.dividendMinor);
        const i = Number(y.interestMinor);
        const r = Number(y.rentalMinor);
        const t = d + i + r || 1;
        return (
          <button
            key={y.fy}
            type="button"
            onClick={() => onSelect(y.fy)}
            className="flex-1 min-w-0 flex flex-col items-center gap-1 h-full justify-end"
            style={{ opacity: selectedFy && !on ? 0.4 : 1 }}
          >
            <div
              className="w-full max-w-[3rem] rounded-t-lg overflow-hidden flex flex-col-reverse"
              style={{ height: `${Math.max(h, 6)}%` }}
            >
              <div style={{ height: `${(d / t) * 100}%`, background: '#3b82f6' }} />
              <div style={{ height: `${(i / t) * 100}%`, background: '#10b981' }} />
              <div style={{ height: `${(r / t) * 100}%`, background: '#8b5cf6' }} />
            </div>
            <span className="text-[10px] truncate w-full text-center" style={{ color: 'var(--muted)' }}>
              FY {y.fy}
            </span>
          </button>
        );
      })}
    </div>
  );
};

export const SplitBar: React.FC<{
  leftLabel: string;
  rightLabel: string;
  leftMinor: bigint;
  rightMinor: bigint;
  leftColor: string;
  rightColor: string;
}> = ({ leftLabel, rightLabel, leftMinor, rightMinor, leftColor, rightColor }) => {
  const tot = leftMinor + rightMinor;
  const lp = tot > 0n ? Number((leftMinor * 10000n) / tot) / 100 : 50;
  return (
    <div>
      <div className="flex justify-between text-xs mb-2">
        <span>
          {leftLabel}{' '}
          <PrivacyValue minor={leftMinor} includePaise={false} className="text-xs font-semibold" />
        </span>
        <span>
          {rightLabel}{' '}
          <PrivacyValue minor={rightMinor} includePaise={false} className="text-xs font-semibold" />
        </span>
      </div>
      <div className="h-3 rounded-full overflow-hidden flex" style={{ background: 'var(--input)' }}>
        <div style={{ width: `${lp}%`, background: leftColor }} />
        <div style={{ width: `${100 - lp}%`, background: rightColor }} />
      </div>
    </div>
  );
};

export function rupeeShort(minor: bigint): string {
  return MoneyEngine.formatCompact(minor);
}
