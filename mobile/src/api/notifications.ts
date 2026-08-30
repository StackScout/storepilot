import { apiFetch } from '@/lib/api-client';
import type { PageResponse, SellerNotificationResponse, SellerNotificationSummaryResponse } from '@/api/types';

export function listNotifications(page = 0, size = 20): Promise<PageResponse<SellerNotificationResponse>> {
  return apiFetch<PageResponse<SellerNotificationResponse>>(`/api/me/seller/notifications?page=${page}&size=${size}`);
}

export function getNotificationsSummary(): Promise<SellerNotificationSummaryResponse> {
  return apiFetch<SellerNotificationSummaryResponse>('/api/me/seller/notifications/summary');
}

export function markNotificationRead(id: string): Promise<SellerNotificationResponse> {
  return apiFetch<SellerNotificationResponse>(`/api/me/seller/notifications/${id}/read`, { method: 'PATCH' });
}

export function markAllNotificationsRead(): Promise<void> {
  return apiFetch<void>('/api/me/seller/notifications/read-all', { method: 'PATCH' });
}
