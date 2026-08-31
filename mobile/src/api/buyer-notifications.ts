import { apiFetch } from '@/lib/api-client';
import type { BuyerNotificationResponse, BuyerNotificationSummaryResponse, PageResponse } from '@/api/types';

export function listBuyerNotifications(page = 0, size = 20): Promise<PageResponse<BuyerNotificationResponse>> {
  return apiFetch<PageResponse<BuyerNotificationResponse>>(`/api/me/buyer/notifications?page=${page}&size=${size}`);
}

export function getBuyerNotificationsSummary(): Promise<BuyerNotificationSummaryResponse> {
  return apiFetch<BuyerNotificationSummaryResponse>('/api/me/buyer/notifications/summary');
}

export function markBuyerNotificationRead(id: string): Promise<BuyerNotificationResponse> {
  return apiFetch<BuyerNotificationResponse>(`/api/me/buyer/notifications/${id}/read`, { method: 'PATCH' });
}

export function markAllBuyerNotificationsRead(): Promise<void> {
  return apiFetch<void>('/api/me/buyer/notifications/read-all', { method: 'PATCH' });
}
