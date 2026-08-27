import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { ActivityIndicator, Alert, StyleSheet, TextInput, TouchableOpacity, View } from 'react-native';
import QRCode from 'react-native-qrcode-svg';
import { SafeAreaView } from 'react-native-safe-area-context';

import { disableMfa, getMfaStatus, startMfaSetup, verifyMfaSetup } from '@/api/mfa';
import type { MfaSetupResponse } from '@/api/types';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-client';

export default function MfaScreen() {
  const theme = useTheme();
  const queryClient = useQueryClient();
  const [setup, setSetup] = useState<MfaSetupResponse | null>(null);
  const [code, setCode] = useState('');

  const statusQuery = useQuery({ queryKey: ['me', 'mfa', 'status'], queryFn: getMfaStatus });

  const setupMutation = useMutation({
    mutationFn: startMfaSetup,
    onSuccess: (data) => setSetup(data),
    onError: (e) => Alert.alert('Could not start setup', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  const verifyMutation = useMutation({
    mutationFn: () => verifyMfaSetup(code),
    onSuccess: () => {
      setSetup(null);
      setCode('');
      queryClient.invalidateQueries({ queryKey: ['me', 'mfa', 'status'] });
      Alert.alert('MFA enabled', 'Two-factor authentication is now required at sign-in.');
    },
    onError: (e) => Alert.alert('Could not verify code', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  const disableMutation = useMutation({
    mutationFn: disableMfa,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['me', 'mfa', 'status'] });
      Alert.alert('MFA disabled', 'Two-factor authentication has been turned off.');
    },
    onError: (e) => Alert.alert('Could not disable MFA', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  const confirmDisable = () => {
    Alert.alert('Disable MFA?', 'Your account will no longer require a code at sign-in.', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Disable', style: 'destructive', onPress: () => disableMutation.mutate() },
    ]);
  };

  if (statusQuery.isLoading) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: theme.background, alignItems: 'center', justifyContent: 'center' }}>
        <ActivityIndicator />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <View style={styles.container}>
        {statusQuery.data?.enabled && !setup ? (
          <>
            <ThemedView type="backgroundElement" style={styles.card}>
              <ThemedText type="smallBold" themeColor="textSecondary">
                STATUS
              </ThemedText>
              <ThemedText type="subtitle">Enabled</ThemedText>
            </ThemedView>
            <TouchableOpacity style={[styles.button, styles.dangerButton]} onPress={confirmDisable} disabled={disableMutation.isPending}>
              {disableMutation.isPending ? <ActivityIndicator color="#fff" /> : <ThemedText style={styles.buttonText}>Disable MFA</ThemedText>}
            </TouchableOpacity>
          </>
        ) : !setup ? (
          <>
            <ThemedView type="backgroundElement" style={styles.card}>
              <ThemedText type="smallBold" themeColor="textSecondary">
                STATUS
              </ThemedText>
              <ThemedText type="subtitle">Disabled</ThemedText>
            </ThemedView>
            <ThemedText type="small" themeColor="textSecondary">
              Add an authenticator app (Google Authenticator, Authy, etc.) as a second step at sign-in.
            </ThemedText>
            <TouchableOpacity style={[styles.button, styles.primaryButton]} onPress={() => setupMutation.mutate()} disabled={setupMutation.isPending}>
              {setupMutation.isPending ? <ActivityIndicator color="#fff" /> : <ThemedText style={styles.buttonText}>Enable MFA</ThemedText>}
            </TouchableOpacity>
          </>
        ) : (
          <>
            <ThemedText style={styles.hint}>Scan this QR code with your authenticator app, then enter the 6-digit code it shows.</ThemedText>
            <View style={styles.qrWrap}>
              <QRCode value={setup.otpauthUri} size={200} />
            </View>
            <ThemedText type="small" themeColor="textSecondary" style={styles.secretLabel}>
              Or enter this code manually:
            </ThemedText>
            <ThemedText type="smallBold" style={styles.secret} selectable>
              {setup.secret}
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
            <TouchableOpacity
              style={[styles.button, styles.primaryButton, code.length !== 6 && styles.buttonDisabled]}
              onPress={() => verifyMutation.mutate()}
              disabled={code.length !== 6 || verifyMutation.isPending}>
              {verifyMutation.isPending ? <ActivityIndicator color="#fff" /> : <ThemedText style={styles.buttonText}>Verify &amp; enable</ThemedText>}
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.button, styles.cancelButton, { borderColor: theme.textSecondary }]}
              onPress={() => {
                setSetup(null);
                setCode('');
              }}>
              <ThemedText themeColor="textSecondary">Cancel</ThemedText>
            </TouchableOpacity>
          </>
        )}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { padding: Spacing.three, gap: Spacing.three },
  card: { borderRadius: 16, padding: Spacing.three, gap: Spacing.half },
  hint: { textAlign: 'center' },
  qrWrap: { alignItems: 'center', padding: Spacing.three, backgroundColor: '#fff', borderRadius: 16 },
  secretLabel: { textAlign: 'center', marginTop: Spacing.two },
  secret: { textAlign: 'center', letterSpacing: 1 },
  input: { height: 48, borderRadius: 10, paddingHorizontal: Spacing.three, fontSize: 20, textAlign: 'center', letterSpacing: 4 },
  button: { height: 50, borderRadius: 12, alignItems: 'center', justifyContent: 'center' },
  buttonDisabled: { opacity: 0.5 },
  primaryButton: { backgroundColor: '#208AEF' },
  dangerButton: { backgroundColor: '#D64545' },
  cancelButton: { borderWidth: 1 },
  buttonText: { color: '#fff', fontWeight: '700' },
});
