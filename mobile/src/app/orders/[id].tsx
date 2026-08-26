import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Alert, ScrollView, StyleSheet, TextInput, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { getOrder, updateOrderStatus, verifyBankTransfer } from '@/api/orders';
import { ApiError } from '@/lib/api-client';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { formatDateTime, formatMoney, orderStatusColor, orderStatusLabel } from '@/lib/format';

/** Mirrors the backend's ALLOWED_STATUS_TRANSITIONS (OrderService.kt) — the UI only ever offers a transition the server will actually accept. */
const NEXT_ACTIONS: Record<string, { status: 'confirmed' | 'shipped' | 'delivered' | 'cancelled'; label: string }[]> = {
  pending: [
    { status: 'confirmed', label: 'Confirm order' },
    { status: 'cancelled', label: 'Cancel order' },
  ],
  confirmed: [
    { status: 'shipped', label: 'Mark as shipped' },
    { status: 'cancelled', label: 'Cancel order' },
  ],
  shipped: [{ status: 'delivered', label: 'Mark as delivered' }],
  delivered: [],
  cancelled: [],
};

function PrimaryButton({ label, onPress, disabled, destructive }: { label: string; onPress: () => void; disabled?: boolean; destructive?: boolean }) {
  return (
    <TouchableOpacity
      style={[styles.button, destructive ? styles.buttonDestructive : styles.buttonPrimary, disabled && styles.buttonDisabled]}
      onPress={onPress}
      disabled={disabled}>
      <ThemedText style={styles.buttonText}>{label}</ThemedText>
    </TouchableOpacity>
  );
}

export default function OrderDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const theme = useTheme();
  const queryClient = useQueryClient();

  const [showShipForm, setShowShipForm] = useState(false);
  const [trackingNumber, setTrackingNumber] = useState('');
  const [courierServiceName, setCourierServiceName] = useState('');

  const orderQuery = useQuery({
    queryKey: ['order', id],
    queryFn: () => getOrder(id!),
    enabled: !!id,
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['order', id] });
    queryClient.invalidateQueries({ queryKey: ['store'] });
  };

  const statusMutation = useMutation({
    mutationFn: (params: { status: 'confirmed' | 'shipped' | 'delivered' | 'cancelled'; trackingNumber?: string; courierServiceName?: string }) =>
      updateOrderStatus(id!, params),
    onSuccess: () => {
      setShowShipForm(false);
      invalidate();
    },
    onError: (e) => Alert.alert('Could not update order', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  const receiptMutation = useMutation({
    mutationFn: (approved: boolean) => verifyBankTransfer(id!, approved),
    onSuccess: invalidate,
    onError: (e) => Alert.alert('Could not update receipt', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  if (orderQuery.isLoading) {
    return (
      <SafeAreaView style={[styles.center, { backgroundColor: theme.background }]}>
        <ActivityIndicator />
      </SafeAreaView>
    );
  }

  const order = orderQuery.data;
  if (!order) {
    return (
      <SafeAreaView style={[styles.center, { backgroundColor: theme.background }]}>
        <ThemedText themeColor="textSecondary">Order not found.</ThemedText>
      </SafeAreaView>
    );
  }

  const nextActions = NEXT_ACTIONS[order.status] ?? [];
  const awaitingReceiptVerification = order.paymentMethod === 'bank-transfer' && !!order.receiptUrl && order.paymentStatus === 'unpaid';

  const handleAction = (status: 'confirmed' | 'shipped' | 'delivered' | 'cancelled') => {
    if (status === 'cancelled') {
      Alert.alert('Cancel this order?', 'The buyer will be notified. This cannot be undone.', [
        { text: 'Keep order', style: 'cancel' },
        { text: 'Cancel order', style: 'destructive', onPress: () => statusMutation.mutate({ status }) },
      ]);
      return;
    }
    if (status === 'shipped') {
      setShowShipForm(true);
      return;
    }
    statusMutation.mutate({ status });
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <ScrollView contentContainerStyle={styles.container}>
        <View style={styles.header}>
          <ThemedText type="subtitle">{order.orderNumber}</ThemedText>
          <View style={[styles.badge, { backgroundColor: orderStatusColor(order.status) }]}>
            <ThemedText style={styles.badgeText}>{orderStatusLabel(order.status)}</ThemedText>
          </View>
        </View>
        <ThemedText type="small" themeColor="textSecondary">
          Placed {formatDateTime(order.createdAt)}
        </ThemedText>

        <ThemedView type="backgroundElement" style={styles.card}>
          <ThemedText type="smallBold" themeColor="textSecondary">
            ITEMS
          </ThemedText>
          {order.items.map((item, i) => (
            <View key={`${item.productId}-${i}`} style={styles.itemRow}>
              <ThemedText style={styles.itemName}>
                {item.quantity}× {item.productName}
              </ThemedText>
              <ThemedText type="smallBold">{formatMoney(item.unitPrice * item.quantity)}</ThemedText>
            </View>
          ))}
          <View style={styles.divider} />
          <View style={styles.itemRow}>
            <ThemedText type="small" themeColor="textSecondary">
              Subtotal
            </ThemedText>
            <ThemedText type="small">{formatMoney(order.subtotal)}</ThemedText>
          </View>
          {order.deliveryMethod === 'shipping' ? (
            <View style={styles.itemRow}>
              <ThemedText type="small" themeColor="textSecondary">
                Shipping
              </ThemedText>
              <ThemedText type="small">{formatMoney(order.shippingFee)}</ThemedText>
            </View>
          ) : null}
          {order.discountAmount > 0 ? (
            <View style={styles.itemRow}>
              <ThemedText type="small" themeColor="textSecondary">
                Discount ({order.couponCode})
              </ThemedText>
              <ThemedText type="small">-{formatMoney(order.discountAmount)}</ThemedText>
            </View>
          ) : null}
          <View style={styles.itemRow}>
            <ThemedText type="smallBold">Total</ThemedText>
            <ThemedText type="smallBold">{formatMoney(order.total)}</ThemedText>
          </View>
          <ThemedText type="small" themeColor="textSecondary">
            Platform fee (deducted from your payout): {formatMoney(order.platformFee)}
          </ThemedText>
        </ThemedView>

        <ThemedView type="backgroundElement" style={styles.card}>
          <ThemedText type="smallBold" themeColor="textSecondary">
            PAYMENT
          </ThemedText>
          <ThemedText>
            {order.paymentMethod.toUpperCase()} — {order.paymentStatus}
          </ThemedText>
        </ThemedView>

        {order.deliveryMethod === 'shipping' ? (
          <ThemedView type="backgroundElement" style={styles.card}>
            <ThemedText type="smallBold" themeColor="textSecondary">
              SHIPPING TO
            </ThemedText>
            <ThemedText>{order.shipping.fullName ?? '—'}</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {order.shipping.phone ?? '—'}
            </ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {[order.shipping.addressLine1, order.shipping.city, order.shipping.state, order.shipping.postalCode]
                .filter(Boolean)
                .join(', ') || '—'}
            </ThemedText>
            {order.trackingNumber ? (
              <ThemedText type="small" themeColor="textSecondary" style={styles.trackingLine}>
                {order.courierServiceName}: {order.trackingNumber}
              </ThemedText>
            ) : null}
          </ThemedView>
        ) : null}

        <ThemedView type="backgroundElement" style={styles.card}>
          <ThemedText type="smallBold" themeColor="textSecondary">
            TIMELINE
          </ThemedText>
          {order.timeline.map((entry, i) => (
            <View key={i} style={styles.timelineRow}>
              <ThemedText type="small">{entry.label}</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                {formatDateTime(entry.timestamp)}
              </ThemedText>
            </View>
          ))}
        </ThemedView>

        {awaitingReceiptVerification ? (
          <ThemedView type="backgroundElement" style={styles.card}>
            <ThemedText type="smallBold" themeColor="textSecondary">
              BANK TRANSFER RECEIPT
            </ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              The buyer uploaded a receipt — review it on the web dashboard, then confirm or reject it here.
            </ThemedText>
            <View style={styles.actionRow}>
              <PrimaryButton label="Reject receipt" destructive onPress={() => receiptMutation.mutate(false)} disabled={receiptMutation.isPending} />
              <PrimaryButton label="Approve receipt" onPress={() => receiptMutation.mutate(true)} disabled={receiptMutation.isPending} />
            </View>
          </ThemedView>
        ) : null}

        {showShipForm ? (
          <ThemedView type="backgroundElement" style={styles.card}>
            <ThemedText type="smallBold" themeColor="textSecondary">
              SHIPPING DETAILS
            </ThemedText>
            <TextInput
              style={[styles.input, { color: theme.text, backgroundColor: theme.background }]}
              placeholder="Courier service name"
              placeholderTextColor={theme.textSecondary}
              value={courierServiceName}
              onChangeText={setCourierServiceName}
            />
            <TextInput
              style={[styles.input, { color: theme.text, backgroundColor: theme.background }]}
              placeholder="Tracking number"
              placeholderTextColor={theme.textSecondary}
              value={trackingNumber}
              onChangeText={setTrackingNumber}
            />
            <PrimaryButton
              label="Confirm shipped"
              disabled={!trackingNumber.trim() || !courierServiceName.trim() || statusMutation.isPending}
              onPress={() => statusMutation.mutate({ status: 'shipped', trackingNumber: trackingNumber.trim(), courierServiceName: courierServiceName.trim() })}
            />
          </ThemedView>
        ) : (
          nextActions.length > 0 && (
            <View style={styles.actionColumn}>
              {nextActions.map((action) => (
                <PrimaryButton
                  key={action.status}
                  label={action.label}
                  destructive={action.status === 'cancelled'}
                  disabled={statusMutation.isPending}
                  onPress={() => handleAction(action.status)}
                />
              ))}
            </View>
          )
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  container: { padding: Spacing.three, gap: Spacing.two },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  badge: { paddingHorizontal: Spacing.two, paddingVertical: 4, borderRadius: 999 },
  badgeText: { color: '#fff', fontSize: 12, fontWeight: '600' },
  card: { borderRadius: 16, padding: Spacing.three, gap: Spacing.half, marginTop: Spacing.two },
  itemRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  itemName: { flex: 1, marginRight: Spacing.two },
  divider: { height: StyleSheet.hairlineWidth, backgroundColor: '#8888', marginVertical: Spacing.half },
  trackingLine: { marginTop: Spacing.half },
  timelineRow: { flexDirection: 'row', justifyContent: 'space-between' },
  actionColumn: { gap: Spacing.two, marginTop: Spacing.three },
  actionRow: { flexDirection: 'row', gap: Spacing.two, marginTop: Spacing.two },
  button: { flex: 1, height: 46, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
  buttonPrimary: { backgroundColor: '#208AEF' },
  buttonDestructive: { backgroundColor: '#D64545' },
  buttonDisabled: { opacity: 0.5 },
  buttonText: { color: '#fff', fontWeight: '600' },
  input: {
    height: 46,
    borderRadius: 10,
    paddingHorizontal: Spacing.three,
    fontSize: 16,
  },
});
