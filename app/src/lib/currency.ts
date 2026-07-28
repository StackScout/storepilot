/**
 * All amounts in this codebase are stored as whole-unit integers (price,
 * total, ...) — maximumFractionDigits: 0 matches that. A currency that
 * needs cents (e.g. AUD's) isn't fully supported by this function alone;
 * that's a bigger, not-yet-done change (see backend's PlatformProperties.kt
 * doc comment).
 */
export interface CurrencyConfig {
  code: string;
  symbol: string;
  locale: string;
}

export function formatCurrency(amount: number, currency: CurrencyConfig): string {
  return new Intl.NumberFormat(currency.locale, {
    style: "currency",
    currency: currency.code,
    maximumFractionDigits: 0,
  })
    .format(amount)
    .replace(currency.code, currency.symbol);
}
