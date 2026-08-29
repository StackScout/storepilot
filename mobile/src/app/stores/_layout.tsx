import { router, Stack } from 'expo-router';
import { TouchableOpacity } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { useTheme } from '@/hooks/use-theme';

/**
 * Every screen in this group is reached by pushing directly into a deep
 * path from a tile tap on Home/Search (store/product/service card) — never
 * by first visiting `[slug]/index` — so whichever one you land on is the
 * first entry in this Stack's own history, and React Navigation correctly
 * shows no back button for it. The outer root Stack's header (which would
 * otherwise let you back out to the tabs) is hidden globally
 * (`app/_layout.tsx`'s `headerShown: false`), so without this, these
 * screens have no way back at all. `router.back()` pops correctly
 * regardless of which nested navigator actually owns the current screen.
 */
function BackButton() {
  const theme = useTheme();
  return (
    <TouchableOpacity onPress={() => router.back()} hitSlop={12} style={{ paddingRight: 8 }}>
      <ThemedText style={{ fontSize: 28, color: theme.text }}>‹</ThemedText>
    </TouchableOpacity>
  );
}

export default function StoresLayout() {
  return (
    <Stack screenOptions={{ headerLeft: () => <BackButton /> }}>
      <Stack.Screen name="[slug]/index" options={{ title: '' }} />
      <Stack.Screen name="[slug]/products/[productSlug]" options={{ title: '' }} />
      <Stack.Screen name="[slug]/services/[serviceSlug]" options={{ title: '' }} />
    </Stack>
  );
}
