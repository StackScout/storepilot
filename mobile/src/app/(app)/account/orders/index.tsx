import { useQuery } from '@tanstack/react-query';
import { router, type Href } from 'expo-router';
import { FlatList, StyleSheet, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { listMyOrders } from '@/api/buyer-orders';
import { EmptyState } from '@/components/empty-state';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { formatDate, orderStatusColor, orderStatusLabel } from '@/lib/format';
import { formatCurrency, usePlatformConfig } from '@/lib/platform-config';

export default function OrderHistoryScreen() {
  const theme = useTheme();
  const platformConfig = usePlatformConfig();
  const ordersQuery = useQuery({ queryKey: ['me', 'orders'], queryFn: listMyOrders });

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <FlatList
        data={ordersQuery.data ?? []}
        keyExtractor={(o) => o.id}
        contentContainerStyle={styles.list}
        ListEmptyComponent={!ordersQuery.isLoading ? <EmptyState title="No orders yet" /> : null}
        renderItem={({ item }) => (
          <TouchableOpacity style={[styles.row, { borderColor: theme.backgroundElement }]} onPress={() => router.push(`/account/orders/${item.id}` as Href)}>
            <View style={styles.rowInfo}>
              <ThemedText type="smallBold">{item.orderNumber}</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                {item.storeName} · {formatDate(item.createdAt)}
              </ThemedText>
            </View>
            <View style={styles.rowRight}>
              <ThemedText type="smallBold">{formatCurrency(item.total, platformConfig)}</ThemedText>
              <ThemedText type="small" style={{ color: orderStatusColor(item.status) }}>
                {orderStatusLabel(item.status)}
              </ThemedText>
            </View>
          </TouchableOpacity>
        )}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  list: { padding: Spacing.three, gap: Spacing.two },
  row: { flexDirection: 'row', justifyContent: 'space-between', borderWidth: 1, borderRadius: 12, padding: Spacing.three },
  rowInfo: { gap: 2 },
  rowRight: { alignItems: 'flex-end', gap: 2 },
});
