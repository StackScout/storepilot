/** Mirrors the backend's CategoryResponse — an admin-managed store/product/service category. */
export interface Category {
  id: string;
  name: string;
  wireValue: string;
  icon: string;
  sortOrder: number;
  active: boolean;
}

/** Used for both create and update — an admin resubmits the full shape rather than a partial patch. */
export interface CategoryFormInput {
  name: string;
  wireValue: string;
  icon: string;
  sortOrder?: number;
  active?: boolean;
}
