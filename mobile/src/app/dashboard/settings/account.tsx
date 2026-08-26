import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { ActivityIndicator, Alert, ScrollView, StyleSheet, TextInput, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { deleteSellerAccount, exportSellerData } from '@/api/seller-account';
import { closeStore, getMyStore } from '@/api/stores';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-client';
import { useAuthStore } from '@/store/auth-store';

export default function SellerAccountScreen() {
  const theme = useTheme();
  const queryClient = useQueryClient();
  const signOut = useAuthStore((s) => s.signOut);
  const [confirmText, setConfirmText] = useState('');
  const [exporting, setExporting] = useState(false);

  const storeQuery = useQuery({ queryKey: ['me', 'store'], queryFn: getMyStore });
  const store = storeQuery.data;
  const isClosed = !store || store.verificationStatus === 'closed';

  const closeMutation = useMutation({
    mutationFn: () => closeStore(store!.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['me', 'store'] });
      Alert.alert('Store closed', 'Your store is no longer visible to buyers.');
    },
    onError: (e) => Alert.alert('Could not close store', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  const deleteMutation = useMutation({
    mutationFn: deleteSellerAccount,
    onSuccess: () => signOut(),
    onError: (e) => Alert.alert('Could not delete account', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  const confirmCloseStore = () => {
    Alert.alert('Close your store?', 'Buyers will no longer be able to find or order from your store. This cannot be undone.', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Close store', style: 'destructive', onPress: () => closeMutation.mutate() },
    ]);
  };

  const confirmDelete = () => {
    Alert.alert('Delete your account?', 'This permanently deletes your seller account and all associated data. This cannot be undone.', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Delete', style: 'destructive', onPress: () => deleteMutation.mutate() },
    ]);
  };

  const handleExport = async () => {
    setExporting(true);
    try {
      const data = await exportSellerData();
      Alert.alert(
        'Export ready',
        `${data.products.length} products, ${data.orders.length} orders, ${data.bookings.length} bookings, ${data.payouts.length} payouts. Full JSON export is available from the web dashboard.`,
      );
    } catch (e) {
      Alert.alert('Could not export data', e instanceof ApiError ? e.message : 'Please try again.');
    } finally {
      setExporting(false);
    }
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <ScrollView contentContainerStyle={styles.container}>
        {store && !isClosed ? (
          <>
            <ThemedText type="smallBold" themeColor="textSecondary">
              STORE
            </ThemedText>
            <ThemedView type="backgroundElement" style={styles.card}>
              <ThemedText type="smallBold">{store.name}</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                {store.verificationStatus}
              </ThemedText>
            </ThemedView>
            <TouchableOpacity style={[styles.button, styles.dangerOutline, { borderColor: '#D64545' }]} onPress={confirmCloseStore} disabled={closeMutation.isPending}>
              {closeMutation.isPending ? <ActivityIndicator /> : <ThemedText style={styles.dangerText}>Close store</ThemedText>}
            </TouchableOpacity>
          </>
        ) : null}

        <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
          YOUR DATA
        </ThemedText>
        <TouchableOpacity style={[styles.button, { borderColor: theme.textSecondary, borderWidth: 1 }]} onPress={handleExport} disabled={exporting}>
          {exporting ? <ActivityIndicator /> : <ThemedText themeColor="textSecondary">Export my data</ThemedText>}
        </TouchableOpacity>

        <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
          DELETE ACCOUNT
        </ThemedText>
        {!isClosed ? (
          <ThemedText type="small" themeColor="textSecondary">
            Close your store before you can delete your account.
          </ThemedText>
        ) : (
          <>
            <ThemedText type="small" themeColor="textSecondary">
              This permanently deletes your seller account. Type DELETE to confirm.
            </ThemedText>
            <TextInput
              style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
              placeholder="DELETE"
              placeholderTextColor={theme.textSecondary}
              autoCapitalize="characters"
              value={confirmText}
              onChangeText={setConfirmText}
            />
            <TouchableOpacity
              style={[styles.button, styles.dangerButton, confirmText !== 'DELETE' && styles.buttonDisabled]}
              onPress={confirmDelete}
              disabled={confirmText !== 'DELETE' || deleteMutation.isPending}>
              {deleteMutation.isPending ? <ActivityIndicator color="#fff" /> : <ThemedText style={styles.buttonText}>Delete my account</ThemedText>}
            </TouchableOpacity>
          </>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { padding: Spacing.three, gap: Spacing.two, paddingBottom: Spacing.six },
  sectionLabel: { marginTop: Spacing.three },
  card: { borderRadius: 16, padding: Spacing.three, gap: Spacing.half },
  input: { height: 48, borderRadius: 10, paddingHorizontal: Spacing.three, fontSize: 16 },
  button: { height: 50, borderRadius: 12, alignItems: 'center', justifyContent: 'center' },
  buttonDisabled: { opacity: 0.5 },
  dangerButton: { backgroundColor: '#D64545' },
  dangerOutline: { borderWidth: 1 },
  dangerText: { color: '#D64545', fontWeight: '700' },
  buttonText: { color: '#fff', fontWeight: '700' },
});
