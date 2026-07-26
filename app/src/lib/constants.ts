export const SITE_NAME = "IslandCart";
export const SITE_TAGLINE = "Sri Lanka's marketplace for small business sellers";

export const PLATFORM_FEE_PERCENT = 3.5;
export const FLAT_SHIPPING_FEE_LKR = 350;

export const SRI_LANKA_DISTRICTS = [
  "Colombo",
  "Gampaha",
  "Kalutara",
  "Kandy",
  "Matale",
  "Nuwara Eliya",
  "Galle",
  "Matara",
  "Hambantota",
  "Jaffna",
  "Kurunegala",
  "Puttalam",
  "Anuradhapura",
  "Polonnaruwa",
  "Badulla",
  "Ratnapura",
  "Kegalle",
] as const;

/** Province for each district in SRI_LANKA_DISTRICTS — used to derive Store.address.province at onboarding. */
export const DISTRICT_TO_PROVINCE: Record<string, string> = {
  Colombo: "Western",
  Gampaha: "Western",
  Kalutara: "Western",
  Kandy: "Central",
  Matale: "Central",
  "Nuwara Eliya": "Central",
  Galle: "Southern",
  Matara: "Southern",
  Hambantota: "Southern",
  Jaffna: "Northern",
  Kurunegala: "North Western",
  Puttalam: "North Western",
  Anuradhapura: "North Central",
  Polonnaruwa: "North Central",
  Badulla: "Uva",
  Ratnapura: "Sabaragamuwa",
  Kegalle: "Sabaragamuwa",
};

export const ORDER_STATUS_LABELS: Record<string, string> = {
  pending: "Pending",
  confirmed: "Confirmed",
  shipped: "Shipped",
  delivered: "Delivered",
  cancelled: "Cancelled",
};
