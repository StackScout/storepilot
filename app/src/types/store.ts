export type StoreCategory =
  | "fashion"
  | "food-beverage"
  | "beauty"
  | "handicrafts"
  | "electronics"
  | "home-living"
  | "jewelry"
  | "grocery";

/** `state` is deliberately one generic "state/province" field, not a separate district+province pair — see backend's StoreAddress.state doc comment. Options come from `GET /api/states`. */
export interface StoreAddress {
  city: string;
  state: string;
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
  /**
   * Verification fields — collected at onboarding, reviewed by admin. Which
   * pair is active (driver's licence/ABN vs NIC/business registration) is
   * decided by this deployment's platform config country, not by the
   * store — see `usePlatformConfig().countryCode`. Both pairs are optional
   * here since a given deployment only ever populates one.
   */
  sellerType: SellerType;
  driverLicenceNumber?: string;
  /** Australian Business Number — required when sellerType === "business" on an AU deployment. */
  abn?: string;
  /** Sri Lanka NIC number — required on an LK deployment. */
  nicNumber?: string;
  /** Sri Lanka Business Registration Number — required when sellerType === "business" on an LK deployment. */
  businessRegistrationNumber?: string;
  /** Set by admin when verificationStatus is rejected, shown to the seller. */
  rejectionReason?: string;
  /** Uploaded proof documents — resolved to a fetchable URL by the backend at read time. */
  driverLicenceDocumentUrl?: string;
  abnDocumentUrl?: string;
  nicDocumentUrl?: string;
  businessRegDocumentUrl?: string;
  /** Store-wide switch — when false, no product in this store tracks stock, and the new-product page hides the stock UI entirely. */
  stockManagementEnabled: boolean;
  /** Opt-in, defaults false — offers "pickup in store" at checkout in addition to shipping. See order.ts's DeliveryMethod. */
  pickupEnabled: boolean;
  /**
   * Stripe Connect (Standard account) — see backend StoreSettings.kt's doc
   * comment. `stripeChargesEnabled`/`stripePayoutsEnabled` are synced from
   * Stripe via webhook, never client-settable; `stripeEnabled` is the
   * seller's own on/off preference, independent of onboarding status.
   */
  stripeAccountId?: string;
  stripeChargesEnabled: boolean;
  stripePayoutsEnabled: boolean;
  stripeEnabled: boolean;
}

/** Input for creating a new Store at seller onboarding time. */
export interface StoreApplicationInput {
  name: string;
  category: StoreCategory;
  tagline: string;
  description: string;
  city: string;
  state: string;
  whatsappNumber: string;
}
