import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { Alert, FlatList, RefreshControl, StyleSheet, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { deleteCoupon, listStoreCoupons } from '@/api/coupons';
import { getMyStore } from '@/api/stores';
import type { CouponResponse } from '@/api/types';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-client';
import { formatDate, formatMoney } from '@/lib/format';

function discountLabel(coupon: CouponResponse): string {
  return coupon.discountType === 'percent' ? `${coupon.discountValue}% off` : `${formatMoney(coupon.discountValue)} off`;
}

function CouponRow({ item, storeId }: { item: CouponResponse; storeId: string }) {
  const theme = useTheme();
  const queryClient = useQueryClient();

  const deleteMutation = useMutation({
    mutationFn: () => deleteCoupon(item.id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['store', storeId, 'coupons'] }),
    onError: (e) => Alert.alert('Could not delete coupon', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  const confirmDelete = () => {
    Alert.alert('Delete coupon', `Delete "${item.code}"?`, [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Delete', style: 'destructive', onPress: () => deleteMutation.mutate() },
    ]);
  };

  return (
    <View style={[styles.row, { backgroundColor: theme.backgroundElement }]}>
      <View style={styles.rowTop}>
        <ThemedText type="smallBold">{item.code}</ThemedText>
        <View style={[styles.badge, { backgroundColor: item.active ? '#1E9E5A' : '#60646C' }]}>
          <ThemedText style={styles.badgeText}>{item.active ? 'Active' : 'Inactive'}</ThemedText>
        </View>
      </View>
      <ThemedText type="small" themeColor="textSecondary">
        {discountLabel(item)} · used {item.usedCount}
        {item.maxUses ? `/${item.maxUses}` : ''}
        {item.expiresAt ? ` · expires ${formatDate(item.expiresAt)}` : ''}
      </ThemedText>
      <TouchableOpacity onPress={confirmDelete} style={styles.deleteLink}>
        <ThemedText style={styles.deleteText}>Delete</ThemedText>
      </TouchableOpacity>
    </View>
  );
}

export default function CouponsListScreen() {
  const theme = useTheme();
  const router = useRouter();
  const storeQuery = useQuery({ queryKey: ['me', 'store'], queryFn: getMyStore });
  const storeId = storeQuery.data?.id;

  const couponsQuery = useQuery({
    queryKey: ['store', storeId, 'coupons'],
    queryFn: () => listStoreCoupons(storeId!),
    enabled: !!storeId,
  });

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <FlatList
        data={couponsQuery.data ?? []}
        keyExtractor={(c) => c.id}
        contentContainerStyle={styles.list}
        refreshControl={<RefreshControl refreshing={couponsQuery.isFetching} onRefresh={() => couponsQuery.refetch()} />}
        renderItem={({ item }) => <CouponRow item={item} storeId={storeId!} />}
        ListEmptyComponent={
          !couponsQuery.isLoading ? (
            <ThemedText themeColor="textSecondary" style={styles.empty}>
              No coupons yet — add your first one below.
            </ThemedText>
          ) : null
        }
      />
      <TouchableOpacity style={styles.addButton} onPress={() => router.push('/dashboard/settings/coupons/new')}>
        <ThemedText style={styles.addButtonText}>+ Add coupon</ThemedText>
      </TouchableOpacity>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  list: { padding: Spacing.three, gap: Spacing.two },
  row: { borderRadius: 14, padding: Spacing.three, gap: Spacing.half },
  rowTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  badge: { paddingHorizontal: Spacing.two, paddingVertical: 2, borderRadius: 999 },
  badgeText: { color: '#fff', fontSize: 12, fontWeight: '600' },
  deleteLink: { marginTop: Spacing.half, alignSelf: 'flex-start' },
  deleteText: { color: '#D64545', fontWeight: '600' },
  empty: { textAlign: 'center', marginTop: Spacing.six },
  addButton: { margin: Spacing.three, height: 50, borderRadius: 12, backgroundColor: '#208AEF', alignItems: 'center', justifyContent: 'center' },
  addButtonText: { color: '#fff', fontWeight: '700' },
});
