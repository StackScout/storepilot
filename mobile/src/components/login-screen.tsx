import { useState } from 'react';
import { ActivityIndicator, KeyboardAvoidingView, Platform, StyleSheet, TextInput, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { login, mfaChallenge as mfaChallengeRequest } from '@/api/auth';
import { ApiError } from '@/lib/api-client';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useAuthStore } from '@/store/auth-store';

/**
 * Seller sign-in — mirrors the web app's root /login (a seller-only Cognito
 * identity, distinct from buyer auth at /account/login on web). MFA is a
 * later phase: if the backend returns mfaRequired, we ask for the TOTP code
 * inline rather than failing silently, but there's no enrollment flow yet.
 */
export function LoginScreen() {
  const theme = useTheme();
  const signIn = useAuthStore((s) => s.signIn);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [mfaSession, setMfaSession] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

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
            Seller sign in
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
