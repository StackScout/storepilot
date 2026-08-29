import { useQuery } from '@tanstack/react-query';
import { Image } from 'expo-image';
import { type Href, useRouter } from 'expo-router';
import { StyleSheet, TouchableOpacity, View } from 'react-native';

import type { Store } from '@storepilot/shared-api';

import { listCategories } from '@/api/categories';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export function StoreTile({ store }: { store: Store }) {
  const theme = useTheme();
  const router = useRouter();
  const categoriesQuery = useQuery({ queryKey: ['categories'], queryFn: listCategories, staleTime: 5 * 60_000 });
  const categoryLabel = categoriesQuery.data?.find((c) => c.wireValue === store.category)?.name ?? store.category;

  return (
    <TouchableOpacity style={styles.container} onPress={() => router.push(`/stores/${store.slug}` as Href)}>
      {store.logoUrl ? (
        <Image source={{ uri: store.logoUrl }} style={[styles.logo, { backgroundColor: theme.backgroundElement }]} contentFit="cover" />
      ) : (
        <View style={[styles.logo, { backgroundColor: theme.backgroundElement }]} />
      )}
      <View style={styles.info}>
        <ThemedText type="smallBold" numberOfLines={1}>
          {store.name}
        </ThemedText>
        <ThemedText type="small" themeColor="textSecondary" numberOfLines={1}>
          {categoryLabel} · {store.address.city}
        </ThemedText>
        <ThemedText type="small" themeColor="textSecondary">
          {store.rating.toFixed(1)} ★ ({store.reviewCount})
        </ThemedText>
      </View>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  container: { flexDirection: 'row', alignItems: 'center', gap: Spacing.two, paddingVertical: Spacing.two },
  logo: { width: 56, height: 56, borderRadius: 12 },
  info: { flex: 1, gap: 2 },
});
