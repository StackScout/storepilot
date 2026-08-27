import { useQuery } from '@tanstack/react-query';
import { useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { FlatList, StyleSheet, TextInput, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { listProducts, type ProductQueryParams } from '@/api/buyer-products';
import { EmptyState } from '@/components/empty-state';
import { ProductTile } from '@/components/product-tile';
import { ThemedText } from '@/components/themed-text';
import { CATEGORIES } from '@/constants/categories';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import type { StoreCategory } from '@storepilot/shared-api';

export default function SearchScreen() {
  const theme = useTheme();
  const params = useLocalSearchParams<{ category?: string; query?: string }>();
  const [query, setQuery] = useState(params.query ?? '');
  const [category, setCategory] = useState<StoreCategory | undefined>(params.category as StoreCategory | undefined);

  const searchParams: ProductQueryParams = { query: query.trim() || undefined, category };
  const productsQuery = useQuery({
    queryKey: ['products', 'search', searchParams],
    queryFn: () => listProducts(searchParams),
  });

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <View style={styles.searchBar}>
        <TextInput
          style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
          placeholder="Search products, stores, categories..."
          placeholderTextColor={theme.textSecondary}
          value={query}
          onChangeText={setQuery}
          returnKeyType="search"
        />
      </View>
      <FlatList
        horizontal
        showsHorizontalScrollIndicator={false}
        data={CATEGORIES}
        keyExtractor={(c) => c.value}
        contentContainerStyle={styles.categoryRow}
        renderItem={({ item }) => {
          const selected = category === item.value;
          return (
            <TouchableOpacity
              style={[styles.categoryChip, { borderColor: selected ? theme.text : theme.textSecondary, backgroundColor: selected ? theme.backgroundElement : 'transparent' }]}
              onPress={() => setCategory(selected ? undefined : item.value)}>
              <ThemedText type="small">{item.label}</ThemedText>
            </TouchableOpacity>
          );
        }}
      />
      <FlatList
        data={productsQuery.data?.content ?? []}
        keyExtractor={(p) => p.id}
        numColumns={2}
        columnWrapperStyle={styles.gridRow}
        contentContainerStyle={styles.grid}
        ListEmptyComponent={
          !productsQuery.isLoading ? <EmptyState title="No products found" description="Try a different search or category." /> : null
        }
        renderItem={({ item }) => (
          <View style={styles.gridItem}>
            <ProductTile product={item} />
          </View>
        )}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  searchBar: { paddingHorizontal: Spacing.three, paddingTop: Spacing.two },
  input: { height: 44, borderRadius: 10, paddingHorizontal: Spacing.three, fontSize: 16 },
  categoryRow: { gap: Spacing.two, paddingHorizontal: Spacing.three, paddingVertical: Spacing.two },
  categoryChip: { borderWidth: 1, borderRadius: 20, paddingHorizontal: Spacing.three, height: 36, alignItems: 'center', justifyContent: 'center' },
  grid: { paddingHorizontal: Spacing.three, paddingBottom: Spacing.six, gap: Spacing.three },
  gridRow: { gap: Spacing.three },
  gridItem: { flex: 1 },
});
