import { router, type Href } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, KeyboardAvoidingView, Platform, StyleSheet, TextInput, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { login, register, resendVerificationCode, verifyEmail } from '@/api/auth';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-client';
import { useAuthStore } from '@/store/auth-store';

/**
 * Shared registration for either identity — mirrors LoginScreen's role-agnostic
 * shape. A seller-track account gets no Cognito group at registration (see
 * backend AuthController.register()'s doc comment), so once verified+signed
 * in it's routed to onboarding to actually create a store, instead of just
 * dismissing back like a buyer registration does.
 */
export function RegisterScreen({ accountType }: { accountType: 'buyer' | 'seller' }) {
  const theme = useTheme();
  const signIn = useAuthStore((s) => s.signIn);

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [pendingVerification, setPendingVerification] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleRegister = async () => {
    setError(null);
    setSubmitting(true);
    try {
      await register(name.trim(), email.trim(), password, accountType);
      setPendingVerification(true);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Something went wrong — please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleVerify = async () => {
    setError(null);
    setSubmitting(true);
    try {
      await verifyEmail(email.trim(), code);
      const session = await login(email.trim(), password);
      if (session.signedIn && session.accessToken && session.refreshToken) {
        await signIn({
          accessToken: session.accessToken,
          refreshToken: session.refreshToken,
          role: session.role ?? null,
          email: session.email ?? email.trim(),
          name: session.name ?? name.trim(),
        });
        if (accountType === 'seller') {
          router.replace('/account/onboarding' as Href);
        } else if (router.canGoBack()) {
          router.back();
        }
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Something went wrong — please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <SafeAreaView style={[styles.flex, { backgroundColor: theme.background }]}>
      <KeyboardAvoidingView style={styles.flex} behavior={Platform.select({ ios: 'padding', android: undefined })}>
        <ThemedView style={styles.container}>
          <ThemedText type="title" style={styles.title}>
            {pendingVerification ? 'Verify your email' : accountType === 'seller' ? 'Start selling' : 'Create account'}
          </ThemedText>
          {!pendingVerification && accountType === 'seller' ? (
            <ThemedText type="small" themeColor="textSecondary" style={styles.subtitle}>
              First, create your seller account — you&apos;ll set up your store next.
            </ThemedText>
          ) : null}

          {pendingVerification ? (
            <>
              <ThemedText type="small" themeColor="textSecondary" style={styles.subtitle}>
                We sent a code to {email.trim()}.
              </ThemedText>
              <TextInput
                style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
                placeholder="123456"
                placeholderTextColor={theme.textSecondary}
                keyboardType="number-pad"
                maxLength={6}
                value={code}
                onChangeText={setCode}
              />
              {error ? <ThemedText style={styles.error}>{error}</ThemedText> : null}
              <TouchableOpacity style={[styles.button, { opacity: submitting ? 0.6 : 1 }]} disabled={submitting || code.length !== 6} onPress={handleVerify}>
                {submitting ? <ActivityIndicator color="#fff" /> : <ThemedText style={styles.buttonText}>Verify</ThemedText>}
              </TouchableOpacity>
              <TouchableOpacity style={styles.resendLink} onPress={() => resendVerificationCode(email.trim())}>
                <ThemedText type="small" themeColor="textSecondary">
                  Resend code
                </ThemedText>
              </TouchableOpacity>
            </>
          ) : (
            <>
              <TextInput
                style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
                placeholder="Full name"
                placeholderTextColor={theme.textSecondary}
                value={name}
                onChangeText={setName}
              />
              <TextInput
                style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
                placeholder="Email"
                placeholderTextColor={theme.textSecondary}
                autoCapitalize="none"
                keyboardType="email-address"
                value={email}
                onChangeText={setEmail}
              />
              <TextInput
                style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
                placeholder="Password"
                placeholderTextColor={theme.textSecondary}
                secureTextEntry
                value={password}
                onChangeText={setPassword}
              />
              {error ? <ThemedText style={styles.error}>{error}</ThemedText> : null}
              <TouchableOpacity
                style={[styles.button, { opacity: submitting ? 0.6 : 1 }]}
                disabled={submitting || !name.trim() || !email.trim() || password.length < 8}
                onPress={handleRegister}>
                {submitting ? <ActivityIndicator color="#fff" /> : <ThemedText style={styles.buttonText}>Create account</ThemedText>}
              </TouchableOpacity>
              {accountType === 'seller' ? (
                <TouchableOpacity style={styles.resendLink} onPress={() => router.push('/account/login' as Href)}>
                  <ThemedText type="small" themeColor="textSecondary">
                    Already have a seller account? <ThemedText type="small">Sign in</ThemedText>
                  </ThemedText>
                </TouchableOpacity>
              ) : null}
            </>
          )}
        </ThemedView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  container: { flex: 1, justifyContent: 'center', paddingHorizontal: Spacing.four, gap: Spacing.three },
  title: { fontSize: 28, textAlign: 'center' },
  subtitle: { textAlign: 'center' },
  input: { height: 48, borderRadius: 10, paddingHorizontal: Spacing.three, fontSize: 16 },
  error: { color: '#D64545', textAlign: 'center' },
  button: { height: 48, borderRadius: 10, backgroundColor: '#208AEF', alignItems: 'center', justifyContent: 'center' },
  buttonText: { color: '#fff', fontSize: 16, fontWeight: '600' },
  resendLink: { alignItems: 'center' },
});
