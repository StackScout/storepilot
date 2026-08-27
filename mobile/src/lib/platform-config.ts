import { useQuery } from '@tanstack/react-query';

import { apiFetch } from '@/lib/api-client';

/** Mirrors the web app's lib/platform-config.ts — no SSR here, so this is always fetched client-side via useQuery rather than injected server-side. */
export interface PlatformConfig {
  name: string;
  tagline: string;
  countryName: string;
  countryCode: string;
  currencyCode: string;
  currencySymbol: string;
  currencyLocale: string;
  platformFeePercent: number;
  flatShippingFee: number;
  proMonthlyPriceCents: number;
  defaultCodEnabled: boolean;
  defaultOnlinePaymentEnabled: boolean;
  defaultBankTransferEnabled: boolean;
  supportEmail: string;
  companyLocation: string;
  returnWindowDays: number;
}

/** Bootstrap-only fallback for the brief window before the real fetch resolves — mirrors PlatformProperties.kt's own bootstrap defaults. */
export const FALLBACK_PLATFORM_CONFIG: PlatformConfig = {
  name: 'StorePilot',
  tagline: "Australia's marketplace for small business sellers",
  countryName: 'Australia',
  countryCode: 'AU',
  currencyCode: 'AUD',
  currencySymbol: '$',
  currencyLocale: 'en-AU',
  platformFeePercent: 3.5,
  flatShippingFee: 1000,
  proMonthlyPriceCents: 2900,
  defaultCodEnabled: true,
  defaultOnlinePaymentEnabled: true,
  defaultBankTransferEnabled: false,
  supportEmail: 'hello@storepilot.au',
  companyLocation: 'Sydney, Australia',
  returnWindowDays: 30,
};

export function getPlatformConfig(): Promise<PlatformConfig> {
  return apiFetch<PlatformConfig>('/api/platform-config', { skipAuth: true });
}

export function usePlatformConfig() {
  const { data } = useQuery({
    queryKey: ['platform-config'],
    queryFn: getPlatformConfig,
    staleTime: Infinity,
  });
  return data ?? FALLBACK_PLATFORM_CONFIG;
}

export interface StateOption {
  name: string;
}

/** GET /api/states — state/province dropdown options for the running platform's own country. Rarely changes, cached indefinitely for the session, mirrors the web app's useStates. */
export function getStates(): Promise<StateOption[]> {
  return apiFetch<StateOption[]>('/api/states', { skipAuth: true });
}

export function useStates() {
  return useQuery({ queryKey: ['states'], queryFn: getStates, staleTime: Infinity });
}

export function formatCurrency(cents: number, config: PlatformConfig): string {
  return new Intl.NumberFormat(config.currencyLocale, { style: 'currency', currency: config.currencyCode }).format(cents / 100);
}
