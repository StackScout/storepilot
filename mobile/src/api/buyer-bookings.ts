import type { Booking, CancelBookingInput, PageResponse } from '@storepilot/shared-api';

import { ApiError, apiFetch, apiFetchForm } from '@/lib/api-client';

export async function getBookingById(id: string): Promise<Booking | null> {
  try {
    return await apiFetch<Booking>(`/api/bookings/${id}`, { skipAuth: true });
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
}

/** GET /api/me/bookings — the signed-in buyer's own booking history. */
export async function listMyBookings(): Promise<Booking[]> {
  return (await apiFetch<PageResponse<Booking>>('/api/me/bookings?size=200')).content;
}

export async function requestBookingLookupCode(bookingNumber: string, phone: string): Promise<void> {
  await apiFetch<void>('/api/bookings/lookup/request-code', { method: 'POST', body: { bookingNumber, phone }, skipAuth: true });
}

export function verifyBookingLookupCode(bookingNumber: string, phone: string, code: string): Promise<Booking> {
  return apiFetch<Booking>('/api/bookings/lookup/verify', { method: 'POST', body: { bookingNumber, phone, code }, skipAuth: true });
}

/** See api/products.ts's buildProductForm doc comment — same Blob-from-uri workaround for Expo SDK 57's FormData encoder. */
export async function uploadBookingReceipt(bookingId: string, imageUri: string): Promise<Booking> {
  const rawBlob = await (await fetch(imageUri)).blob();
  const blob = rawBlob.slice(0, rawBlob.size, 'image/jpeg');
  const form = new FormData();
  form.append('file', blob, 'receipt.jpg');
  return apiFetchForm<Booking>(`/api/bookings/${bookingId}/receipt`, form, 'POST');
}

export function cancelBooking(bookingId: string, input: CancelBookingInput = {}): Promise<Booking> {
  return apiFetch<Booking>(`/api/bookings/${bookingId}/cancel`, { method: 'POST', body: input, skipAuth: true });
}
