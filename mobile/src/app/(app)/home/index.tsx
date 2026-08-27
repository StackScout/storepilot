import { useQuery } from '@tanstack/react-query';
import { type Href, useRouter } from 'expo-router';
import { FlatList, RefreshControl, ScrollView, StyleSheet, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { listCategories } from '@/api/categories';
import { getFeaturedProducts } from '@/api/buyer-products';
import { listStores } from '@/api/buyer-stores';
import { ProductTile } from '@/components/product-tile';
import { StoreTile } from '@/components/store-tile';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { usePlatformConfig } from '@/lib/platform-config';

export default function HomeScreen() {
  const theme = useTheme();
  const router = useRouter();
  const platformConfig = usePlatformConfig();

  const featuredQuery = useQuery({ queryKey: ['products', 'featured'], queryFn: () => getFeaturedProducts(8) });
  const storesQuery = useQuery({ queryKey: ['stores', 'popular'], queryFn: () => listStores({ size: 6 }) });
  const categoriesQuery = useQuery({ queryKey: ['categories'], queryFn: listCategories, staleTime: 5 * 60_000 });

  const isRefreshing = featuredQuery.isFetching || storesQuery.isFetching;
  const onRefresh = () => {
    featuredQuery.refetch();
    storesQuery.refetch();
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <ScrollView
        contentContainerStyle={styles.container}
        refreshControl={<RefreshControl refreshing={isRefreshing} onRefresh={onRefresh} />}>
        <ThemedText type="title" style={styles.hero}>
          Shop directly from {platformConfig.countryName}&apos;s small businesses
        </ThemedText>

        <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
          SHOP BY CATEGORY
        </ThemedText>
        <FlatList
          horizontal
          showsHorizontalScrollIndicator={false}
          data={categoriesQuery.data ?? []}
          keyExtractor={(c) => c.id}
          contentContainerStyle={styles.categoryRow}
          renderItem={({ item }) => (
            <TouchableOpacity
              style={[styles.categoryChip, { borderColor: theme.textSecondary }]}
              onPress={() => router.push({ pathname: '/search', params: { category: item.wireValue } } as unknown as Href)}>
              <ThemedText type="small">{item.name}</ThemedText>
            </TouchableOpacity>
          )}
        />

        <View style={styles.sectionHeader}>
          <ThemedText type="smallBold" themeColor="textSecondary">
            FEATURED PRODUCTS
          </ThemedText>
          <TouchableOpacity onPress={() => router.push('/search' as Href)}>
            <ThemedText type="small" themeColor="textSecondary">
              View all
            </ThemedText>
          </TouchableOpacity>
        </View>
        <FlatList
          horizontal
          showsHorizontalScrollIndicator={false}
          data={featuredQuery.data ?? []}
          keyExtractor={(p) => p.id}
          contentContainerStyle={styles.productRow}
          renderItem={({ item }) => <ProductTile product={item} />}
        />

        <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
          POPULAR STORES
        </ThemedText>
        {(storesQuery.data?.content ?? []).map((store) => (
          <StoreTile key={store.id} store={store} />
        ))}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { padding: Spacing.three, gap: Spacing.two, paddingBottom: Spacing.six },
  hero: { fontSize: 26, lineHeight: 32, marginBottom: Spacing.two },
  sectionLabel: { marginTop: Spacing.three },
  sectionHeader: { marginTop: Spacing.three, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  categoryRow: { gap: Spacing.two, paddingVertical: Spacing.half },
  categoryChip: { borderWidth: 1, borderRadius: 20, paddingHorizontal: Spacing.three, height: 36, alignItems: 'center', justifyContent: 'center' },
  productRow: { gap: Spacing.three, paddingVertical: Spacing.half },
});
