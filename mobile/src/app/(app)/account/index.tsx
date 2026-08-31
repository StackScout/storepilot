import { useQuery } from '@tanstack/react-query';
import { router, type Href } from 'expo-router';
import { Alert, ScrollView, StyleSheet, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { deleteBuyerAccount, exportBuyerData } from '@/api/buyer-account';
import { listMyBookings } from '@/api/buyer-bookings';
import { listMyOrders } from '@/api/buyer-orders';
import { getBuyerNotificationsSummary } from '@/api/buyer-notifications';
import { logout } from '@/api/auth';
import { BiometricLockToggle } from '@/components/biometric-lock-toggle';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-client';
import { useAuthStore } from '@/store/auth-store';

function AccountLink({ label, onPress }: { label: string; onPress: () => void }) {
  const theme = useTheme();
  return (
    <TouchableOpacity style={[styles.link, { borderColor: theme.backgroundElement }]} onPress={onPress}>
      <ThemedText>{label}</ThemedText>
      <ThemedText themeColor="textSecondary">›</ThemedText>
    </TouchableOpacity>
  );
}

export default function AccountScreen() {
  const theme = useTheme();
  const isSignedIn = useAuthStore((s) => s.isSignedIn);
  const role = useAuthStore((s) => s.role);
  const name = useAuthStore((s) => s.name);
  const email = useAuthStore((s) => s.email);
  const signOut = useAuthStore((s) => s.signOut);

  const isBuyer = isSignedIn && role === 'buyer';

  const ordersQuery = useQuery({ queryKey: ['me', 'orders'], queryFn: listMyOrders, enabled: isBuyer });
  const bookingsQuery = useQuery({ queryKey: ['me', 'bookings'], queryFn: listMyBookings, enabled: isBuyer });
  const notificationsSummaryQuery = useQuery({
    queryKey: ['me', 'buyer', 'notifications', 'summary'],
    queryFn: getBuyerNotificationsSummary,
    enabled: isBuyer,
    refetchInterval: 30000,
  });
  const unreadNotifications = notificationsSummaryQuery.data?.unreadCount ?? 0;

  if (!isBuyer) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }}>
        <View style={styles.signedOutContainer}>
          <ThemedText type="title" style={styles.signedOutTitle}>
            Your account
          </ThemedText>
          <ThemedText themeColor="textSecondary" style={styles.signedOutSubtitle}>
            Sign in to see your orders, bookings, wishlist, and more.
          </ThemedText>
          <TouchableOpacity style={styles.primaryButton} onPress={() => router.push('/account/login' as Href)}>
            <ThemedText style={styles.primaryButtonText}>Sign in</ThemedText>
          </TouchableOpacity>
          <TouchableOpacity style={[styles.secondaryButton, { borderColor: theme.textSecondary }]} onPress={() => router.push('/account/register' as Href)}>
            <ThemedText>Create an account</ThemedText>
          </TouchableOpacity>
          <TouchableOpacity style={styles.trackLink} onPress={() => router.push('/account/track' as Href)}>
            <ThemedText type="small" themeColor="textSecondary">
              Track an order or booking without signing in
            </ThemedText>
          </TouchableOpacity>
          <TouchableOpacity style={styles.trackLink} onPress={() => router.push('/account/register-seller' as Href)}>
            <ThemedText type="small">Sell on StorePilot</ThemedText>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  const handleExport = async () => {
    try {
      const data = await exportBuyerData();
      Alert.alert('Data exported', `Export includes ${JSON.stringify(data).length} bytes of JSON. Full export is available from the web account page.`);
    } catch (e) {
      Alert.alert('Could not export data', e instanceof ApiError ? e.message : 'Please try again.');
    }
  };

  const confirmDelete = () => {
    Alert.alert('Delete your account?', 'This permanently deletes your account. This cannot be undone.', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete',
        style: 'destructive',
        onPress: async () => {
          try {
            await deleteBuyerAccount();
          } finally {
            signOut();
          }
        },
      },
    ]);
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <ScrollView contentContainerStyle={styles.container}>
        <View>
          <ThemedText type="title">{name ?? 'My account'}</ThemedText>
          {email ? (
            <ThemedText type="small" themeColor="textSecondary">
              {email}
            </ThemedText>
          ) : null}
        </View>

        <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
          ACTIVITY
        </ThemedText>
        <AccountLink label={`Order history (${ordersQuery.data?.length ?? 0})`} onPress={() => router.push('/account/orders' as Href)} />
        <AccountLink label={`Booking history (${bookingsQuery.data?.length ?? 0})`} onPress={() => router.push('/account/bookings' as Href)} />
        <AccountLink label="Messages" onPress={() => router.push('/account/messages' as Href)} />
        <AccountLink
          label={unreadNotifications > 0 ? `Notifications (${unreadNotifications} unread)` : 'Notifications'}
          onPress={() => router.push('/account/notifications' as Href)}
        />
        <AccountLink label="Wishlist" onPress={() => router.push('/account/wishlist' as Href)} />

        <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
          PREFERENCES
        </ThemedText>
        <AccountLink label="Addresses" onPress={() => router.push('/account/addresses' as Href)} />
        <AccountLink label="Saved searches" onPress={() => router.push('/account/saved-searches' as Href)} />

        <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
          SECURITY
        </ThemedText>
        <BiometricLockToggle />

        <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
          ACCOUNT
        </ThemedText>
        <AccountLink label="Sell on StorePilot" onPress={() => router.push('/account/onboarding' as Href)} />
        <TouchableOpacity style={[styles.button, { borderColor: theme.textSecondary }]} onPress={handleExport}>
          <ThemedText themeColor="textSecondary">Export my data</ThemedText>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.button, { borderColor: theme.textSecondary }]}
          onPress={async () => {
            try {
              await logout();
            } finally {
              signOut();
            }
          }}>
          <ThemedText themeColor="textSecondary">Sign out</ThemedText>
        </TouchableOpacity>
        <TouchableOpacity style={[styles.button, styles.deleteButton]} onPress={confirmDelete}>
          <ThemedText style={{ color: '#D64545' }}>Delete account</ThemedText>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { padding: Spacing.three, gap: Spacing.two, paddingBottom: Spacing.six },
  sectionLabel: { marginTop: Spacing.three },
  link: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', borderBottomWidth: 1, paddingVertical: Spacing.two },
  button: { height: 44, borderRadius: 10, borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
  deleteButton: { borderColor: '#D64545', marginTop: Spacing.two },
  signedOutContainer: { flex: 1, justifyContent: 'center', paddingHorizontal: Spacing.four, gap: Spacing.three },
  signedOutTitle: { textAlign: 'center' },
  signedOutSubtitle: { textAlign: 'center', marginBottom: Spacing.two },
  primaryButton: { height: 48, borderRadius: 10, backgroundColor: '#208AEF', alignItems: 'center', justifyContent: 'center' },
  primaryButtonText: { color: '#fff', fontWeight: '600' },
  secondaryButton: { height: 48, borderRadius: 10, borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
  trackLink: { alignItems: 'center', marginTop: Spacing.two },
});
