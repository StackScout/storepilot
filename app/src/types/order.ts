export type OrderStatus =
  | "pending"
  | "confirmed"
  | "shipped"
  | "delivered"
  | "cancelled";

export type PaymentMethod = "payhere" | "cod" | "bank-transfer" | "stripe";

export type PaymentStatus = "unpaid" | "paid" | "refunded";

export interface OrderItem {
  productId: string;
  productName: string;
  productImageUrl: string;
  unitPrice: number;
  quantity: number;
}

export interface OrderTimelineEntry {
  status: OrderStatus;
  label: string;
  timestamp: string;
  note?: string;
}

export interface ShippingDetails {
  fullName: string;
  phone: string;
  addressLine1: string;
  city: string;
  state: string;
  postalCode: string;
}

export interface Order {
  id: string;
  orderNumber: string;
  storeId: string;
  storeName: string;
  storeSlug: string;
  items: OrderItem[];
  subtotal: number;
  shippingFee: number;
  platformFee: number;
  total: number;
  status: OrderStatus;
  paymentMethod: PaymentMethod;
  paymentStatus: PaymentStatus;
  /** Set once the buyer uploads a bank-transfer proof-of-payment; only ever present for paymentMethod === "bank-transfer". */
  receiptUrl?: string;
  /** Set together when the seller marks the order shipped — both required at that transition. */
  trackingNumber?: string;
  courierServiceName?: string;
  /** Optional proof-of-handover upload from the seller. */
  courierReceiptUrl?: string;
  shipping: ShippingDetails;
  timeline: OrderTimelineEntry[];
  createdAt: string;
  /** Where the order receipt is sent — collected at checkout, guest or not. */
  buyerEmail: string;
  /** Set only when the buyer was signed into a buyer account at checkout. */
  buyerId?: string;
}

/** No buyerId field — a signed-in buyer's order is linked server-side from their auth cookie, never a client-supplied id. */
export interface CheckoutInput {
  storeId: string;
  items: { productId: string; quantity: number }[];
  shipping: ShippingDetails;
  paymentMethod: PaymentMethod;
  email: string;
}

/** Server-generated payload for PayHere's Checkout API form redirect — hash is computed backend-side so the merchant secret never reaches the browser. */
export interface PayHereCheckoutPayload {
  actionUrl: string;
  merchantId: string;
  orderId: string;
  items: string;
  amount: string;
  currency: string;
  hash: string;
  notifyUrl: string;
  returnUrl: string;
  cancelUrl: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  address: string;
  city: string;
  country: string;
}
