import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { FlatList, StyleSheet, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { listStoreProducts, productToFormInput, updateProduct } from '@/api/products';
import { getMyStore } from '@/api/stores';
import type { ProductResponse } from '@/api/types';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { formatMoney } from '@/lib/format';

const STATUS_COLORS: Record<string, string> = {
  active: '#1E9E5A',
  draft: '#60646C',
  'out-of-stock': '#D64545',
};

function ProductRow({ product, onPress, onAdjustStock }: { product: ProductResponse; onPress: () => void; onAdjustStock: (delta: number) => void }) {
  const theme = useTheme();
  return (
    <View style={[styles.row, { backgroundColor: theme.backgroundElement }]}>
      <TouchableOpacity onPress={onPress} style={styles.rowMain}>
        {product.images[0] ? (
          <Image source={{ uri: product.images[0].url }} style={styles.thumb} contentFit="cover" />
        ) : (
          <View style={[styles.thumb, { backgroundColor: theme.backgroundSelected }]} />
        )}
        <View style={styles.rowText}>
          <ThemedText type="smallBold" numberOfLines={1}>
            {product.name}
          </ThemedText>
          <ThemedText type="small" themeColor="textSecondary">
            {formatMoney(product.price)}
          </ThemedText>
          <ThemedText type="small" style={{ color: STATUS_COLORS[product.status] }}>
            {product.status}
          </ThemedText>
        </View>
      </TouchableOpacity>
      {product.trackStock ? (
        <View style={styles.stepper}>
          <TouchableOpacity
            style={[styles.stepperButton, { backgroundColor: theme.backgroundSelected }]}
            disabled={product.stockQuantity <= 0}
            onPress={() => onAdjustStock(-1)}>
            <ThemedText type="smallBold">-</ThemedText>
          </TouchableOpacity>
          <ThemedText type="smallBold" style={styles.stockValue}>
            {product.stockQuantity}
          </ThemedText>
          <TouchableOpacity style={[styles.stepperButton, { backgroundColor: theme.backgroundSelected }]} onPress={() => onAdjustStock(1)}>
            <ThemedText type="smallBold">+</ThemedText>
          </TouchableOpacity>
        </View>
      ) : null}
    </View>
  );
}

export default function ProductsListScreen() {
  const theme = useTheme();
  const router = useRouter();
  const queryClient = useQueryClient();

  const storeQuery = useQuery({ queryKey: ['me', 'store'], queryFn: getMyStore });
  const storeId = storeQuery.data?.id;

  const productsQuery = useQuery({
    queryKey: ['store', storeId, 'products'],
    queryFn: () => listStoreProducts(storeId!),
    enabled: !!storeId,
  });

  const stockMutation = useMutation({
    mutationFn: ({ product, delta }: { product: ProductResponse; delta: number }) =>
      updateProduct(product.id, { ...productToFormInput(product), stockQuantity: Math.max(0, product.stockQuantity + delta) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['store', storeId, 'products'] }),
  });

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <FlatList
        data={productsQuery.data ?? []}
        keyExtractor={(p) => p.id}
        contentContainerStyle={styles.list}
        renderItem={({ item }) => (
          <ProductRow
            product={item}
            onPress={() => router.push(`/products/${item.id}`)}
            onAdjustStock={(delta) => stockMutation.mutate({ product: item, delta })}
          />
        )}
        ListEmptyComponent={
          !productsQuery.isLoading ? (
            <ThemedText themeColor="textSecondary" style={styles.empty}>
              No products yet — add your first one below.
            </ThemedText>
          ) : null
        }
      />
      <TouchableOpacity style={styles.fab} onPress={() => router.push('/products/new')}>
        <ThemedText style={styles.fabText}>+ Add product</ThemedText>
      </TouchableOpacity>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  list: { padding: Spacing.three, gap: Spacing.two, paddingBottom: Spacing.six },
  row: { borderRadius: 14, padding: Spacing.two, flexDirection: 'row', alignItems: 'center', gap: Spacing.two },
  rowMain: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: Spacing.two },
  thumb: { width: 56, height: 56, borderRadius: 10 },
  rowText: { flex: 1, gap: 2 },
  stepper: { flexDirection: 'row', alignItems: 'center', gap: Spacing.one },
  stepperButton: { width: 32, height: 32, borderRadius: 8, alignItems: 'center', justifyContent: 'center' },
  stockValue: { minWidth: 24, textAlign: 'center' },
  empty: { textAlign: 'center', marginTop: Spacing.six },
  fab: {
    position: 'absolute',
    bottom: Spacing.four,
    left: Spacing.three,
    right: Spacing.three,
    height: 50,
    borderRadius: 12,
    backgroundColor: '#208AEF',
    alignItems: 'center',
    justifyContent: 'center',
  },
  fabText: { color: '#fff', fontWeight: '700' },
});
