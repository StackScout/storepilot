import type { StoreCategory } from "./store";
import type { PaymentMethod, PaymentStatus } from "./order";

/** No "out-of-stock" analog — a service has no stock concept. */
export type ServiceStatus = "active" | "draft";

export interface BookableServiceImage {
  id: string;
  url: string;
  alt: string;
}

/** Mirrors backend BookableService — a bookable appointment/service a store offers, parallel to Product but for stores selling time instead of goods. */
export interface BookableService {
  id: string;
  storeId: string;
  storeName: string;
  storeSlug: string;
  name: string;
  slug: string;
  description: string;
  images: BookableServiceImage[];
  category: StoreCategory;
  price: number;
  /** Minutes — drives slot-chunking, see AvailabilityService. */
  durationMinutes: number;
  /** Minutes of gap enforced after each booking of this service before the next slot opens. */
  bufferMinutes: number;
  status: ServiceStatus;
  createdAt: string;
  updatedAt: string;
}

export interface BookableServiceFormInput {
  name: string;
  description: string;
  category: StoreCategory;
  price: number;
  durationMinutes: number;
  bufferMinutes: number;
  status: ServiceStatus;
}

/** 1 (Monday) .. 7 (Sunday). */
export interface WeeklyAvailabilityRule {
  dayOfWeek: number;
  isOpen: boolean;
  openTime?: string;
  closeTime?: string;
}

/** A date-specific override to the weekly template — either a closure or a special one-off opening. */
export interface AvailabilityException {
  id: string;
  date: string;
  isOpen: boolean;
  openTime?: string;
  closeTime?: string;
  note?: string;
}

export interface WeeklyAvailabilityInput {
  rules: WeeklyAvailabilityRule[];
  leadTimeMinutes?: number;
}

export interface AvailabilityExceptionInput {
  date: string;
  isOpen: boolean;
  openTime?: string;
  closeTime?: string;
  note?: string;
}

export interface StoreAvailability {
  leadTimeMinutes: number;
  weeklyRules: WeeklyAvailabilityRule[];
  exceptions: AvailabilityException[];
}

/** A service's own weekly-hours override — see ServiceAvailabilityOverrideResponse's backend doc comment. When hasCustomAvailability is false, the service simply inherits the store's weekly template (weeklyRules is then empty). */
export interface ServiceAvailabilityOverride {
  hasCustomAvailability: boolean;
  weeklyRules: WeeklyAvailabilityRule[];
}

export interface ServiceAvailabilityOverrideInput {
  rules: WeeklyAvailabilityRule[];
}

export interface SlotResponse {
  start: string;
  end: string;
}

export interface DayAvailability {
  date: string;
  slots: SlotResponse[];
}

export type BookingStatus = "pending" | "confirmed" | "completed" | "cancelled" | "no-show";

export interface BookingTimelineEntry {
  status: BookingStatus;
  label: string;
  timestamp: string;
  note?: string;
}

/** Mirrors backend Booking — parallel aggregate to Order, no delivery/shipping concepts. */
export interface Booking {
  id: string;
  bookingNumber: string;
  storeId: string;
  storeName: string;
  storeSlug: string;
  serviceId: string;
  serviceName: string;
  servicePrice: number;
  serviceDurationMinutes: number;
  scheduledStart: string;
  scheduledEnd: string;
  platformFee: number;
  total: number;
  status: BookingStatus;
  paymentMethod: PaymentMethod;
  paymentStatus: PaymentStatus;
  /** Set once the buyer uploads a bank-transfer proof-of-payment; only ever present for paymentMethod === "bank-transfer". */
  receiptUrl?: string;
  buyerName: string;
  buyerPhone: string;
  buyerEmail: string;
  /** Set only when the buyer was signed into a buyer account at checkout. */
  buyerId?: string;
  cancellationReason?: string;
  timeline: BookingTimelineEntry[];
  createdAt: string;
}

/** No buyerId field — a signed-in buyer's booking is linked server-side from their auth cookie, never a client-supplied id, same reasoning as CheckoutInput. */
export interface CheckoutBookingInput {
  storeId: string;
  serviceId: string;
  scheduledStart: string;
  paymentMethod: PaymentMethod;
  buyerName: string;
  buyerPhone: string;
  buyerEmail: string;
}

export interface BookingStatusUpdateInput {
  status: BookingStatus;
  note?: string;
}

export interface VerifyBookingBankTransferInput {
  approved: boolean;
  note?: string;
}

export interface CancelBookingInput {
  reason?: string;
}
