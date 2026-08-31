import { useQuery } from '@tanstack/react-query';
import { router, useLocalSearchParams, type Href } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, KeyboardAvoidingView, Platform, StyleSheet, TextInput, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { acceptStaffInvite } from '@/api/auth';
import { getInviteDetails } from '@/api/store-staff';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-client';
import { useAuthStore } from '@/store/auth-store';

/**
 * Redeems a store-owner-issued staff invite. No verify-email step — the
 * invite link itself already proves the invitee controls that inbox, unlike
 * RegisterScreen's flow. There's no in-app deep link into this screen (see
 * the mobile staff-accounts plan's note on Universal Links being out of
 * scope) — an invitee opens the emailed link on web, or navigates here
 * manually with the token if they already have the app.
 */
export default function AcceptStaffInviteScreen() {
  const theme = useTheme();
  const signIn = useAuthStore((s) => s.signIn);
  const { token } = useLocalSearchParams<{ token?: string }>();

  const [name, setName] = useState('');
  const [nameEdited, setNameEdited] = useState(false);
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const inviteQuery = useQuery({
    queryKey: ['staff-invite', token],
    queryFn: () => getInviteDetails(token!),
    enabled: !!token,
    retry: false,
  });

  useEffect(() => {
    if (inviteQuery.data && !nameEdited) {
      setName(inviteQuery.data.name);
    }
  }, [inviteQuery.data, nameEdited]);

  const handleAccept = async () => {
    if (!token) return;
    setError(null);
    setSubmitting(true);
    try {
      const session = await acceptStaffInvite(token, password, name.trim() || undefined);
      if (session.signedIn && session.accessToken && session.refreshToken) {
        await signIn({
          accessToken: session.accessToken,
          refreshToken: session.refreshToken,
          role: session.role ?? null,
          email: session.email ?? inviteQuery.data?.email ?? '',
          name: session.name ?? name.trim(),
        });
        router.replace('/dashboard' as Href);
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Something went wrong — please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  if (!token) {
    return (
      <SafeAreaView style={[styles.flex, { backgroundColor: theme.background }]}>
        <ThemedView style={styles.container}>
          <ThemedText type="title" style={styles.title}>
            Invite link missing
          </ThemedText>
          <ThemedText type="small" themeColor="textSecondary" style={styles.subtitle}>
            Open the invite link from your email again to continue.
          </ThemedText>
        </ThemedView>
      </SafeAreaView>
    );
  }

  if (inviteQuery.isLoading) {
    return (
      <SafeAreaView style={[styles.flex, { backgroundColor: theme.background }]}>
        <ThemedView style={[styles.container, styles.centered]}>
          <ActivityIndicator />
        </ThemedView>
      </SafeAreaView>
    );
  }

  if (inviteQuery.isError || !inviteQuery.data) {
    return (
      <SafeAreaView style={[styles.flex, { backgroundColor: theme.background }]}>
        <ThemedView style={styles.container}>
          <ThemedText type="title" style={styles.title}>
            This invite isn&apos;t valid
          </ThemedText>
          <ThemedText type="small" themeColor="textSecondary" style={styles.subtitle}>
            It may have expired or already been used — ask the store owner to send a new one.
          </ThemedText>
        </ThemedView>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={[styles.flex, { backgroundColor: theme.background }]}>
      <KeyboardAvoidingView style={styles.flex} behavior={Platform.select({ ios: 'padding', android: undefined })}>
        <ThemedView style={styles.container}>
          <ThemedText type="title" style={styles.title}>
            Join {inviteQuery.data.storeName}
          </ThemedText>
          <ThemedText type="small" themeColor="textSecondary" style={styles.subtitle}>
            {inviteQuery.data.email}
          </ThemedText>

          <TextInput
            style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
            placeholder="Full name"
            placeholderTextColor={theme.textSecondary}
            value={name}
            onChangeText={(t) => {
              setNameEdited(true);
              setName(t);
            }}
          />
          <TextInput
            style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
            placeholder="Choose a password"
            placeholderTextColor={theme.textSecondary}
            secureTextEntry
            value={password}
            onChangeText={setPassword}
          />
          {error ? <ThemedText style={styles.error}>{error}</ThemedText> : null}
          <TouchableOpacity
            style={[styles.button, { opacity: submitting ? 0.6 : 1 }]}
            disabled={submitting || !name.trim() || password.length < 8}
            onPress={handleAccept}>
            {submitting ? <ActivityIndicator color="#fff" /> : <ThemedText style={styles.buttonText}>Create account</ThemedText>}
          </TouchableOpacity>
        </ThemedView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  container: { flex: 1, justifyContent: 'center', paddingHorizontal: Spacing.four, gap: Spacing.three },
  centered: { alignItems: 'center' },
  title: { fontSize: 28, textAlign: 'center' },
  subtitle: { textAlign: 'center' },
  input: { height: 48, borderRadius: 10, paddingHorizontal: Spacing.three, fontSize: 16 },
  error: { color: '#D64545', textAlign: 'center' },
  button: { height: 48, borderRadius: 10, backgroundColor: '#208AEF', alignItems: 'center', justifyContent: 'center' },
  buttonText: { color: '#fff', fontSize: 16, fontWeight: '600' },
});
