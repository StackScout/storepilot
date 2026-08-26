import { apiFetch } from '@/lib/api-client';
import type { CheckoutUrlResponse, SellerPlanResponse } from '@/api/types';

export function getSellerPlan(): Promise<SellerPlanResponse> {
  return apiFetch<SellerPlanResponse>('/api/me/seller/plan');
}

/** Starts a Stripe Checkout session — open the returned checkoutUrl in a browser (expo-web-browser). */
export function startBillingCheckout(): Promise<CheckoutUrlResponse> {
  return apiFetch<CheckoutUrlResponse>('/api/me/seller/billing/checkout', { method: 'POST' });
}

export function cancelBillingAtPeriodEnd(): Promise<SellerPlanResponse> {
  return apiFetch<SellerPlanResponse>('/api/me/seller/billing/cancel', { method: 'POST' });
}

/**
 * The Stripe Checkout success/cancel redirect always lands on the web dashboard, never back into
 * this app — after the in-app browser closes, call this (safe/idempotent) to force a resync from
 * Stripe in case the webhook hasn't landed yet, then re-fetch getSellerPlan().
 */
export function refreshBillingFromStripe(): Promise<SellerPlanResponse> {
  return apiFetch<SellerPlanResponse>('/api/me/seller/billing/refresh', { method: 'POST' });
}
