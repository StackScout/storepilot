import { apiClient, toQueryString } from "@/lib/api-client";
import type { AccountingSummary, AdminSummary, AuditLogEntry, PageResponse } from "@/types";

/** POST /admin/admins — invites a new admin; the inviting admin sets the initial password directly and shares it out of band (no self-service "set your own password" step exists anywhere in this app). */
export async function inviteAdmin(name: string, email: string, password: string): Promise<{ email: string; name: string }> {
  return apiClient.post("/api/admin/admins", { name, email, password });
}

/** GET /admin/admins — every Cognito user in the `admin` group, including ones who haven't signed in yet. */
export async function listAdmins(): Promise<AdminSummary[]> {
  return apiClient.get<AdminSummary[]>("/api/admin/admins");
}

/** GET /admin/audit-log — paginated, optionally filtered by action and/or targetType. */
export async function listAuditLog(params: { action?: string; targetType?: string; page?: number; size?: number } = {}): Promise<PageResponse<AuditLogEntry>> {
  const qs = toQueryString({
    action: params.action,
    targetType: params.targetType,
    page: params.page ?? 0,
    size: params.size ?? 50,
  });
  return apiClient.get<PageResponse<AuditLogEntry>>(`/api/admin/audit-log${qs}`);
}

/** GET /admin/accounting/summary */
export async function getAccountingSummary(): Promise<AccountingSummary> {
  return apiClient.get<AccountingSummary>("/api/admin/accounting/summary");
}
