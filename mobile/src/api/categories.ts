import type { Category } from '@storepilot/shared-api';

import { apiFetch } from '@/lib/api-client';

/**
 * GET /api/categories — public, active categories only. Categories are admin-managed now
 * (see backend's CategoryController), replacing the old hardcoded constants/categories.ts.
 * Not paginated — mirrors the pre-existing GET /api/states pattern. Mirrors the web app's
 * services/categories.service.ts listCategories().
 */
export function listCategories(): Promise<Category[]> {
  return apiFetch<Category[]>('/api/categories', { skipAuth: true });
}
