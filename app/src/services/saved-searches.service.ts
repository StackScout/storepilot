import { apiClient } from "@/lib/api-client";
import type { SavedSearch, SavedSearchInput } from "@/types";

/** GET /me/saved-searches */
export async function listSavedSearches(): Promise<SavedSearch[]> {
  return apiClient.get<SavedSearch[]>("/api/me/saved-searches");
}

/** POST /me/saved-searches */
export async function createSavedSearch(input: SavedSearchInput): Promise<SavedSearch> {
  return apiClient.post<SavedSearch>("/api/me/saved-searches", input);
}

/** DELETE /me/saved-searches/:id */
export async function deleteSavedSearch(id: string): Promise<void> {
  await apiClient.delete<void>(`/api/me/saved-searches/${id}`);
}
