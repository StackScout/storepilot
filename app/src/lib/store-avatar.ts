/**
 * Deterministic "generated avatar" for a store with no uploaded logo/banner
 * yet — same store always gets the same initials + color, computed
 * client-side from the store name/slug rather than a stored image. See
 * StoreLogoFallback/StoreBannerFallback in components/shared/store-image-fallback.tsx.
 */

// A muted, evenly-spaced palette that reads fine with white text in both
// light and dark mode — same idea as Slack/Gmail's generated-avatar colors.
const AVATAR_COLORS = [
  "#f97316", // orange
  "#ef4444", // red
  "#ec4899", // pink
  "#a855f7", // purple
  "#6366f1", // indigo
  "#3b82f6", // blue
  "#06b6d4", // cyan
  "#14b8a6", // teal
  "#22c55e", // green
  "#84cc16", // lime
  "#eab308", // yellow
  "#f59e0b", // amber
];

/** First letters of the first two words, or the first two letters of a single word. */
export function getStoreInitials(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return "?";
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
  return (words[0][0] + words[1][0]).toUpperCase();
}

/** Simple string hash → stable index into AVATAR_COLORS, so the same seed always picks the same color. */
export function getStoreAvatarColor(seed: string): string {
  let hash = 0;
  for (let i = 0; i < seed.length; i++) {
    hash = seed.charCodeAt(i) + ((hash << 5) - hash);
  }
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length];
}
