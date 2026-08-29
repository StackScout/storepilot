import type { CheckoutBookingInput, CheckoutInput, Order, Booking } from '@storepilot/shared-api';

import { apiFetch } from '@/lib/api-client';

/** Guest-reachable (skipAuth) — checkout works signed-in or as a guest, same as web. A signed-in buyer's order/booking is still linked server-side from the auth header when present. */
export function createOrder(input: CheckoutInput): Promise<Order> {
  return apiFetch<Order>('/api/orders', { method: 'POST', body: input, skipAuth: true });
}

export function createBooking(input: CheckoutBookingInput): Promise<Booking> {
  return apiFetch<Booking>('/api/bookings', { method: 'POST', body: input, skipAuth: true });
}

export function getPayHereCheckoutPayload(orderId: string): Promise<Record<string, string>> {
  return apiFetch(`/api/orders/${orderId}/payhere-checkout`, { method: 'POST', skipAuth: true });
}

/** platform=mobile makes the backend hand back a deep-link success/cancel URL instead of the web app's order page — see checkout.tsx's use of openAuthSessionAsync. */
export function getStripeCheckoutUrl(orderId: string): Promise<{ checkoutUrl: string }> {
  return apiFetch(`/api/orders/${orderId}/stripe-checkout?platform=mobile`, { method: 'POST', skipAuth: true });
}

export function getBookingPayHereCheckoutPayload(bookingId: string): Promise<Record<string, string>> {
  return apiFetch(`/api/bookings/${bookingId}/payhere-checkout`, { method: 'POST', skipAuth: true });
}

export function getBookingStripeCheckoutUrl(bookingId: string): Promise<{ checkoutUrl: string }> {
  return apiFetch(`/api/bookings/${bookingId}/stripe-checkout`, { method: 'POST', skipAuth: true });
}
