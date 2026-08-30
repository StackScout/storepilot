import { useQuery } from '@tanstack/react-query';
import { router } from 'expo-router';
import { ActivityIndicator, ScrollView, StyleSheet, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { getSellerPlan } from '@/api/billing';
import { getBookingAnalytics } from '@/api/bookings';
import { getMyStore } from '@/api/stores';
import { EmptyState } from '@/components/empty-state';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { usePlatformConfig } from '@/lib/platform-config';
import { formatMoney } from '@/lib/format';

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <ThemedView type="backgroundElement" style={styles.statCard}>
      <ThemedText type="smallBold" themeColor="textSecondary">
        {label}
      </ThemedText>
      <ThemedText type="subtitle" style={styles.statValue}>
        {value}
      </ThemedText>
    </ThemedView>
  );
}

export default function BookingAnalyticsScreen() {
  const theme = useTheme();
  const { proPlanEnabled } = usePlatformConfig();

  const storeQuery = useQuery({ queryKey: ['me', 'store'], queryFn: getMyStore });
  const storeId = storeQuery.data?.id;

  const planQuery = useQuery({ queryKey: ['me', 'seller', 'plan'], queryFn: getSellerPlan, enabled: proPlanEnabled });
  // On a deployment with no Pro tier concept at all, this is free for everyone — see PlatformSettings.proPlanEnabled's doc comment.
  const isPro = !proPlanEnabled || planQuery.data?.plan === 'pro';

  const analyticsQuery = useQuery({
    queryKey: ['store', storeId, 'booking-analytics'],
    queryFn: () => getBookingAnalytics(storeId!),
    enabled: !!storeId && isPro,
  });

  if (proPlanEnabled && planQuery.isLoading) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: theme.background, alignItems: 'center', justifyContent: 'center' }}>
        <ActivityIndicator />
      </SafeAreaView>
    );
  }

  if (!isPro) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
        <View style={styles.upsellContainer}>
          <ThemedText type="title" style={styles.upsellTitle}>
            Booking analytics is a Pro feature
          </ThemedText>
          <ThemedText type="small" themeColor="textSecondary" style={styles.upsellText}>
            Upgrade to Pro to see revenue, no-show rate, top services, and repeat-customer trends for your bookings.
          </ThemedText>
          <TouchableOpacity style={styles.upsellButton} onPress={() => router.push('/dashboard/settings/billing')}>
            <ThemedText style={styles.upsellButtonText}>Upgrade to Pro</ThemedText>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  const analytics = analyticsQuery.data;

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <ScrollView contentContainerStyle={styles.container}>
        <ThemedText type="small" themeColor="textSecondary">
          Revenue and performance trends for your bookings.
        </ThemedText>

        {analyticsQuery.isLoading || !analytics ? (
          <ActivityIndicator style={styles.loading} />
        ) : analytics.totalBookings === 0 ? (
          <EmptyState title="No bookings yet" description="Analytics will show up here once buyers start booking your services." />
        ) : (
          <>
            <View style={styles.statsGrid}>
              <StatCard label="TOTAL BOOKINGS" value={String(analytics.totalBookings)} />
              <StatCard label="REVENUE" value={formatMoney(analytics.totalRevenue)} />
              <StatCard label="NO-SHOW RATE" value={`${analytics.noShowRate}%`} />
              <StatCard label="REPEAT CUSTOMERS" value={`${analytics.repeatBuyerRate}%`} />
            </View>

            <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
              TOP SERVICES
            </ThemedText>
            {analytics.topServices.length === 0 ? (
              <ThemedText type="small" themeColor="textSecondary">
                No completed, paid bookings yet.
              </ThemedText>
            ) : (
              analytics.topServices.map((service) => (
                <View key={service.serviceName} style={[styles.serviceRow, { borderColor: theme.backgroundElement }]}>
                  <View>
                    <ThemedText type="smallBold">{service.serviceName}</ThemedText>
                    <ThemedText type="small" themeColor="textSecondary">
                      {service.bookingCount} bookings
                    </ThemedText>
                  </View>
                  <ThemedText type="smallBold">{formatMoney(service.revenue)}</ThemedText>
                </View>
              ))
            )}

            <ThemedView type="backgroundElement" style={[styles.summaryCard, styles.sectionLabel]}>
              <ThemedText type="small">
                <ThemedText type="smallBold">{analytics.cancelledBookings} cancelled</ThemedText> and{' '}
                <ThemedText type="smallBold">{analytics.noShowBookings} no-show</ThemedText> bookings out of {analytics.totalBookings} total.
              </ThemedText>
            </ThemedView>
          </>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { padding: Spacing.three, gap: Spacing.two, paddingBottom: Spacing.six },
  loading: { marginTop: Spacing.six },
  statsGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.two, marginTop: Spacing.two },
  statCard: { flexBasis: '47%', flexGrow: 1, borderRadius: 14, padding: Spacing.three, gap: 4 },
  statValue: { fontSize: 22, lineHeight: 28 },
  sectionLabel: { marginTop: Spacing.three },
  serviceRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', borderBottomWidth: 1, paddingVertical: Spacing.two },
  summaryCard: { borderRadius: 14, padding: Spacing.three },
  upsellContainer: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: Spacing.four, gap: Spacing.two },
  upsellTitle: { fontSize: 22, textAlign: 'center' },
  upsellText: { textAlign: 'center', marginBottom: Spacing.two },
  upsellButton: { height: 48, borderRadius: 10, paddingHorizontal: Spacing.four, backgroundColor: '#208AEF', alignItems: 'center', justifyContent: 'center' },
  upsellButtonText: { color: '#fff', fontWeight: '600' },
});
