import { StyleSheet, Switch, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useBiometricAvailability } from '@/lib/biometrics';
import { useAuthStore } from '@/store/auth-store';

/** Shared between the buyer and seller account screens. Renders nothing when the device has no biometric hardware enrolled — the setting would be meaningless (and confusing) to show. */
export function BiometricLockToggle() {
  const theme = useTheme();
  const available = useBiometricAvailability();
  const enabled = useAuthStore((s) => s.biometricLockEnabled);
  const setEnabled = useAuthStore((s) => s.setBiometricLockEnabled);

  if (!available) return null;

  return (
    <View style={[styles.row, { borderColor: theme.backgroundElement }]}>
      <ThemedText>Require Face ID / fingerprint</ThemedText>
      <Switch value={enabled} onValueChange={setEnabled} />
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', borderBottomWidth: 1, paddingVertical: Spacing.two },
});
