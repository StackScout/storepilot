import { useRouter } from 'expo-router';
import { ScrollView, StyleSheet, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { logout } from '@/api/auth';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { usePlatformConfig } from '@/lib/platform-config';
import { useTheme } from '@/hooks/use-theme';
import { useAuthStore } from '@/store/auth-store';

const BASE_LINKS: { label: string; path: string }[] = [
  { label: 'Store settings', path: '/dashboard/settings/store' },
  { label: 'Store profile', path: '/dashboard/settings/profile' },
  { label: 'Billing', path: '/dashboard/settings/billing' },
  { label: 'Payouts', path: '/dashboard/settings/payouts' },
  { label: 'Fee collections', path: '/dashboard/settings/fee-collections' },
  { label: 'Coupons', path: '/dashboard/settings/coupons' },
  { label: 'Two-factor authentication', path: '/dashboard/settings/mfa' },
  { label: 'Account', path: '/dashboard/settings/account' },
];

export default function SettingsHubScreen() {
  const theme = useTheme();
  const router = useRouter();
  const signOut = useAuthStore((s) => s.signOut);
  const { proPlanEnabled, countryCode, defaultCodEnabled, defaultBankTransferEnabled } = usePlatformConfig();
  // Payouts only ever contain PayHere-funded money (LK-only) — see
  // backend PayoutService.getEligibleOrders' doc comment; everywhere else
  // this screen would always be empty.
  const showPayouts = countryCode === 'LK';
  // Fee collections only ever contain cod/bank-transfer orders — dead
  // weight on a deployment that doesn't offer either at all, see backend
  // PlatformSettings' default*Enabled doc comment.
  const showFees = defaultCodEnabled || defaultBankTransferEnabled;
  const links = BASE_LINKS.filter((l) => {
    if (l.path === '/dashboard/settings/billing') return proPlanEnabled;
    if (l.path === '/dashboard/settings/payouts') return showPayouts;
    if (l.path === '/dashboard/settings/fee-collections') return showFees;
    return true;
  });

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <ScrollView contentContainerStyle={styles.list}>
        {links.map((link) => (
          <TouchableOpacity
            key={link.path}
            style={[styles.row, { backgroundColor: theme.backgroundElement }]}
            onPress={() => router.push(link.path as never)}>
            <ThemedText type="smallBold">{link.label}</ThemedText>
          </TouchableOpacity>
        ))}

        <TouchableOpacity
          style={[styles.row, styles.signOutRow, { borderColor: theme.backgroundElement }]}
          onPress={async () => {
            try {
              await logout();
            } finally {
              signOut();
            }
          }}>
          <ThemedText type="smallBold" style={{ color: '#D64545' }}>
            Sign out
          </ThemedText>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  list: { padding: Spacing.three, gap: Spacing.two },
  row: { borderRadius: 14, padding: Spacing.three },
  signOutRow: { marginTop: Spacing.four, borderWidth: 1, backgroundColor: 'transparent' },
});
