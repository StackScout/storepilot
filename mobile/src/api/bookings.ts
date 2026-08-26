import { apiFetch } from '@/lib/api-client';
import type { BookingResponse, BookingStatus } from '@/api/types';

export function listStoreBookings(storeId: string): Promise<BookingResponse[]> {
  return apiFetch<BookingResponse[]>(`/api/stores/${storeId}/bookings`);
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
