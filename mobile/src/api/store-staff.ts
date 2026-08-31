import { apiFetch } from '@/lib/api-client';
import type { StaffInviteDetailsResponse, StaffInviteInput, StoreStaffInviteResponse, StoreStaffMemberResponse } from '@/api/types';

/** POST /stores/:storeId/staff/invite — owner-only. */
export function inviteStaff(storeId: string, input: StaffInviteInput): Promise<StoreStaffInviteResponse> {
  return apiFetch<StoreStaffInviteResponse>(`/api/stores/${storeId}/staff/invite`, { method: 'POST', body: input });
}

/** GET /stores/:storeId/staff — owner-only. */
export function listStaff(storeId: string): Promise<StoreStaffMemberResponse[]> {
  return apiFetch<StoreStaffMemberResponse[]>(`/api/stores/${storeId}/staff`);
}

/** GET /stores/:storeId/staff/invites — owner-only, pending invites only. */
export function listPendingInvites(storeId: string): Promise<StoreStaffInviteResponse[]> {
  return apiFetch<StoreStaffInviteResponse[]>(`/api/stores/${storeId}/staff/invites`);
}

/** DELETE /stores/:storeId/staff/:staffMemberId — owner-only; also strips the removed member's Cognito seller role. */
export function removeStaff(storeId: string, staffMemberId: string): Promise<void> {
  return apiFetch<void>(`/api/stores/${storeId}/staff/${staffMemberId}`, { method: 'DELETE' });
}

/** DELETE /stores/:storeId/staff/invites/:inviteId — owner-only. */
export function revokeInvite(storeId: string, inviteId: string): Promise<void> {
  return apiFetch<void>(`/api/stores/${storeId}/staff/invites/${inviteId}`, { method: 'DELETE' });
}

/** GET /staff/invites/:token — public; lets the accept-invite screen render before the invitee is authenticated. */
export function getInviteDetails(token: string): Promise<StaffInviteDetailsResponse> {
  return apiFetch<StaffInviteDetailsResponse>(`/api/staff/invites/${encodeURIComponent(token)}`, { skipAuth: true });
}
