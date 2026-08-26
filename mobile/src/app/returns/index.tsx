import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { ActivityIndicator, Alert, FlatList, RefreshControl, StyleSheet, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { decideReturn, listStoreReturns, markReturnRefunded } from '@/api/returns';
import { getMyStore } from '@/api/stores';
import type { ReturnRequestResponse } from '@/api/types';
import { ApiError } from '@/lib/api-client';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { formatDateTime, returnReasonLabel, returnStatusColor, returnStatusLabel } from '@/lib/format';

function ReturnRow({ item }: { item: ReturnRequestResponse }) {
  const theme = useTheme();
  const queryClient = useQueryClient();
  const [busy, setBusy] = useState<'approve' | 'reject' | 'refund' | null>(null);

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['store', item.storeId, 'returns'] });

  const decideMutation = useMutation({
    mutationFn: (approved: boolean) => decideReturn(item.orderId, item.id, approved),
    onMutate: (approved) => setBusy(approved ? 'approve' : 'reject'),
    onSuccess: invalidate,
    onError: (e) => Alert.alert('Could not update return', e instanceof ApiError ? e.message : 'Please try again.'),
    onSettled: () => setBusy(null),
  });

  const refundMutation = useMutation({
    mutationFn: () => markReturnRefunded(item.orderId, item.id),
    onMutate: () => setBusy('refund'),
    onSuccess: invalidate,
    onError: (e) => Alert.alert('Could not mark refunded', e instanceof ApiError ? e.message : 'Please try again.'),
    onSettled: () => setBusy(null),
  });

  const canDecide = item.status === 'requested';
  const canMarkRefunded = item.status === 'refund-pending' && (item.paymentMethod === 'cod' || item.paymentMethod === 'bank-transfer');

  return (
    <View style={[styles.row, { backgroundColor: theme.backgroundElement }]}>
      <View style={styles.rowTop}>
        <ThemedText type="smallBold">{item.orderNumber}</ThemedText>
        <View style={[styles.badge, { backgroundColor: returnStatusColor(item.status) }]}>
          <ThemedText style={styles.badgeText}>{returnStatusLabel(item.status)}</ThemedText>
        </View>
      </View>
      <ThemedText type="small" themeColor="textSecondary">
        {returnReasonLabel(item.reasonCategory)} · {formatDateTime(item.createdAt)}
      </ThemedText>
      {item.reasonNote ? <ThemedText type="small">{item.reasonNote}</ThemedText> : null}
      {item.settlementReconciliationNote ? (
        <ThemedText type="small" style={{ color: returnStatusColor('requested') }}>
          {item.settlementReconciliationNote}
        </ThemedText>
      ) : null}

      {canDecide ? (
        <View style={styles.actionsRow}>
          <TouchableOpacity
            style={[styles.actionButton, styles.rejectButton]}
            disabled={busy !== null}
            onPress={() => decideMutation.mutate(false)}>
            {busy === 'reject' ? <ActivityIndicator color="#D64545" /> : <ThemedText style={styles.rejectText}>Reject</ThemedText>}
          </TouchableOpacity>
          <TouchableOpacity style={[styles.actionButton, styles.approveButton]} disabled={busy !== null} onPress={() => decideMutation.mutate(true)}>
            {busy === 'approve' ? <ActivityIndicator color="#fff" /> : <ThemedText style={styles.approveText}>Approve</ThemedText>}
          </TouchableOpacity>
        </View>
      ) : null}

      {canMarkRefunded ? (
        <TouchableOpacity
          style={[styles.actionButton, styles.approveButton, styles.fullWidthButton]}
          disabled={busy !== null}
          onPress={() => refundMutation.mutate()}>
          {busy === 'refund' ? <ActivityIndicator color="#fff" /> : <ThemedText style={styles.approveText}>Mark refunded</ThemedText>}
        </TouchableOpacity>
      ) : null}
    </View>
  );
}

export default function ReturnsListScreen() {
  const theme = useTheme();
  const storeQuery = useQuery({ queryKey: ['me', 'store'], queryFn: getMyStore });
  const storeId = storeQuery.data?.id;

  const returnsQuery = useQuery({
    queryKey: ['store', storeId, 'returns'],
    queryFn: () => listStoreReturns(storeId!),
    enabled: !!storeId,
  });

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <FlatList
        data={returnsQuery.data ?? []}
        keyExtractor={(r) => r.id}
        contentContainerStyle={styles.list}
        refreshControl={<RefreshControl refreshing={returnsQuery.isFetching} onRefresh={() => returnsQuery.refetch()} />}
        renderItem={({ item }) => <ReturnRow item={item} />}
        ListEmptyComponent={
          !returnsQuery.isLoading ? (
            <ThemedText themeColor="textSecondary" style={styles.empty}>
              No return requests yet.
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
  actionsRow: { flexDirection: 'row', gap: Spacing.two, marginTop: Spacing.two },
  actionButton: { flex: 1, height: 40, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
  fullWidthButton: { marginTop: Spacing.two },
  approveButton: { backgroundColor: '#208AEF' },
  approveText: { color: '#fff', fontWeight: '700' },
  rejectButton: { borderWidth: 1, borderColor: '#D64545' },
  rejectText: { color: '#D64545', fontWeight: '700' },
  empty: { textAlign: 'center', marginTop: Spacing.six },
});
