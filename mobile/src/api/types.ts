// DTOs mirroring backend/src/main/kotlin/com/storepilot/backend/**/*.kt are
// now the single source of truth in @storepilot/shared-api (originally
// authored on the web app's side, since it has the fuller/older catalog).
// This file just re-exports them under the names this app's code already
// uses (mobile named things with a "...Response" suffix; web didn't) so no
// other mobile file needs to change its imports. Only genuinely
// mobile-only types are defined directly below.

import type {
  Store,
  StoreStats,
  Order,
  Product,
  ProductImage,
  Booking,
  BookableService,
  Conversation,
  Message,
  ReturnRequest,
  Payout,
  FeeCollection,
  Coupon,
  SellerPlanInfo,
  StoreSettings,
  SellerNotification,
  SellerNotificationSummary,
  BuyerNotification,
  BuyerNotificationSummary,
  StoreStaffMember,
  StoreStaffInvite,
  StaffInviteDetails,
} from "@storepilot/shared-api";

export type {
  PageResponse,
  StoreProfileInput,
  ShippingDetails,
  OrderStatus,
  PaymentStatus,
  PaymentMethod,
  OrderItem,
  OrderTimelineEntry,
  ProductStatus,
  BookingStatus,
  BookingTimelineEntry,
  BookableServiceImage,
  ReturnReasonCategory,
  ReturnRequestStatus,
  PayoutOrderRef,
  FeeCollectionOrderRef,
  CouponInput,
  SellerNotificationType,
  StoreApplicationInput,
  SellerType,
  StoreAvailability,
  WeeklyAvailabilityRule,
  WeeklyAvailabilityInput,
  AvailabilityException,
  AvailabilityExceptionInput,
  BookingAnalytics,
  AbnLookupResult,
  StaffInviteInput,
  AcceptStaffInviteInput,
} from "@storepilot/shared-api";

export type StoreResponse = Store;
export type StoreStatsResponse = StoreStats;
export type StoreStaffMemberResponse = StoreStaffMember;
export type StoreStaffInviteResponse = StoreStaffInvite;
export type StaffInviteDetailsResponse = StaffInviteDetails;
export type OrderResponse = Order;
export type ProductResponse = Product;
export type ProductImageResponse = ProductImage;
export type BookingResponse = Booking;
export type BookableServiceResponse = BookableService;
export type ConversationResponse = Conversation;
export type MessageResponse = Message;
export type ReturnRequestResponse = ReturnRequest;
export type PayoutResponse = Payout;
export type FeeCollectionResponse = FeeCollection;
export type CouponResponse = Coupon;
export type SellerPlanResponse = SellerPlanInfo;
export type StoreSettingsResponse = StoreSettings;
export type SellerNotificationResponse = SellerNotification;
export type SellerNotificationSummaryResponse = SellerNotificationSummary;
export type BuyerNotificationResponse = BuyerNotification;
export type BuyerNotificationSummaryResponse = BuyerNotificationSummary;

export type ApiErrorBody = {
  error: {
    code: string;
    message: string;
    fields?: Record<string, string> | null;
  };
};

export type AuthSessionResponse = {
  signedIn: boolean;
  role?: string | null;
  email?: string | null;
  name?: string | null;
  mfaRequired?: boolean;
  mfaSession?: string | null;
  accessToken?: string | null;
  refreshToken?: string | null;
};

/** All fields optional — a field left undefined/null is untouched server-side, matching the PATCH's upsert-merge semantics. */
export type StoreSettingsInput = Partial<{
  contactEmail: string;
  contactPhone: string;
  bankAccountName: string;
  bankAccountNumber: string;
  bankName: string;
  transactionFeePercent: number;
  codEnabled: boolean;
  onlinePaymentEnabled: boolean;
  bankTransferEnabled: boolean;
  sellerType: 'individual' | 'business';
  driverLicenceNumber: string;
  abn: string;
  nicNumber: string;
  businessRegistrationNumber: string;
  stockManagementEnabled: boolean;
  pickupEnabled: boolean;
  stripeEnabled: boolean;
  bookingsEnabled: boolean;
  gstRegistered: boolean;
}>;

export type CheckoutUrlResponse = {
  checkoutUrl: string;
};

export type MfaSetupResponse = {
  secret: string;
  otpauthUri: string;
};

export type MfaStatusResponse = {
  enabled: boolean;
};

export type SellerExportResponse = {
  profile: SellerPlanResponse;
  store: StoreResponse | null;
  storeSettings: StoreSettingsResponse | null;
  products: ProductResponse[];
  orders: OrderResponse[];
  bookings: BookingResponse[];
  payouts: PayoutResponse[];
  feeCollections: FeeCollectionResponse[];
  coupons: CouponResponse[];
};
