import { useRouter } from 'expo-router';
import { ScrollView, StyleSheet, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

const LINKS: { label: string; path: string }[] = [
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

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <ScrollView contentContainerStyle={styles.list}>
        {LINKS.map((link) => (
          <TouchableOpacity
            key={link.path}
            style={[styles.row, { backgroundColor: theme.backgroundElement }]}
            onPress={() => router.push(link.path as never)}>
            <ThemedText type="smallBold">{link.label}</ThemedText>
          </TouchableOpacity>
        ))}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  list: { padding: Spacing.three, gap: Spacing.two },
  row: { borderRadius: 14, padding: Spacing.three },
});
