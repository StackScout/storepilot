export interface StoreStaffMember {
  id: string;
  sellerId: string;
  name: string;
  email: string;
  joinedAt: string;
}

export type StoreStaffInviteStatus = "pending" | "accepted" | "expired" | "revoked";

export interface StoreStaffInvite {
  id: string;
  email: string;
  name: string;
  status: StoreStaffInviteStatus;
  invitedAt: string;
  expiresAt: string;
}

export interface StaffInviteInput {
  name: string;
  email: string;
}

/** GET /api/staff/invites/:token — public; deliberately never carries the store id, just enough to render the accept-invite screen. */
export interface StaffInviteDetails {
  storeName: string;
  email: string;
  name: string;
  expiresAt: string;
}

export interface AcceptStaffInviteInput {
  token: string;
  password: string;
  name?: string;
}
