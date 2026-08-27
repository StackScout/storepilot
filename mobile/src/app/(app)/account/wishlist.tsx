import { useQuery } from '@tanstack/react-query';
import { FlatList, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { listMyWishlist } from '@/api/buyer-products';
import { EmptyState } from '@/components/empty-state';
import { ProductTile } from '@/components/product-tile';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export default function WishlistScreen() {
  const theme = useTheme();
  const wishlistQuery = useQuery({ queryKey: ['me', 'wishlist'], queryFn: listMyWishlist });

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <FlatList
        data={wishlistQuery.data ?? []}
        keyExtractor={(p) => p.id}
        numColumns={2}
        columnWrapperStyle={styles.row}
        contentContainerStyle={styles.grid}
        ListEmptyComponent={!wishlistQuery.isLoading ? <EmptyState title="Your wishlist is empty" /> : null}
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
  grid: { padding: Spacing.three, gap: Spacing.three },
  row: { gap: Spacing.three },
  gridItem: { flex: 1 },
});
