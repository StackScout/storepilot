import type { SavedSearch, SavedSearchInput } from '@storepilot/shared-api';

import { apiFetch } from '@/lib/api-client';

export function listSavedSearches(): Promise<SavedSearch[]> {
  return apiFetch<SavedSearch[]>('/api/me/saved-searches');
}

export function createSavedSearch(input: SavedSearchInput): Promise<SavedSearch> {
  return apiFetch<SavedSearch>('/api/me/saved-searches', { method: 'POST', body: input });
}

export async function deleteSavedSearch(id: string): Promise<void> {
  await apiFetch<void>(`/api/me/saved-searches/${id}`, { method: 'DELETE' });
}
