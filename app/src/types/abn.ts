/**
 * "not-configured" means the backend has no ABR GUID set yet (see backend
 * AbrProperties) — expected in any environment without a real GUID, callers
 * should render nothing rather than an error state for it.
 */
export type AbnLookupStatus = "found" | "invalid-format" | "not-found" | "not-configured" | "error";

export interface AbnLookupResult {
  status: AbnLookupStatus;
  entityName?: string;
  abnStatus?: string;
  entityTypeName?: string;
  gstRegistered?: boolean;
}
