import { apiFetch } from '@/lib/api-client';
import type { BookingAnalytics, BookingResponse, BookingStatus, PageResponse } from '@/api/types';

/** No pagination UI on the seller dashboard yet — size=200 keeps today's "show everything" behavior. */
export async function listStoreBookings(storeId: string): Promise<BookingResponse[]> {
  return (await apiFetch<PageResponse<BookingResponse>>(`/api/stores/${storeId}/bookings?size=200`)).content;
}

export function getBooking(id: string): Promise<BookingResponse> {
  return apiFetch<BookingResponse>(`/api/bookings/${id}`);
}

export function updateBookingStatus(id: string, status: BookingStatus, note?: string): Promise<BookingResponse> {
  return apiFetch<BookingResponse>(`/api/bookings/${id}/status`, {
    method: 'PATCH',
    body: { status, note },
  });
}

export function verifyBookingBankTransfer(id: string, approved: boolean, note?: string): Promise<BookingResponse> {
  return apiFetch<BookingResponse>(`/api/bookings/${id}/verify-bank-transfer`, {
    method: 'POST',
    body: { approved, note },
  });
}

/** GET /stores/:storeId/booking-analytics — Pro-only, see backend BookingAnalyticsService's doc comment. */
export function getBookingAnalytics(storeId: string): Promise<BookingAnalytics> {
  return apiFetch<BookingAnalytics>(`/api/stores/${storeId}/booking-analytics`);
}
