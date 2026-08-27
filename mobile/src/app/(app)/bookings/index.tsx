import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { FlatList, StyleSheet, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { listStoreBookings } from '@/api/bookings';
import { getMyStore } from '@/api/stores';
import type { BookingResponse } from '@/api/types';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { bookingStatusColor, bookingStatusLabel, formatDateTime, formatMoney } from '@/lib/format';

function BookingRow({ booking, onPress }: { booking: BookingResponse; onPress: () => void }) {
  const theme = useTheme();
  return (
    <TouchableOpacity onPress={onPress} style={[styles.row, { backgroundColor: theme.backgroundElement }]}>
      <View style={styles.rowTop}>
        <ThemedText type="smallBold">{booking.bookingNumber}</ThemedText>
        <View style={[styles.badge, { backgroundColor: bookingStatusColor(booking.status) }]}>
          <ThemedText style={styles.badgeText}>{bookingStatusLabel(booking.status)}</ThemedText>
        </View>
      </View>
      <ThemedText type="small" themeColor="textSecondary">
        {booking.serviceName} · {formatDateTime(booking.scheduledStart)}
      </ThemedText>
      <View style={styles.rowBottom}>
        <ThemedText type="small" themeColor="textSecondary">
          {booking.buyerName}
        </ThemedText>
        <ThemedText type="smallBold">{formatMoney(booking.total)}</ThemedText>
      </View>
    </TouchableOpacity>
  );
}

export default function BookingsListScreen() {
  const theme = useTheme();
  const router = useRouter();

  const storeQuery = useQuery({ queryKey: ['me', 'store'], queryFn: getMyStore });
  const storeId = storeQuery.data?.id;

  const bookingsQuery = useQuery({
    queryKey: ['store', storeId, 'bookings'],
    queryFn: () => listStoreBookings(storeId!),
    enabled: !!storeId,
  });

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <FlatList
        data={bookingsQuery.data ?? []}
        keyExtractor={(b) => b.id}
        contentContainerStyle={styles.list}
        renderItem={({ item }) => <BookingRow booking={item} onPress={() => router.push(`/bookings/${item.id}`)} />}
        ListEmptyComponent={
          !bookingsQuery.isLoading ? (
            <ThemedText themeColor="textSecondary" style={styles.empty}>
              No bookings yet. If your store offers services, they&apos;ll show up here once a buyer books one.
            </ThemedText>
          ) : null
        }
      />
      <TouchableOpacity style={[styles.manageLink, { borderColor: theme.textSecondary }]} onPress={() => router.push('/bookings/services')}>
        <ThemedText themeColor="textSecondary">Manage services</ThemedText>
      </TouchableOpacity>
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
  empty: { textAlign: 'center', marginTop: Spacing.six, paddingHorizontal: Spacing.four },
  manageLink: { margin: Spacing.three, height: 44, borderRadius: 10, borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
});
