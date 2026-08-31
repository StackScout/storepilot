import { apiClient } from "@/lib/api-client";
import type { StaffInviteDetails, StaffInviteInput, StoreStaffInvite, StoreStaffMember } from "@/types";

/** POST /stores/:storeId/staff/invite — owner-only. */
export async function inviteStaff(storeId: string, input: StaffInviteInput): Promise<StoreStaffInvite> {
  return apiClient.post<StoreStaffInvite>(`/api/stores/${storeId}/staff/invite`, input);
}

/** GET /stores/:storeId/staff — owner-only. */
export async function listStaff(storeId: string): Promise<StoreStaffMember[]> {
  return apiClient.get<StoreStaffMember[]>(`/api/stores/${storeId}/staff`);
}

/** GET /stores/:storeId/staff/invites — owner-only, pending invites only. */
export async function listPendingInvites(storeId: string): Promise<StoreStaffInvite[]> {
  return apiClient.get<StoreStaffInvite[]>(`/api/stores/${storeId}/staff/invites`);
}

/** DELETE /stores/:storeId/staff/:staffMemberId — owner-only; also strips the removed member's Cognito seller role, see backend StoreStaffService.removeStaff's doc comment. */
export async function removeStaff(storeId: string, staffMemberId: string): Promise<void> {
  await apiClient.delete<void>(`/api/stores/${storeId}/staff/${staffMemberId}`);
}

/** DELETE /stores/:storeId/staff/invites/:inviteId — owner-only. */
export async function revokeInvite(storeId: string, inviteId: string): Promise<void> {
  await apiClient.delete<void>(`/api/stores/${storeId}/staff/invites/${inviteId}`);
}

/** GET /staff/invites/:token — public; lets the accept-invite page render before the invitee is authenticated. */
export async function getInviteDetails(token: string): Promise<StaffInviteDetails> {
  return apiClient.get<StaffInviteDetails>(`/api/staff/invites/${encodeURIComponent(token)}`);
}
