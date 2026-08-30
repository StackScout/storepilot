import { apiClient } from "@/lib/api-client";
import type { PlatformConfig } from "@/lib/platform-config";

export interface PlatformPaymentMethodsInput {
  codEnabled: boolean;
  onlinePaymentEnabled: boolean;
  bankTransferEnabled: boolean;
}

/** PATCH /admin/platform-config/payment-methods — which payment methods this deployment offers at all, admin-only. See backend PlatformSettings' default*Enabled doc comment. */
export async function updatePaymentMethods(input: PlatformPaymentMethodsInput): Promise<PlatformConfig> {
  return apiClient.patch<PlatformConfig>("/api/admin/platform-config/payment-methods", input);
}

/** PATCH /admin/platform-config/pro-plan — whether the seller Free/Pro tier concept exists at all, admin-only. See backend PlatformSettings.proPlanEnabled doc comment. */
export async function updateProPlanEnabled(enabled: boolean): Promise<PlatformConfig> {
  return apiClient.patch<PlatformConfig>("/api/admin/platform-config/pro-plan", { enabled });
}
