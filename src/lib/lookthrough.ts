/**
 * Look-through and tree building. Implements SRS-UX-047/048 and Annex C §C5.1.
 *
 * A hybrid holding is LISTED ONCE under its dominant exposure and COUNTED
 * PROPORTIONALLY across exposure nodes. It is never duplicated: duplicating
 * double-counts, and assigning it wholly to one node hides part of the money.
 */
import { Holding } from '../types';
import { ExposureNode, Wrapper, Placement, classifyHolding, EXPOSURES } from './taxonomy';

export interface SplitSlice {
  exposure: ExposureNode;
  /** basis points, 10000 = 100%. Integer, so splits always sum exactly. */
  basisPoints: number;
}

export interface ResolvedSplit {
  slices: SplitSlice[];
  /** true when derived from a category default rather than disclosed data */
  estimated: boolean;
  source: string;
}

/**
 * SEBI-mandated minimum allocations, used only when actual disclosed
 * holdings are unavailable. Marked estimated so the interface can say so.
 */
const CATEGORY_DEFAULTS: { match: string[]; slices: SplitSlice[]; label: string }[] = [
  { match: ['aggressive hybrid'], label: 'Aggressive hybrid (SEBI minimum 65% equity)',
    slices: [{ exposure: 'EQUITY', basisPoints: 6500 }, { exposure: 'DEBT', basisPoints: 3500 }] },
  { match: ['conservative hybrid'], label: 'Conservative hybrid (SEBI minimum 75% debt)',
    slices: [{ exposure: 'DEBT', basisPoints: 7500 }, { exposure: 'EQUITY', basisPoints: 2500 }] },
  { match: ['balanced advantage', 'dynamic asset allocation'], label: 'Balanced advantage (assumed 65/35)',
    slices: [{ exposure: 'EQUITY', basisPoints: 6500 }, { exposure: 'DEBT', basisPoints: 3500 }] },
  { match: ['balanced hybrid'], label: 'Balanced hybrid (assumed 50/50)',
    slices: [{ exposure: 'EQUITY', basisPoints: 5000 }, { exposure: 'DEBT', basisPoints: 5000 }] },
  { match: ['multi asset', 'multi-asset'], label: 'Multi asset (assumed 50/30/20)',
    slices: [{ exposure: 'EQUITY', basisPoints: 5000 }, { exposure: 'DEBT', basisPoints: 3000 },
             { exposure: 'GOLD', basisPoints: 2000 }] },
  { match: ['equity savings'], label: 'Equity savings (assumed 35/65)',
    slices: [{ exposure: 'EQUITY', basisPoints: 3500 }, { exposure: 'DEBT', basisPoints: 6500 }] },
  { match: ['nps tier', 'nps pension'], label: 'NPS (assumed 50/30/20 E/C/G)',
    slices: [{ exposure: 'EQUITY', basisPoints: 5000 }, { exposure: 'DEBT', basisPoints: 5000 }] },
  { match: ['ulip'], label: 'ULIP (assumed 60/40)',
    slices: [{ exposure: 'EQUITY', basisPoints: 6000 }, { exposure: 'DEBT', basisPoints: 4000 }] },
];

/**
 * Resolve how a holding's value is spread across exposure nodes.
 * Order of precedence: user-supplied split, then category default,
 * then the whole value to its single placed exposure.
 */
export function resolveSplit(h: Holding, placement: Placement): ResolvedSplit {
  if (h.allocationSplit && h.allocationSplit.length > 0) {
    const slices = h.allocationSplit.map(s => ({
      exposure: s.exposure as ExposureNode,
      basisPoints: Math.round(s.percent * 100),
    }));
    const total = slices.reduce((a, s) => a + s.basisPoints, 0);
    if (total > 0) {
      slices[0].basisPoints += 10000 - total; // force exact 100%
      return { slices, estimated: false, source: 'You set this split' };
    }
  }

  const text = `${h.name} ${h.subCategory}`.toLowerCase();
  for (const d of CATEGORY_DEFAULTS) {
    if (d.match.some(m => text.includes(m))) {
      return { slices: d.slices, estimated: true, source: d.label };
    }
  }

  return {
    slices: [{ exposure: placement.exposure, basisPoints: 10000 }],
    estimated: false,
    source: 'Single asset class',
  };
}

export function isHybrid(split: ResolvedSplit): boolean {
  return split.slices.length > 1;
}

/**
 * Apportion a value across slices without losing a paisa.
 * Any rounding remainder goes to the largest slice.
 */
export function apportion(valueMinor: bigint, split: ResolvedSplit): Map<ExposureNode, bigint> {
  const out = new Map<ExposureNode, bigint>();
  if (split.slices.length === 1) {
    out.set(split.slices[0].exposure, valueMinor);
    return out;
  }
  let assigned = 0n;
  let largest = split.slices[0];
  for (const s of split.slices) {
    if (s.basisPoints > largest.basisPoints) largest = s;
    const part = (valueMinor * BigInt(s.basisPoints)) / 10000n;
    out.set(s.exposure, (out.get(s.exposure) ?? 0n) + part);
    assigned += part;
  }
  const remainder = valueMinor - assigned;
  if (remainder !== 0n) {
    out.set(largest.exposure, (out.get(largest.exposure) ?? 0n) + remainder);
  }
  return out;
}

export interface HoldingPlacement {
  holding: Holding;
  placement: Placement;
  split: ResolvedSplit;
  valueMinor: bigint;
  /** value attributed to the node this row is listed under */
  dominantExposure: ExposureNode;
}

export interface TreeL2 {
  id: string;
  label: string;
  valueMinor: bigint;
  rows: HoldingPlacement[];
  wrappers: Wrapper[];
}

export interface TreeL1 {
  id: ExposureNode;
  label: string;
  order: number;
  valueMinor: bigint;
  l2: TreeL2[];
  hasHoldings: boolean;
}

/**
 * Build the My Money tree. Every L1 and L2 carries its total so that
 * navigation also serves as the allocation view (SRS-UX-049).
 *
 * valueOf must return the market value of a holding in minor units.
 */
export function buildTree(
  holdings: Holding[],
  valueOf: (h: Holding) => bigint
): { tree: TreeL1[]; exposureTotals: Map<ExposureNode, bigint>; placements: HoldingPlacement[] } {
  const exposureTotals = new Map<ExposureNode, bigint>();
  const placements: HoldingPlacement[] = [];

  for (const h of holdings) {
    const placement = classifyHolding(h);
    const split = resolveSplit(h, placement);
    const valueMinor = valueOf(h);
    const dominant = split.slices.reduce((a, b) => (b.basisPoints > a.basisPoints ? b : a)).exposure;

    placements.push({ holding: h, placement, split, valueMinor, dominantExposure: dominant });

    // Totals are apportioned. The ROW is listed once, under the dominant node.
    apportion(valueMinor, split).forEach((v, node) => {
      exposureTotals.set(node, (exposureTotals.get(node) ?? 0n) + v);
    });
  }

  const tree: TreeL1[] = EXPOSURES.map(meta => {
    const l2s: TreeL2[] = meta.l2.map(n => {
      const rows = placements.filter(
        p => p.dominantExposure === meta.id && p.placement.l2 === n.id
      );
      const wrappers = Array.from(new Set(rows.map(r => r.placement.wrapper)));
      const val = rows.reduce((s, r) => s + r.valueMinor, 0n);
      return { id: n.id, label: n.label, valueMinor: val, rows, wrappers };
    });
    const total = exposureTotals.get(meta.id) ?? 0n;
    return {
      id: meta.id,
      label: meta.label,
      order: meta.order,
      valueMinor: total,
      l2: l2s,
      hasHoldings: l2s.some(x => x.rows.length > 0),
    };
  });

  return { tree, exposureTotals, placements };
}

/** Wrappers actually present, in canonical order. Empty ones are hidden (SRS-UX-046). */
export function activeWrappers(rows: HoldingPlacement[], order: Wrapper[]): Wrapper[] {
  const present = new Set(rows.map(r => r.placement.wrapper));
  return order.filter(w => present.has(w));
}
