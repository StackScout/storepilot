import { useQuery } from '@tanstack/react-query';
import { FlatList, RefreshControl, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { listStoreFeeCollections } from '@/api/fee-collections';
import { getMyStore } from '@/api/stores';
import type { FeeCollectionResponse } from '@/api/types';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { formatDateTime, formatMoney } from '@/lib/format';
import { usePlatformConfig } from '@/lib/platform-config';

function FeeCollectionRow({ item }: { item: FeeCollectionResponse }) {
  const theme = useTheme();
  return (
    <View style={[styles.row, { backgroundColor: theme.backgroundElement }]}>
      <View style={styles.rowTop}>
        <ThemedText type="smallBold">{formatMoney(item.platformFee)}</ThemedText>
        <View style={[styles.badge, { backgroundColor: item.status === 'collected' ? '#1E9E5A' : '#B98900' }]}>
          <ThemedText style={styles.badgeText}>{item.status === 'collected' ? 'Collected' : 'Pending'}</ThemedText>
        </View>
      </View>
      <ThemedText type="small" themeColor="textSecondary">
        {item.orders.length} order{item.orders.length === 1 ? '' : 's'} · {formatDateTime(item.createdAt)}
      </ThemedText>
      {item.collectedAt ? (
        <ThemedText type="small" themeColor="textSecondary">
          Collected {formatDateTime(item.collectedAt)} {item.reference ? `· ${item.reference}` : ''}
        </ThemedText>
      ) : null}
    </View>
  );
}

export default function FeeCollectionsScreen() {
  const theme = useTheme();
  const { defaultCodEnabled, defaultBankTransferEnabled } = usePlatformConfig();
  // Fee collections only ever contain cod/bank-transfer orders — see
  // backend PlatformSettings' default*Enabled doc comment.
  const showFees = defaultCodEnabled || defaultBankTransferEnabled;
  const storeQuery = useQuery({ queryKey: ['me', 'store'], queryFn: getMyStore });
  const storeId = storeQuery.data?.id;

  const feesQuery = useQuery({
    queryKey: ['store', storeId, 'fee-collections'],
    queryFn: () => listStoreFeeCollections(storeId!),
    enabled: !!storeId && showFees,
  });

  if (!showFees) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: theme.background, alignItems: 'center', justifyContent: 'center', padding: Spacing.three }}>
        <ThemedText themeColor="textSecondary" style={{ textAlign: 'center' }}>
          This store doesn&apos;t use cash on delivery or bank transfer, so there are no fees to collect here.
        </ThemedText>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <FlatList
        data={feesQuery.data ?? []}
        keyExtractor={(f) => f.id}
        contentContainerStyle={styles.list}
        refreshControl={<RefreshControl refreshing={feesQuery.isFetching} onRefresh={() => feesQuery.refetch()} />}
        renderItem={({ item }) => <FeeCollectionRow item={item} />}
        ListEmptyComponent={
          !feesQuery.isLoading ? (
            <ThemedText themeColor="textSecondary" style={styles.empty}>
              No fee collections yet.
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
