/**
 * Admin-managed via GET /api/categories (see Category.kt backend-side) —
 * no longer a fixed set of literals. Was a union type; widened to `string`
 * once categories became dynamic. Valid values are whatever `GET
 * /api/categories` currently returns, not enumerable at the type level.
 */
export type StoreCategory = string;

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
export type StoreVerificationStatus = "pending" | "active" | "rejected" | "closed";

export interface Store {
  id: string;
  slug: string;
  name: string;
  tagline: string;
  description: string;
  /** Null until the seller uploads one — StoreLogoFallback/StoreBannerFallback render a generated placeholder in the meantime. */
  logoUrl: string | null;
  bannerUrl: string | null;
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
  /** Only ever populated by GET /api/me/store, relative to the caller — undefined everywhere else (public listings, admin views, other sellers). */
  role?: "owner" | "staff";
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
  /** Opt-in, defaults false — gates whether this store's bookable-services section exists at all. Not itself Pro-gated; only the "pay at venue"/bank-transfer booking payment methods are (mirroring codEnabled/bankTransferEnabled) — see docs/features/bookings.md. */
  bookingsEnabled: boolean;
  /** Self-declared, opt-in, defaults false — GST registration is turnover-based (mandatory above A$75k/year, optional below it), never implied by ABN presence alone. Drives whether order confirmations render as ATO tax invoices — see order.ts's Order.sellerAbn/gstAmount. */
  gstRegistered: boolean;
}

/**
 * Buyer-safe subset of StoreSettings returned by GET
 * /api/stores/{storeId}/public-settings — what checkout and order pages
 * need to render payment options and bank-transfer details, without the
 * PII the full StoreSettings carries (contact info, NIC/ABN, verification
 * documents).
 */
export type StorePublicSettings = Pick<
  StoreSettings,
  | "storeId"
  | "bankAccountName"
  | "bankAccountNumber"
  | "bankName"
  | "codEnabled"
  | "onlinePaymentEnabled"
  | "bankTransferEnabled"
  | "pickupEnabled"
  | "stripeEnabled"
  | "stripeChargesEnabled"
  | "bookingsEnabled"
>;

export type StoreVerificationChangeRequestStatus = "pending" | "approved" | "rejected";

/**
 * A seller's proposed change to a subset of their (already-approved)
 * store's verification-identity fields — see backend
 * StoreVerificationChangeRequest's doc comment. current* fields reflect the
 * store's live settings at read time, letting the review UI render an
 * old-vs-new diff without a second request.
 */
export interface StoreVerificationChangeRequest {
  id: string;
  storeId: string;
  storeName: string;
  status: StoreVerificationChangeRequestStatus;
  sellerType?: SellerType;
  driverLicenceNumber?: string;
  abn?: string;
  nicNumber?: string;
  businessRegistrationNumber?: string;
  driverLicenceDocumentUrl?: string;
  abnDocumentUrl?: string;
  nicDocumentUrl?: string;
  businessRegDocumentUrl?: string;
  currentSellerType: SellerType;
  currentDriverLicenceNumber?: string;
  currentAbn?: string;
  currentNicNumber?: string;
  currentBusinessRegistrationNumber?: string;
  currentDriverLicenceDocumentUrl?: string;
  currentAbnDocumentUrl?: string;
  currentNicDocumentUrl?: string;
  currentBusinessRegDocumentUrl?: string;
  rejectionReason?: string;
  submittedAt: string;
  reviewedAt?: string;
  reviewedByEmail?: string;
}

/** POST /api/stores/{storeId}/verification-change-requests — text-field part of the multipart submission; document re-uploads go in separate parts. */
export interface VerificationChangeRequestInput {
  sellerType?: SellerType;
  driverLicenceNumber?: string;
  abn?: string;
  nicNumber?: string;
  businessRegistrationNumber?: string;
}

/** GET /api/stores/{storeId}/stats — rolling 7-day window vs the 7 days before it. */
export interface StoreStats {
  revenueCurrentPeriod: number;
  revenuePreviousPeriod: number;
  platformFeeCurrentPeriod: number;
  platformFeePreviousPeriod: number;
  /** Not time-windowed like the fields above — a live count of every order currently awaiting the seller's action. */
  pendingOrderCount: number;
}

/** GET /api/stores/{storeId}/follow */
export interface FollowStatus {
  following: boolean;
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
