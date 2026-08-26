import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, Alert, ScrollView, StyleSheet, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { getBooking, updateBookingStatus, verifyBookingBankTransfer } from '@/api/bookings';
import type { BookingStatus } from '@/api/types';
import { ApiError } from '@/lib/api-client';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { bookingStatusColor, bookingStatusLabel, formatDateTime, formatDuration, formatMoney } from '@/lib/format';

/** Mirrors the backend's ALLOWED_STATUS_TRANSITIONS (BookingService.kt). */
const NEXT_ACTIONS: Record<string, { status: BookingStatus; label: string; destructive?: boolean }[]> = {
  pending: [
    { status: 'confirmed', label: 'Confirm booking' },
    { status: 'cancelled', label: 'Cancel booking', destructive: true },
  ],
  confirmed: [
    { status: 'completed', label: 'Mark as completed' },
    { status: 'no-show', label: 'Mark as no-show', destructive: true },
    { status: 'cancelled', label: 'Cancel booking', destructive: true },
  ],
  completed: [],
  cancelled: [],
  'no-show': [],
};

function ActionButton({ label, onPress, disabled, destructive }: { label: string; onPress: () => void; disabled?: boolean; destructive?: boolean }) {
  return (
    <TouchableOpacity
      style={[styles.button, destructive ? styles.buttonDestructive : styles.buttonPrimary, disabled && styles.buttonDisabled]}
      onPress={onPress}
      disabled={disabled}>
      <ThemedText style={styles.buttonText}>{label}</ThemedText>
    </TouchableOpacity>
  );
}

export default function BookingDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const theme = useTheme();
  const queryClient = useQueryClient();

  const bookingQuery = useQuery({ queryKey: ['booking', id], queryFn: () => getBooking(id!), enabled: !!id });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['booking', id] });
    queryClient.invalidateQueries({ queryKey: ['store'] });
  };

  const statusMutation = useMutation({
    mutationFn: (status: BookingStatus) => updateBookingStatus(id!, status),
    onSuccess: invalidate,
    onError: (e) => Alert.alert('Could not update booking', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  const receiptMutation = useMutation({
    mutationFn: (approved: boolean) => verifyBookingBankTransfer(id!, approved),
    onSuccess: invalidate,
    onError: (e) => Alert.alert('Could not update receipt', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  if (bookingQuery.isLoading) {
    return (
      <SafeAreaView style={[styles.center, { backgroundColor: theme.background }]}>
        <ActivityIndicator />
      </SafeAreaView>
    );
  }

  const booking = bookingQuery.data;
  if (!booking) {
    return (
      <SafeAreaView style={[styles.center, { backgroundColor: theme.background }]}>
        <ThemedText themeColor="textSecondary">Booking not found.</ThemedText>
      </SafeAreaView>
    );
  }

  const nextActions = NEXT_ACTIONS[booking.status] ?? [];
  const awaitingReceiptVerification = booking.paymentMethod === 'bank-transfer' && !!booking.receiptUrl && booking.paymentStatus === 'unpaid';

  const handleAction = (status: BookingStatus) => {
    if (status === 'cancelled' || status === 'no-show') {
      Alert.alert(
        status === 'cancelled' ? 'Cancel this booking?' : 'Mark as no-show?',
        'The buyer will be notified. This cannot be undone.',
        [
          { text: 'Go back', style: 'cancel' },
          { text: 'Confirm', style: 'destructive', onPress: () => statusMutation.mutate(status) },
        ],
      );
      return;
    }
    statusMutation.mutate(status);
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <ScrollView contentContainerStyle={styles.container}>
        <View style={styles.header}>
          <ThemedText type="subtitle">{booking.bookingNumber}</ThemedText>
          <View style={[styles.badge, { backgroundColor: bookingStatusColor(booking.status) }]}>
            <ThemedText style={styles.badgeText}>{bookingStatusLabel(booking.status)}</ThemedText>
          </View>
        </View>
        <ThemedText type="small" themeColor="textSecondary">
          Booked {formatDateTime(booking.createdAt)}
        </ThemedText>

        <ThemedView type="backgroundElement" style={styles.card}>
          <ThemedText type="smallBold" themeColor="textSecondary">
            SERVICE
          </ThemedText>
          <ThemedText>{booking.serviceName}</ThemedText>
          <ThemedText type="small" themeColor="textSecondary">
            {formatDateTime(booking.scheduledStart)} · {formatDuration(booking.serviceDurationMinutes)}
          </ThemedText>
          <View style={styles.divider} />
          <View style={styles.itemRow}>
            <ThemedText type="small" themeColor="textSecondary">
              Price
            </ThemedText>
            <ThemedText type="small">{formatMoney(booking.servicePrice)}</ThemedText>
          </View>
          {booking.discountAmount > 0 ? (
            <View style={styles.itemRow}>
              <ThemedText type="small" themeColor="textSecondary">
                Discount ({booking.couponCode})
              </ThemedText>
              <ThemedText type="small">-{formatMoney(booking.discountAmount)}</ThemedText>
            </View>
          ) : null}
          <View style={styles.itemRow}>
            <ThemedText type="smallBold">Total</ThemedText>
            <ThemedText type="smallBold">{formatMoney(booking.total)}</ThemedText>
          </View>
          <ThemedText type="small" themeColor="textSecondary">
            Platform fee (deducted from your payout): {formatMoney(booking.platformFee)}
          </ThemedText>
        </ThemedView>

        <ThemedView type="backgroundElement" style={styles.card}>
          <ThemedText type="smallBold" themeColor="textSecondary">
            CUSTOMER
          </ThemedText>
          <ThemedText>{booking.buyerName}</ThemedText>
          <ThemedText type="small" themeColor="textSecondary">
            {booking.buyerPhone}
          </ThemedText>
          <ThemedText type="small" themeColor="textSecondary">
            {booking.buyerEmail}
          </ThemedText>
        </ThemedView>

        <ThemedView type="backgroundElement" style={styles.card}>
          <ThemedText type="smallBold" themeColor="textSecondary">
            PAYMENT
          </ThemedText>
          <ThemedText>
            {booking.paymentMethod.toUpperCase()} — {booking.paymentStatus}
          </ThemedText>
        </ThemedView>

        {booking.cancellationReason ? (
          <ThemedView type="backgroundElement" style={styles.card}>
            <ThemedText type="smallBold" themeColor="textSecondary">
              CANCELLATION REASON
            </ThemedText>
            <ThemedText type="small">{booking.cancellationReason}</ThemedText>
          </ThemedView>
        ) : null}

        <ThemedView type="backgroundElement" style={styles.card}>
          <ThemedText type="smallBold" themeColor="textSecondary">
            TIMELINE
          </ThemedText>
          {booking.timeline.map((entry, i) => (
            <View key={i} style={styles.itemRow}>
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
              Review the buyer&apos;s uploaded receipt on the web dashboard, then confirm or reject it here.
            </ThemedText>
            <View style={styles.actionRow}>
              <ActionButton label="Reject receipt" destructive onPress={() => receiptMutation.mutate(false)} disabled={receiptMutation.isPending} />
              <ActionButton label="Approve receipt" onPress={() => receiptMutation.mutate(true)} disabled={receiptMutation.isPending} />
            </View>
          </ThemedView>
        ) : null}

        {nextActions.length > 0 ? (
          <View style={styles.actionColumn}>
            {nextActions.map((action) => (
              <ActionButton
                key={action.status}
                label={action.label}
                destructive={action.destructive}
                disabled={statusMutation.isPending}
                onPress={() => handleAction(action.status)}
              />
            ))}
          </View>
        ) : null}
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
  divider: { height: StyleSheet.hairlineWidth, backgroundColor: '#8888', marginVertical: Spacing.half },
  itemRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  actionColumn: { gap: Spacing.two, marginTop: Spacing.three },
  actionRow: { flexDirection: 'row', gap: Spacing.two, marginTop: Spacing.two },
  button: { flex: 1, height: 46, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
  buttonPrimary: { backgroundColor: '#208AEF' },
  buttonDestructive: { backgroundColor: '#D64545' },
  buttonDisabled: { opacity: 0.5 },
  buttonText: { color: '#fff', fontWeight: '600' },
});
