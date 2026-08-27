import { router, type Href } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, KeyboardAvoidingView, Platform, StyleSheet, TextInput, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { login, mfaChallenge as mfaChallengeRequest, signInWithGoogle } from '@/api/auth';
import { ApiError } from '@/lib/api-client';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useAuthStore } from '@/store/auth-store';

/**
 * Shared sign-in for either identity — mirrors how the web app's /login and
 * /account/login both just call the same authService.login() and branch on
 * whatever role comes back. Rendered as a pushed route (from the buyer
 * Account tab), not a root-level gate: the root layout reactively swaps to
 * the seller dashboard once role becomes "seller" in the auth store; for a
 * buyer sign-in, this screen dismisses itself back to the tabs it was
 * opened from.
 */
export function LoginScreen({ showRegisterLink = false }: { showRegisterLink?: boolean }) {
  const theme = useTheme();
  const signIn = useAuthStore((s) => s.signIn);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [mfaSession, setMfaSession] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleGoogleSignIn = async () => {
    setError(null);
    setSubmitting(true);
    try {
      const result = await signInWithGoogle('buyer');
      if (result?.signedIn && result.accessToken && result.refreshToken) {
        await signIn({
          accessToken: result.accessToken,
          refreshToken: result.refreshToken,
          role: result.role ?? 'buyer',
          email: result.email ?? null,
          name: result.name ?? null,
        });
        if (router.canGoBack()) router.back();
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Google sign-in failed — please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleSubmit = async () => {
    setError(null);
    setSubmitting(true);
    try {
      const result = mfaSession
        ? await mfaChallengeRequest(email, mfaSession, code)
        : await login(email, password);

      if (result.mfaRequired && result.mfaSession) {
        setMfaSession(result.mfaSession);
        return;
      }

      if (result.signedIn && result.accessToken && result.refreshToken) {
        await signIn({
          accessToken: result.accessToken,
          refreshToken: result.refreshToken,
          role: result.role ?? null,
          email: result.email ?? null,
          name: result.name ?? null,
        });
        // A seller sign-in swaps the whole root tree out from under this
        // screen automatically (see _layout.tsx); a buyer sign-in just
        // needs to dismiss back to wherever this was opened from.
        if (result.role !== 'seller' && router.canGoBack()) router.back();
      } else {
        setError('Sign-in did not complete — please try again.');
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Something went wrong — please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <SafeAreaView style={[styles.flex, { backgroundColor: theme.background }]}>
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.select({ ios: 'padding', android: undefined })}>
        <ThemedView style={styles.container}>
          <ThemedText type="title" style={styles.title}>
            StorePilot
          </ThemedText>
          <ThemedText type="subtitle" themeColor="textSecondary" style={styles.subtitle}>
            Sign in
          </ThemedText>

          {!mfaSession ? (
            <>
              <TextInput
                style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
                placeholder="Email"
                placeholderTextColor={theme.textSecondary}
                autoCapitalize="none"
                keyboardType="email-address"
                autoComplete="email"
                value={email}
                onChangeText={setEmail}
              />
              <TextInput
                style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
                placeholder="Password"
                placeholderTextColor={theme.textSecondary}
                secureTextEntry
                autoComplete="password"
                value={password}
                onChangeText={setPassword}
              />
            </>
          ) : (
            <>
              <ThemedText style={styles.mfaHint}>Enter the 6-digit code from your authenticator app.</ThemedText>
              <TextInput
                style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
                placeholder="123456"
                placeholderTextColor={theme.textSecondary}
                keyboardType="number-pad"
                maxLength={6}
                value={code}
                onChangeText={setCode}
              />
            </>
          )}

          {error ? (
            <ThemedText style={styles.error} themeColor="text">
              {error}
            </ThemedText>
          ) : null}

          <TouchableOpacity
            style={[styles.button, { opacity: submitting ? 0.6 : 1 }]}
            onPress={handleSubmit}
            disabled={submitting || !email || !password || (!!mfaSession && code.length !== 6)}>
            {submitting ? <ActivityIndicator color="#fff" /> : <ThemedText style={styles.buttonText}>{mfaSession ? 'Verify' : 'Sign in'}</ThemedText>}
          </TouchableOpacity>

          {showRegisterLink && !mfaSession ? (
            <>
              <TouchableOpacity style={[styles.googleButton, { borderColor: theme.textSecondary }]} disabled={submitting} onPress={handleGoogleSignIn}>
                <ThemedText>Continue with Google</ThemedText>
              </TouchableOpacity>
              <TouchableOpacity style={styles.registerLink} onPress={() => router.push('/account/register' as Href)}>
                <ThemedText type="small" themeColor="textSecondary">
                  New here? <ThemedText type="small">Create an account</ThemedText>
                </ThemedText>
              </TouchableOpacity>
            </>
          ) : null}
        </ThemedView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  container: {
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: Spacing.four,
    gap: Spacing.three,
  },
  title: { fontSize: 36, textAlign: 'center' },
  subtitle: { fontSize: 16, textAlign: 'center', marginBottom: Spacing.three },
  input: {
    height: 48,
    borderRadius: 10,
    paddingHorizontal: Spacing.three,
    fontSize: 16,
  },
  mfaHint: { textAlign: 'center', marginBottom: Spacing.two },
  registerLink: { alignItems: 'center', marginTop: Spacing.three },
  googleButton: { height: 48, borderRadius: 10, borderWidth: 1, alignItems: 'center', justifyContent: 'center', marginTop: Spacing.three },
  error: { color: '#D64545', textAlign: 'center' },
  button: {
    height: 48,
    borderRadius: 10,
    backgroundColor: '#208AEF',
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: Spacing.two,
  },
  buttonText: { color: '#fff', fontSize: 16, fontWeight: '600' },
});
