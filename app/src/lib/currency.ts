/**
 * All amounts in this codebase are stored as an integer count of the
 * currency's smallest unit — cents for AUD — never whole dollars. [amount]
 * here is always that cents value; this function divides by 100 before
 * formatting. See backend's Product.price doc comment for the storage-side
 * half of this convention.
 */
export interface CurrencyConfig {
  code: string;
  symbol: string;
  locale: string;
}

export function formatCurrency(amountInCents: number, currency: CurrencyConfig): string {
  return new Intl.NumberFormat(currency.locale, {
    style: "currency",
    currency: currency.code,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
    .format(amountInCents / 100)
    .replace(currency.code, currency.symbol);
}
