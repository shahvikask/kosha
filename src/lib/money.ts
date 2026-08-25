/**
 * Money and quantity types and fixed-point arithmetic for Project Kosha.
 * Implements SRS-LED-007, SRS-UX-014, SRS-UX-015, SRS-UX-016 and Annex A §A2.3.
 * 
 * Scaled integer math only:
 * - Money: scaled by 10,000 (1 Rupee = 10,000 units, 1 Paisa = 100 units).
 * - Qty: scaled by 100,000,000 (8 decimal places for stocks, gold grams, mutual fund units).
 */

export const MONEY_SCALE = 10_000n;
export const QTY_SCALE = 100_000_000n;

export class MoneyEngine {
  /** Convert a normal Rupee number or string to scaled bigint */
  static fromRupees(rupees: number | string): bigint {
    if (typeof rupees === 'string') {
      const clean = rupees.replace(/,/g, '').trim();
      const num = parseFloat(clean);
      if (isNaN(num)) return 0n;
      return BigInt(Math.round(num * 10_000));
    }
    if (isNaN(rupees)) return 0n;
    return BigInt(Math.round(rupees * 10_000));
  }

  /** Convert scaled bigint, number, or string to floating Rupee number for charts or calculations */
  static toRupees(scaled: bigint | number | string): number {
    if (typeof scaled === 'string') {
      const clean = scaled.replace(/,/g, '').trim();
      if (!clean) return 0;
      try {
        return Number(BigInt(clean)) / 10_000;
      } catch {
        const num = parseFloat(clean);
        return isNaN(num) ? 0 : num / 10_000;
      }
    }
    const val = typeof scaled === 'bigint' ? Number(scaled) : scaled;
    return val / 10_000;
  }

  /** Convert scaled quantity (bigint, string, or number scaled by 10^8) to normal number */
  static toQty(scaled: bigint | number | string): number {
    if (typeof scaled === 'string') {
      const clean = scaled.replace(/,/g, '').trim();
      if (!clean) return 0;
      try {
        return Number(BigInt(clean)) / 100_000_000;
      } catch {
        const num = parseFloat(clean);
        return isNaN(num) ? 0 : (num > 10_000_000 ? num / 100_000_000 : num);
      }
    }
    const val = typeof scaled === 'bigint' ? Number(scaled) : scaled;
    return val / 100_000_000;
  }

  /** Convert normal quantity number to scaled bigint */
  static fromQty(units: number | string): bigint {
    if (typeof units === 'string') {
      const clean = units.replace(/,/g, '').trim();
      const num = parseFloat(clean);
      if (isNaN(num)) return 0n;
      return BigInt(Math.round(num * 100_000_000));
    }
    if (isNaN(units)) return 0n;
    return BigInt(Math.round(units * 100_000_000));
  }

  /**
   * Indian digit grouping for integer Rupee amounts.
   * e.g. 12345678 -> "1,23,45,678"
   * e.g. 123456 -> "1,23,456"
   * e.g. 1234 -> "1,234"
   */
  static groupIndian(n: number | bigint): string {
    const num = typeof n === 'bigint' ? Number(n) : n;
    const isNeg = num < 0;
    const absVal = Math.abs(Math.floor(num));
    const s = absVal.toString();

    if (s.length <= 3) {
      return (isNeg ? '-' : '') + s;
    }

    const lastThree = s.substring(s.length - 3);
    const otherNumbers = s.substring(0, s.length - 3);
    const formattedOther = otherNumbers.replace(/\B(?=(\d{2})+(?!\d))/g, ',');

    const res = formattedOther + ',' + lastThree;
    return isNeg ? '-' + res : res;
  }

  /**
   * Full standard Indian Rupee display string: "₹ 1,23,45,678.00"
   */
  static formatRupees(scaled: bigint | number | string, includePaise = true): string {
    let minor: bigint;
    if (typeof scaled === 'string') {
      const clean = scaled.replace(/,/g, '').trim();
      try {
        minor = BigInt(clean || '0');
      } catch {
        minor = BigInt(Math.round(parseFloat(clean || '0')));
      }
    } else if (typeof scaled === 'bigint') {
      minor = scaled;
    } else {
      minor = BigInt(Math.round(scaled));
    }

    const isNeg = minor < 0n;
    const absMinor = isNeg ? -minor : minor;

    const wholeRupees = absMinor / MONEY_SCALE;
    const paiseScaled = absMinor % MONEY_SCALE;
    const paise = paiseScaled / 100n;

    const grouped = this.groupIndian(wholeRupees);
    const sign = isNeg ? '-' : '';

    if (!includePaise) {
      return `₹ ${sign}${grouped}`;
    }

    const paiseStr = paise.toString().padStart(2, '0');
    return `₹ ${sign}${grouped}.${paiseStr}`;
  }

  /**
   * Compact Indian format: "2.47 crore", "18.60 lakh", "75,200"
   */
  static formatCompact(scaled: bigint | number | string): string {
    let minor: bigint;
    if (typeof scaled === 'string') {
      const clean = scaled.replace(/,/g, '').trim();
      try {
        minor = BigInt(clean || '0');
      } catch {
        minor = BigInt(Math.round(parseFloat(clean || '0')));
      }
    } else if (typeof scaled === 'bigint') {
      minor = scaled;
    } else {
      minor = BigInt(Math.round(scaled));
    }

    const rupees = Number(minor / MONEY_SCALE);
    const isNeg = rupees < 0;
    const abs = Math.abs(rupees);
    const sign = isNeg ? '-' : '';

    if (abs >= 10_000_000) {
      const cr = abs / 10_000_000;
      return `${sign}${cr.toFixed(2)} crore`;
    } else if (abs >= 100_000) {
      const lk = abs / 100_000;
      return `${sign}${lk.toFixed(2)} lakh`;
    } else {
      return `${sign}₹ ${this.groupIndian(abs)}`;
    }
  }

  /**
   * Accessible Change formatting with Arrow + Word + Amount + %
   * e.g., "▲ Up ₹ 45,200 (+1.25%)" or "▼ Down ₹ 12,300 (-0.45%)"
   */
  static formatChange(changeMinor: bigint | number | string, percent: number): {
    arrow: string;
    word: string;
    amountStr: string;
    percentStr: string;
    fullText: string;
    isPositive: boolean;
    isNegative: boolean;
    isZero: boolean;
  } {
    let minor: bigint;
    if (typeof changeMinor === 'string') {
      const clean = changeMinor.replace(/,/g, '').trim();
      try {
        minor = BigInt(clean || '0');
      } catch {
        minor = BigInt(Math.round(parseFloat(clean || '0')));
      }
    } else if (typeof changeMinor === 'bigint') {
      minor = changeMinor;
    } else {
      minor = BigInt(Math.round(changeMinor));
    }

    const isZero = minor === 0n;
    const isPositive = minor > 0n;
    const isNegative = minor < 0n;

    let arrow = '—';
    let word = 'Unchanged';
    if (isPositive) {
      arrow = '▲';
      word = 'Up';
    } else if (isNegative) {
      arrow = '▼';
      word = 'Down';
    }

    const absMinor = isNegative ? -minor : minor;
    const absPercent = Math.abs(percent);
    const amountStr = this.formatRupees(absMinor, false);
    const percentStr = `${isPositive ? '+' : isNegative ? '-' : ''}${absPercent.toFixed(2)}%`;

    const fullText = isZero 
      ? '— Unchanged (0.00%)'
      : `${arrow} ${word} ${amountStr} (${percentStr})`;

    return {
      arrow,
      word,
      amountStr,
      percentStr,
      fullText,
      isPositive,
      isNegative,
      isZero,
    };
  }

  /**
   * Format Quantity nicely (e.g. 139 shares, 14.520 grams, 324.891 units)
   */
  static formatQty(scaled: bigint | number | string, decimals = 3): string {
    const raw = this.toQty(scaled);
    if (Number.isInteger(raw)) {
      return raw.toLocaleString('en-IN');
    }
    return raw.toLocaleString('en-IN', {
      minimumFractionDigits: 0,
      maximumFractionDigits: decimals,
    });
  }
}
