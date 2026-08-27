import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, StyleSheet, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { logout } from '@/api/auth';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { authenticateWithBiometrics } from '@/lib/biometrics';
import { useAuthStore } from '@/store/auth-store';

/** Rendered by the root layout in place of the whole app whenever isLocked is true — cold start with a stored session, or returning from the background. Auto-prompts once on mount so unlocking feels immediate rather than requiring an extra tap. */
export function BiometricLockScreen() {
  const theme = useTheme();
  const unlock = useAuthStore((s) => s.unlock);
  const signOut = useAuthStore((s) => s.signOut);
  const [authenticating, setAuthenticating] = useState(false);
  const [failed, setFailed] = useState(false);
  // Guards the state updates after the `await` below — the native prompt can resolve (e.g. cancelled via the hardware back button) right as this screen is being torn down/rebuilt, and setting state outside that window throws.
  const isMountedRef = useRef(false);

  const attempt = async () => {
    setAuthenticating(true);
    setFailed(false);
    const success = await authenticateWithBiometrics('Unlock StorePilot');
    if (!isMountedRef.current) return;
    setAuthenticating(false);
    if (success) unlock();
    else setFailed(true);
  };

  useEffect(() => {
    isMountedRef.current = true;
    // Fires exactly once per lock — this component only mounts when isLocked flips true, not on every render.
    attempt();
    return () => {
      isMountedRef.current = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSignOut = async () => {
    try {
      await logout();
    } finally {
      signOut();
    }
  };

  return (
    <SafeAreaView style={[StyleSheet.absoluteFill, styles.container, { backgroundColor: theme.background }]}>
      <View style={styles.content}>
        <ThemedText type="title" style={styles.title}>
          StorePilot
        </ThemedText>
        <ThemedText themeColor="textSecondary" style={styles.subtitle}>
          {failed ? 'Authentication failed. Try again.' : 'Unlock with Face ID or fingerprint to continue.'}
        </ThemedText>
        {authenticating ? (
          <ActivityIndicator style={styles.spinner} />
        ) : (
          <TouchableOpacity style={styles.primaryButton} onPress={attempt}>
            <ThemedText style={styles.primaryButtonText}>Try again</ThemedText>
          </TouchableOpacity>
        )}
        <TouchableOpacity style={styles.signOutLink} onPress={handleSignOut}>
          <ThemedText type="small" themeColor="textSecondary">
            Sign out instead
          </ThemedText>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { flex: 1, justifyContent: 'center', alignItems: 'center', paddingHorizontal: Spacing.four, gap: Spacing.three },
  title: { fontSize: 32, lineHeight: 38 },
  subtitle: { textAlign: 'center' },
  spinner: { marginTop: Spacing.two },
  primaryButton: { height: 48, minWidth: 180, borderRadius: 10, backgroundColor: '#208AEF', alignItems: 'center', justifyContent: 'center' },
  primaryButtonText: { color: '#fff', fontWeight: '600' },
  signOutLink: { marginTop: Spacing.four },
});
