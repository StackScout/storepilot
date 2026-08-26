/** Matches backend AuditLogResponse — one recorded admin action, for audit purposes. Never updated after creation. */
export interface AuditLogEntry {
  id: string;
  actorEmail: string;
  action: string;
  targetType?: string;
  targetId?: string;
  description: string;
  createdAt: string;
}

/** Matches backend AdminSummaryResponse — sourced from Cognito's `admin` group directly, not the local Admin table, so an invited admin who hasn't logged in yet still shows up. */
export interface AdminSummary {
  email: string;
  name: string;
  invitedAt: string;
}

/** Matches backend AccountingSummaryResponse — all fields are cents, like every other money field in this app. */
export interface AccountingSummary {
  payoutsScheduledTotal: number;
  payoutsPaidTotal: number;
  feeCollectionsPendingTotal: number;
  feeCollectionsCollectedTotal: number;
  stripeSettledTotal: number;
  stripePlatformFeeTotal: number;
}
