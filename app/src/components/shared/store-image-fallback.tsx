import { getStoreAvatarColor, getStoreInitials } from "@/lib/store-avatar";

/** Fills its positioned parent (matches next/image's `fill` sizing) with a colored initials circle — shown in place of the logo <Image> when store.logoUrl is null. */
export function StoreLogoFallback({ name, className }: { name: string; className?: string }) {
  return (
    <div
      className={`absolute inset-0 flex items-center justify-center font-semibold text-white ${className ?? ""}`}
      style={{ backgroundColor: getStoreAvatarColor(name) }}
      aria-hidden="true"
    >
      {getStoreInitials(name)}
    </div>
  );
}

/** Fills its positioned parent with a solid color block (same seeded color as the logo) — shown in place of the banner <Image> when store.bannerUrl is null. */
export function StoreBannerFallback({ name, className }: { name: string; className?: string }) {
  return (
    <div
      className={`absolute inset-0 ${className ?? ""}`}
      style={{ backgroundColor: getStoreAvatarColor(name) }}
      aria-hidden="true"
    />
  );
}
