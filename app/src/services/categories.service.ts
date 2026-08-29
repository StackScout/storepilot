import { apiClient } from "@/lib/api-client";
import type { Category, CategoryFormInput } from "@/types";

/** GET /categories — public, active categories only. Used by onboarding/product/service-form dropdowns. */
export async function listCategories(): Promise<Category[]> {
  return apiClient.get<Category[]>("/api/categories");
}

/** GET /admin/categories — every category, including inactive ones, for the admin management page. */
export async function adminListCategories(): Promise<Category[]> {
  return apiClient.get<Category[]>("/api/admin/categories");
}

/** POST /admin/categories */
export async function createCategory(input: CategoryFormInput): Promise<Category> {
  return apiClient.post<Category>("/api/admin/categories", input);
}

/** PATCH /admin/categories/:id */
export async function updateCategory(id: string, input: CategoryFormInput): Promise<Category> {
  return apiClient.patch<Category>(`/api/admin/categories/${id}`, input);
}

/** DELETE /admin/categories/:id — rejected server-side if any store/product/service still references it; deactivate instead (via updateCategory) in that case. */
export async function deleteCategory(id: string): Promise<void> {
  await apiClient.delete<void>(`/api/admin/categories/${id}`);
}
