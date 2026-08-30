import { useQuery } from '@tanstack/react-query';
import { Stack, useRouter } from 'expo-router';
import { RefreshControl, ScrollView, StyleSheet, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { getNotificationsSummary } from '@/api/notifications';
import { getMyStore, getStoreStats } from '@/api/stores';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { formatMoney } from '@/lib/format';

export default function DashboardScreen() {
  const theme = useTheme();
  const router = useRouter();

  const storeQuery = useQuery({ queryKey: ['me', 'store'], queryFn: getMyStore });
  const storeId = storeQuery.data?.id;

  const statsQuery = useQuery({
    queryKey: ['store', storeId, 'stats'],
    queryFn: () => getStoreStats(storeId!),
    enabled: !!storeId,
  });

  const summaryQuery = useQuery({
    queryKey: ['me', 'seller', 'notifications', 'summary'],
    queryFn: getNotificationsSummary,
    refetchInterval: 30000,
  });
  const unreadCount = summaryQuery.data?.unreadCount ?? 0;

  const isRefreshing = storeQuery.isFetching || statsQuery.isFetching;
  const onRefresh = () => {
    storeQuery.refetch();
    statsQuery.refetch();
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <Stack.Screen
        options={{
          title: storeQuery.data?.name ?? 'Dashboard',
          headerRight: () => (
            <View style={styles.headerActions}>
              <TouchableOpacity hitSlop={8} onPress={() => router.push('/dashboard/notifications' as never)} style={styles.bellWrapper}>
                <ThemedText style={styles.headerIcon}>🔔</ThemedText>
                {unreadCount > 0 ? (
                  <View style={styles.badge}>
                    <ThemedText style={styles.badgeText}>{unreadCount > 9 ? '9+' : unreadCount}</ThemedText>
                  </View>
                ) : null}
              </TouchableOpacity>
              <TouchableOpacity hitSlop={8} onPress={() => router.push('/dashboard/settings')}>
                <ThemedText style={styles.headerIcon}>⚙</ThemedText>
              </TouchableOpacity>
            </View>
          ),
        }}
      />
      <ScrollView
        contentContainerStyle={styles.container}
        refreshControl={<RefreshControl refreshing={isRefreshing} onRefresh={onRefresh} />}>
        {!storeQuery.isLoading && !storeQuery.data ? (
          <ThemedView type="backgroundElement" style={styles.card}>
            <ThemedText>
              You haven&apos;t onboarded a store yet. Complete store onboarding on the web dashboard, then come back here.
            </ThemedText>
          </ThemedView>
        ) : null}

        {statsQuery.data ? (
          <View style={styles.statsRow}>
            <ThemedView type="backgroundElement" style={[styles.card, styles.statCard]}>
              <ThemedText type="smallBold" themeColor="textSecondary">
                REVENUE (7 DAYS)
              </ThemedText>
              <ThemedText type="subtitle" style={styles.statValue}>
                {formatMoney(statsQuery.data.revenueCurrentPeriod)}
              </ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                {formatMoney(statsQuery.data.revenuePreviousPeriod)} the week before
              </ThemedText>
            </ThemedView>

            <TouchableOpacity
              style={styles.statCard}
              onPress={() => router.push({ pathname: '/orders', params: { status: 'pending' } } as never)}>
              <ThemedView type="backgroundElement" style={styles.card}>
                <ThemedText type="smallBold" themeColor="textSecondary">
                  PENDING ORDERS
                </ThemedText>
                <ThemedText type="subtitle" style={styles.statValue}>
                  {statsQuery.data.pendingOrderCount}
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {statsQuery.data.pendingOrderCount > 0 ? 'Tap to view' : 'All caught up'}
                </ThemedText>
              </ThemedView>
            </TouchableOpacity>
          </View>
        ) : null}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { padding: Spacing.three, gap: Spacing.three },
  headerActions: { flexDirection: 'row', gap: Spacing.three, paddingRight: Spacing.two },
  headerIcon: { fontSize: 20 },
  bellWrapper: { position: 'relative' },
  badge: {
    position: 'absolute',
    top: -4,
    right: -6,
    backgroundColor: '#D64545',
    minWidth: 16,
    height: 16,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 3,
  },
  badgeText: { color: '#fff', fontSize: 10, fontWeight: '700' },
  statsRow: { flexDirection: 'row', gap: Spacing.two },
  statCard: { flex: 1 },
  card: { borderRadius: 16, padding: Spacing.three, gap: Spacing.half, flex: 1 },
  statValue: { fontSize: 24, lineHeight: 30 },
});
