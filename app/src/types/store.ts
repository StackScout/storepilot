export type StoreCategory =
  | "fashion"
  | "food-beverage"
  | "beauty"
  | "handicrafts"
  | "electronics"
  | "home-living"
  | "jewelry"
  | "grocery";

export interface StoreAddress {
  city: string;
  district: string;
  province: string;
}

/**
 * Gates whether a store is discoverable/orderable on the public marketplace.
 * Set by the (mock) admin review flow, not by the seller themselves — see
 * `docs/features/seller-auth.md`.
 */
export type StoreVerificationStatus = "pending" | "active" | "rejected";

export interface Store {
  id: string;
  slug: string;
  name: string;
  tagline: string;
  description: string;
  logoUrl: string;
  bannerUrl: string;
  category: StoreCategory;
  address: StoreAddress;
  whatsappNumber: string;
  rating: number;
  reviewCount: number;
  productCount: number;
  isVerified: boolean;
  joinedAt: string;
  followerCount: number;
  verificationStatus: StoreVerificationStatus;
  facebookUrl?: string;
  instagramUrl?: string;
  tiktokUrl?: string;
}

/** Input for PATCH /api/stores/{id}/profile — seller-editable public social links. */
export interface StoreProfileInput {
  facebookUrl?: string;
  instagramUrl?: string;
  tiktokUrl?: string;
}

/** Whether a seller is registering as a private individual or a registered business. */
export type SellerType = "individual" | "business";

export interface StoreSettings {
  storeId: string;
  contactEmail: string;
  contactPhone: string;
  bankAccountName: string;
  bankAccountNumber: string;
  bankName: string;
  transactionFeePercent: number;
  codEnabled: boolean;
  onlinePaymentEnabled: boolean;
  /** Opt-in, defaults false — shows bankName/bankAccountName/bankAccountNumber to buyers at checkout. */
  bankTransferEnabled: boolean;
  /** Verification fields — collected at onboarding, reviewed by admin. */
  sellerType: SellerType;
  nicNumber: string;
  /** Required when sellerType === "business"; absent for individual sellers. */
  businessRegistrationNumber?: string;
  /** Set by admin when verificationStatus is rejected, shown to the seller. */
  rejectionReason?: string;
  /** Uploaded proof documents — resolved to a fetchable URL by the backend at read time. */
  nicDocumentUrl?: string;
  businessRegDocumentUrl?: string;
  /** Store-wide switch — when false, no product in this store tracks stock, and the new-product page hides the stock UI entirely. */
  stockManagementEnabled: boolean;
}

/** Input for creating a new Store at seller onboarding time. */
export interface StoreApplicationInput {
  name: string;
  category: StoreCategory;
  tagline: string;
  description: string;
  city: string;
  district: string;
  whatsappNumber: string;
}
