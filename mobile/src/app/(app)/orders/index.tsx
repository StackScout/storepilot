import { useQuery } from '@tanstack/react-query';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { FlatList, RefreshControl, ScrollView, StyleSheet, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { listStoreOrders } from '@/api/orders';
import { getMyStore } from '@/api/stores';
import type { OrderResponse, OrderStatus } from '@/api/types';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { formatDateTime, formatMoney, orderStatusColor, orderStatusLabel } from '@/lib/format';

const STATUS_FILTERS: { label: string; value: OrderStatus | undefined }[] = [
  { label: 'All', value: undefined },
  { label: 'Pending', value: 'pending' },
  { label: 'Confirmed', value: 'confirmed' },
  { label: 'Shipped', value: 'shipped' },
  { label: 'Delivered', value: 'delivered' },
  { label: 'Cancelled', value: 'cancelled' },
];

function OrderRow({ order, onPress }: { order: OrderResponse; onPress: () => void }) {
  const theme = useTheme();
  return (
    <TouchableOpacity onPress={onPress} style={[styles.row, { backgroundColor: theme.backgroundElement }]}>
      <View style={styles.rowTop}>
        <ThemedText type="smallBold">{order.orderNumber}</ThemedText>
        <View style={[styles.badge, { backgroundColor: orderStatusColor(order.status) }]}>
          <ThemedText style={styles.badgeText}>{orderStatusLabel(order.status)}</ThemedText>
        </View>
      </View>
      <ThemedText type="small" themeColor="textSecondary">
        {formatDateTime(order.createdAt)}
      </ThemedText>
      <View style={styles.rowBottom}>
        <ThemedText type="small" themeColor="textSecondary">
          {order.items.length} item{order.items.length === 1 ? '' : 's'}
        </ThemedText>
        <ThemedText type="smallBold">{formatMoney(order.total)}</ThemedText>
      </View>
    </TouchableOpacity>
  );
}

export default function OrdersListScreen() {
  const theme = useTheme();
  const router = useRouter();
  // Lets the dashboard's "Pending orders" stat deep-link straight into a
  // pre-filtered view — e.g. /orders?status=pending. Only read once on
  // mount so switching filters afterward (via the chips below) isn't
  // fought by a stale param on re-render.
  const { status: initialStatus } = useLocalSearchParams<{ status?: OrderStatus }>();
  const [statusFilter, setStatusFilter] = useState<OrderStatus | undefined>(initialStatus);
  useEffect(() => {
    if (initialStatus) setStatusFilter(initialStatus);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only react to the param actually changing, not statusFilter
  }, [initialStatus]);

  const storeQuery = useQuery({ queryKey: ['me', 'store'], queryFn: getMyStore });
  const storeId = storeQuery.data?.id;

  const ordersQuery = useQuery({
    queryKey: ['store', storeId, 'orders', statusFilter],
    queryFn: () => listStoreOrders(storeId!, 0, 20, statusFilter),
    enabled: !!storeId,
  });

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.filterRow}>
        {STATUS_FILTERS.map((filter) => {
          const selected = statusFilter === filter.value;
          return (
            <TouchableOpacity
              key={filter.label}
              style={[
                styles.filterChip,
                { borderColor: theme.textSecondary },
                selected && { backgroundColor: theme.text, borderColor: theme.text },
              ]}
              onPress={() => setStatusFilter(filter.value)}>
              <ThemedText type="small" style={selected ? { color: theme.background } : undefined}>
                {filter.label}
              </ThemedText>
            </TouchableOpacity>
          );
        })}
      </ScrollView>
      <FlatList
        data={ordersQuery.data?.content ?? []}
        keyExtractor={(order) => order.id}
        contentContainerStyle={styles.list}
        refreshControl={<RefreshControl refreshing={ordersQuery.isFetching} onRefresh={() => ordersQuery.refetch()} />}
        renderItem={({ item }) => <OrderRow order={item} onPress={() => router.push(`/orders/${item.id}`)} />}
        ListEmptyComponent={
          !ordersQuery.isLoading ? (
            <ThemedText themeColor="textSecondary" style={styles.empty}>
              {statusFilter ? `No ${orderStatusLabel(statusFilter).toLowerCase()} orders.` : 'No orders yet.'}
            </ThemedText>
          ) : null
        }
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  filterRow: { gap: Spacing.two, paddingHorizontal: Spacing.three, paddingVertical: Spacing.two },
  filterChip: { height: 34, paddingHorizontal: Spacing.three, borderRadius: 17, borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
  list: { padding: Spacing.three, paddingTop: 0, gap: Spacing.two },
  row: { borderRadius: 14, padding: Spacing.three, gap: Spacing.half },
  rowTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  rowBottom: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: Spacing.half },
  badge: { paddingHorizontal: Spacing.two, paddingVertical: 2, borderRadius: 999 },
  badgeText: { color: '#fff', fontSize: 12, fontWeight: '600' },
  empty: { textAlign: 'center', marginTop: Spacing.six },
});
