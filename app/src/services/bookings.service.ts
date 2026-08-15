import { apiClient, toQueryString } from "@/lib/api-client";
import type { Booking, BookingAnalytics, BookingStatus, CancelBookingInput, CheckoutBookingInput, PayHereCheckoutPayload } from "@/types";

/** GET /stores/:storeId/bookings */
export async function listBookingsByStore(storeId: string, status?: BookingStatus): Promise<Booking[]> {
  const qs = toQueryString({ status });
  return apiClient.get<Booking[]>(`/api/stores/${storeId}/bookings${qs}`);
}

/** GET /stores/:storeId/booking-analytics — Pro-only; the backend 403s with a "Booking analytics is a Pro feature" message for a non-Pro seller. */
export async function getBookingAnalytics(storeId: string): Promise<BookingAnalytics> {
  return apiClient.get<BookingAnalytics>(`/api/stores/${storeId}/booking-analytics`);
}

/** GET /api/me/bookings — the signed-in buyer's own booking history, derived from the auth cookie. */
export async function listMyBookings(): Promise<Booking[]> {
  return apiClient.get<Booking[]>("/api/me/bookings");
}

/** GET /bookings/:id */
export async function getBookingById(id: string): Promise<Booking | null> {
  return apiClient.getOrNull<Booking>(`/api/bookings/${id}`);
}

/** No transformation needed today (no relative asset URLs on a booking) — kept for symmetry with orders.service.ts's normalizeOrder so callers like useLiveStatus don't need to special-case bookings. */
export function normalizeBooking(booking: Booking): Booking {
  return booking;
}

/** GET /bookings/recurrence/:groupId — every occurrence of a recurring series, chronological order. */
export async function listBookingsByRecurrenceGroup(groupId: string): Promise<Booking[]> {
  return apiClient.get<Booking[]>(`/api/bookings/recurrence/${groupId}`);
}

/** POST /bookings/lookup/request-code — first step of guest lookup, emails a one-time code. Always resolves, regardless of whether bookingNumber/phone matched anything. */
export async function requestBookingLookupCode(bookingNumber: string, phone: string): Promise<void> {
  await apiClient.post<void>("/api/bookings/lookup/request-code", { bookingNumber, phone });
}

/** POST /bookings/lookup/verify — second step of guest lookup. Throws ApiRequestError (404 number/phone mismatch, 400 bad/expired code) on failure — callers should catch and show the error's message. */
export async function verifyBookingLookupCode(bookingNumber: string, phone: string, code: string): Promise<Booking> {
  return apiClient.post<Booking>("/api/bookings/lookup/verify", { bookingNumber, phone, code });
}

/** POST /bookings — booking checkout. */
export async function createBooking(input: CheckoutBookingInput): Promise<Booking> {
  return apiClient.post<Booking>("/api/bookings", input);
}

/** POST /bookings/:id/payhere-checkout — hash generated server-side, never in the browser. */
export async function getPayHereCheckoutPayload(bookingId: string): Promise<PayHereCheckoutPayload> {
  return apiClient.post<PayHereCheckoutPayload>(`/api/bookings/${bookingId}/payhere-checkout`);
}

/** POST /bookings/:id/stripe-checkout — returns a ready-to-redirect Stripe Checkout URL. */
export async function getStripeCheckoutUrl(bookingId: string): Promise<{ checkoutUrl: string }> {
  return apiClient.post<{ checkoutUrl: string }>(`/api/bookings/${bookingId}/stripe-checkout`);
}

/** PATCH /bookings/:id/status — seller-driven transitions. */
export async function updateBookingStatus(id: string, status: BookingStatus, note?: string): Promise<Booking> {
  return apiClient.patch<Booking>(`/api/bookings/${id}/status`, { status, note });
}

/** POST /bookings/:id/receipt — buyer uploads proof of a bank transfer. */
export async function uploadBookingReceipt(bookingId: string, file: File): Promise<Booking> {
  const formData = new FormData();
  formData.append("file", file);
  return apiClient.postForm<Booking>(`/api/bookings/${bookingId}/receipt`, formData);
}

/** POST /bookings/:id/verify-bank-transfer — seller accepts or rejects the uploaded receipt. */
export async function verifyBookingBankTransfer(bookingId: string, approved: boolean, note?: string): Promise<Booking> {
  return apiClient.post<Booking>(`/api/bookings/${bookingId}/verify-bank-transfer`, { approved, note });
}

/** POST /bookings/:id/cancel — buyer- or seller-initiated, reachable unauthenticated (booking id is the credential). Rejected inside the store's lead-time cutoff. */
export async function cancelBooking(bookingId: string, input: CancelBookingInput = {}): Promise<Booking> {
  return apiClient.post<Booking>(`/api/bookings/${bookingId}/cancel`, input);
}
