import { useQuery } from '@tanstack/react-query';
import { router, type Href } from 'expo-router';
import { FlatList, StyleSheet, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { listMyBookings } from '@/api/buyer-bookings';
import { EmptyState } from '@/components/empty-state';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { bookingStatusColor, bookingStatusLabel, formatDateTime } from '@/lib/format';
import { formatCurrency, usePlatformConfig } from '@/lib/platform-config';

export default function BookingHistoryScreen() {
  const theme = useTheme();
  const platformConfig = usePlatformConfig();
  const bookingsQuery = useQuery({ queryKey: ['me', 'bookings'], queryFn: listMyBookings });

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <FlatList
        data={bookingsQuery.data ?? []}
        keyExtractor={(b) => b.id}
        contentContainerStyle={styles.list}
        ListEmptyComponent={!bookingsQuery.isLoading ? <EmptyState title="No bookings yet" /> : null}
        renderItem={({ item }) => (
          <TouchableOpacity style={[styles.row, { borderColor: theme.backgroundElement }]} onPress={() => router.push(`/account/bookings/${item.id}` as Href)}>
            <View style={styles.rowInfo}>
              <ThemedText type="smallBold">{item.serviceName}</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                {item.storeName} · {formatDateTime(item.scheduledStart)}
              </ThemedText>
            </View>
            <View style={styles.rowRight}>
              <ThemedText type="smallBold">{formatCurrency(item.total, platformConfig)}</ThemedText>
              <ThemedText type="small" style={{ color: bookingStatusColor(item.status) }}>
                {bookingStatusLabel(item.status)}
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
