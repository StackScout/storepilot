import { useQuery } from '@tanstack/react-query';
import { FlatList, RefreshControl, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { listStorePayouts } from '@/api/payouts';
import { getMyStore } from '@/api/stores';
import type { PayoutResponse } from '@/api/types';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { formatDateTime, formatMoney } from '@/lib/format';

function PayoutRow({ item }: { item: PayoutResponse }) {
  const theme = useTheme();
  return (
    <View style={[styles.row, { backgroundColor: theme.backgroundElement }]}>
      <View style={styles.rowTop}>
        <ThemedText type="smallBold">{formatMoney(item.net)}</ThemedText>
        <View style={[styles.badge, { backgroundColor: item.status === 'paid' ? '#1E9E5A' : '#B98900' }]}>
          <ThemedText style={styles.badgeText}>{item.status === 'paid' ? 'Paid' : 'Scheduled'}</ThemedText>
        </View>
      </View>
      <ThemedText type="small" themeColor="textSecondary">
        {item.orders.length} order{item.orders.length === 1 ? '' : 's'} · {formatDateTime(item.createdAt)}
      </ThemedText>
      {item.paidAt ? (
        <ThemedText type="small" themeColor="textSecondary">
          Paid {formatDateTime(item.paidAt)} {item.bankReference ? `· ${item.bankReference}` : ''}
        </ThemedText>
      ) : null}
    </View>
  );
}

export default function PayoutsScreen() {
  const theme = useTheme();
  const storeQuery = useQuery({ queryKey: ['me', 'store'], queryFn: getMyStore });
  const storeId = storeQuery.data?.id;

  const payoutsQuery = useQuery({
    queryKey: ['store', storeId, 'payouts'],
    queryFn: () => listStorePayouts(storeId!),
    enabled: !!storeId,
  });

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <FlatList
        data={payoutsQuery.data ?? []}
        keyExtractor={(p) => p.id}
        contentContainerStyle={styles.list}
        refreshControl={<RefreshControl refreshing={payoutsQuery.isFetching} onRefresh={() => payoutsQuery.refetch()} />}
        renderItem={({ item }) => <PayoutRow item={item} />}
        ListEmptyComponent={
          !payoutsQuery.isLoading ? (
            <ThemedText themeColor="textSecondary" style={styles.empty}>
              No payouts yet.
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
  badge: { paddingHorizontal: Spacing.two, paddingVertical: 2, borderRadius: 999 },
  badgeText: { color: '#fff', fontSize: 12, fontWeight: '600' },
  empty: { textAlign: 'center', marginTop: Spacing.six },
});
