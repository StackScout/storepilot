/**
 * Fetches the DB-backed platform configuration (see backend's
 * PlatformSettings.kt) and state/province reference data (State.kt) —
 * replaces the old NEXT_PUBLIC_PLATFORM_* / SRI_LANKA_DISTRICTS build-time
 * constants. `next: { revalidate }` keeps Server Component call sites
 * (layout metadata, the footer, the home page hero) effectively static
 * instead of forcing full per-request dynamic rendering, while still
 * picking up a config change within a few minutes without a rebuild.
 * Client Components read the same data through the context/hook in
 * app/providers.tsx instead of calling this directly (see usePlatformConfig).
 */
const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

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
  defaultCodEnabled: boolean;
  defaultOnlinePaymentEnabled: boolean;
  defaultBankTransferEnabled: boolean;
  supportEmail: string;
  companyLocation: string;
}

/**
 * Bootstrap-only fallback — used solely when the backend isn't reachable
 * yet (e.g. `next build`'s static-generation pass runs before/without a
 * live backend, including in `docker compose build`'s per-service build
 * order). Never the steady-state path: once the backend is up, ISR's
 * `revalidate` window (below) re-fetches the real platform_settings row
 * within minutes, so a build-time fallback never lingers in production.
 * Mirrors PlatformProperties.kt's own bootstrap defaults.
 */
const FALLBACK_CONFIG: PlatformConfig = {
  name: "StorePilot",
  tagline: "Australia's marketplace for small business sellers",
  countryName: "Australia",
  countryCode: "AU",
  currencyCode: "AUD",
  currencySymbol: "$",
  currencyLocale: "en-AU",
  platformFeePercent: 3.5,
  flatShippingFee: 10,
  defaultCodEnabled: true,
  defaultOnlinePaymentEnabled: false,
  defaultBankTransferEnabled: true,
  supportEmail: "hello@storepilot.au",
  companyLocation: "Sydney, Australia",
};

export async function getPlatformConfig(): Promise<PlatformConfig> {
  try {
    const res = await fetch(`${API_BASE_URL}/api/platform-config`, { next: { revalidate: 300 } });
    if (!res.ok) throw new Error(`Failed to load platform config: ${res.status}`);
    return await res.json();
  } catch (err) {
    console.warn("getPlatformConfig: backend unreachable, using fallback config", err);
    return FALLBACK_CONFIG;
  }
}

export interface StateOption {
  name: string;
}

export async function getStates(): Promise<StateOption[]> {
  try {
    const res = await fetch(`${API_BASE_URL}/api/states`, { next: { revalidate: 300 } });
    if (!res.ok) throw new Error(`Failed to load states: ${res.status}`);
    return await res.json();
  } catch (err) {
    console.warn("getStates: backend unreachable, returning no options", err);
    return [];
  }
}
