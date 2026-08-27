import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { RefreshControl, ScrollView, StyleSheet, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { logout } from '@/api/auth';
import { getMyStore, getStoreStats } from '@/api/stores';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { formatMoney } from '@/lib/format';
import { useAuthStore } from '@/store/auth-store';

export default function DashboardScreen() {
  const theme = useTheme();
  const router = useRouter();
  const signOut = useAuthStore((s) => s.signOut);

  const storeQuery = useQuery({ queryKey: ['me', 'store'], queryFn: getMyStore });
  const storeId = storeQuery.data?.id;

  const statsQuery = useQuery({
    queryKey: ['store', storeId, 'stats'],
    queryFn: () => getStoreStats(storeId!),
    enabled: !!storeId,
  });

  const isRefreshing = storeQuery.isFetching || statsQuery.isFetching;
  const onRefresh = () => {
    storeQuery.refetch();
    statsQuery.refetch();
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }}>
      <ScrollView
        contentContainerStyle={styles.container}
        refreshControl={<RefreshControl refreshing={isRefreshing} onRefresh={onRefresh} />}>
        <ThemedText type="title" style={styles.title}>
          {storeQuery.data?.name ?? 'Dashboard'}
        </ThemedText>

        {!storeQuery.isLoading && !storeQuery.data ? (
          <ThemedView type="backgroundElement" style={styles.card}>
            <ThemedText>
              You haven&apos;t onboarded a store yet. Complete store onboarding on the web dashboard, then come back here.
            </ThemedText>
          </ThemedView>
        ) : null}

        {statsQuery.data ? (
          <ThemedView type="backgroundElement" style={styles.card}>
            <ThemedText type="smallBold" themeColor="textSecondary">
              REVENUE (LAST 7 DAYS)
            </ThemedText>
            <ThemedText type="subtitle" style={styles.statValue}>
              {formatMoney(statsQuery.data.revenueCurrentPeriod)}
            </ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {formatMoney(statsQuery.data.revenuePreviousPeriod)} in the 7 days before
            </ThemedText>

            <ThemedText type="smallBold" themeColor="textSecondary" style={styles.secondStat}>
              PLATFORM FEE (LAST 7 DAYS)
            </ThemedText>
            <ThemedText type="subtitle" style={styles.statValue}>
              {formatMoney(statsQuery.data.platformFeeCurrentPeriod)}
            </ThemedText>
          </ThemedView>
        ) : null}

        <TouchableOpacity style={[styles.linkButton, { borderColor: theme.textSecondary }]} onPress={() => router.push('/dashboard/settings')}>
          <ThemedText>Settings</ThemedText>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.logoutButton, { borderColor: theme.textSecondary }]}
          onPress={async () => {
            try {
              await logout();
            } finally {
              signOut();
            }
          }}>
          <ThemedText themeColor="textSecondary">Sign out</ThemedText>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { padding: Spacing.three, gap: Spacing.three },
  title: { fontSize: 28, lineHeight: 34 },
  card: { borderRadius: 16, padding: Spacing.three, gap: Spacing.half },
  statValue: { fontSize: 28, lineHeight: 34 },
  secondStat: { marginTop: Spacing.two },
  linkButton: {
    marginTop: Spacing.two,
    borderWidth: 1,
    borderRadius: 10,
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  logoutButton: {
    marginTop: Spacing.four,
    borderWidth: 1,
    borderRadius: 10,
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
