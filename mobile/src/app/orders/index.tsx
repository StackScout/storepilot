import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { FlatList, RefreshControl, StyleSheet, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { listStoreOrders } from '@/api/orders';
import { getMyStore } from '@/api/stores';
import type { OrderResponse } from '@/api/types';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { formatDateTime, formatMoney, orderStatusColor, orderStatusLabel } from '@/lib/format';

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

  const storeQuery = useQuery({ queryKey: ['me', 'store'], queryFn: getMyStore });
  const storeId = storeQuery.data?.id;

  const ordersQuery = useQuery({
    queryKey: ['store', storeId, 'orders'],
    queryFn: () => listStoreOrders(storeId!),
    enabled: !!storeId,
  });

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <FlatList
        data={ordersQuery.data?.content ?? []}
        keyExtractor={(order) => order.id}
        contentContainerStyle={styles.list}
        refreshControl={<RefreshControl refreshing={ordersQuery.isFetching} onRefresh={() => ordersQuery.refetch()} />}
        renderItem={({ item }) => <OrderRow order={item} onPress={() => router.push(`/orders/${item.id}`)} />}
        ListEmptyComponent={
          !ordersQuery.isLoading ? (
            <ThemedText themeColor="textSecondary" style={styles.empty}>
              No orders yet.
            </ThemedText>
          ) : null
        }
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  list: { padding: Spacing.three, gap: Spacing.two },
  row: { borderRadius: 14, padding: Spacing.three, gap: Spacing.half },
  rowTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  rowBottom: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: Spacing.half },
  badge: { paddingHorizontal: Spacing.two, paddingVertical: 2, borderRadius: 999 },
  badgeText: { color: '#fff', fontSize: 12, fontWeight: '600' },
  empty: { textAlign: 'center', marginTop: Spacing.six },
});
