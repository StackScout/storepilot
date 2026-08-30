import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as ImagePicker from 'expo-image-picker';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Alert, ScrollView, StyleSheet, Switch, TextInput, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { getMyStore } from '@/api/stores';
import {
  getStoreSettings,
  updateStoreSettings,
  uploadAbnDocument,
  uploadBusinessRegDocument,
  uploadDriverLicenceDocument,
  uploadNicDocument,
} from '@/api/store-settings';
import type { StoreSettingsInput, StoreSettingsResponse } from '@/api/types';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-client';
import { usePlatformConfig } from '@/lib/platform-config';

function Field({ label, value, onChangeText, editable = true, keyboardType }: {
  label: string;
  value: string;
  onChangeText: (v: string) => void;
  editable?: boolean;
  keyboardType?: 'default' | 'email-address' | 'phone-pad' | 'decimal-pad';
}) {
  const theme = useTheme();
  return (
    <View style={styles.field}>
      <ThemedText type="small" themeColor="textSecondary">
        {label}
      </ThemedText>
      <TextInput
        style={[styles.input, { color: theme.text, backgroundColor: editable ? theme.backgroundElement : theme.background, borderColor: theme.backgroundElement, borderWidth: editable ? 0 : 1 }]}
        value={value}
        onChangeText={onChangeText}
        editable={editable}
        keyboardType={keyboardType}
        placeholderTextColor={theme.textSecondary}
      />
    </View>
  );
}

function ToggleRow({ label, value, onValueChange, disabled }: { label: string; value: boolean; onValueChange: (v: boolean) => void; disabled?: boolean }) {
  return (
    <View style={styles.toggleRow}>
      <ThemedText style={disabled ? styles.disabledText : undefined}>{label}</ThemedText>
      <Switch value={value} onValueChange={onValueChange} disabled={disabled} />
    </View>
  );
}

function DocumentButton({ label, url, uploading, onPick }: { label: string; url: string | undefined; uploading: boolean; onPick: () => void }) {
  const theme = useTheme();
  return (
    <TouchableOpacity style={[styles.docButton, { borderColor: theme.textSecondary }]} onPress={onPick} disabled={uploading}>
      {uploading ? (
        <ActivityIndicator />
      ) : (
        <ThemedText themeColor="textSecondary">
          {label} {url ? '· uploaded' : '· not uploaded'}
        </ThemedText>
      )}
    </TouchableOpacity>
  );
}

export default function StoreSettingsScreen() {
  const theme = useTheme();
  const queryClient = useQueryClient();
  const [uploadingDoc, setUploadingDoc] = useState<string | null>(null);
  const { proPlanEnabled, defaultCodEnabled: platformCodEnabled, defaultBankTransferEnabled: platformBankTransferEnabled } = usePlatformConfig();
  const needsBankDetails = platformCodEnabled || platformBankTransferEnabled;

  const storeQuery = useQuery({ queryKey: ['me', 'store'], queryFn: getMyStore });
  const storeId = storeQuery.data?.id;
  const isActive = storeQuery.data?.verificationStatus === 'active';

  const settingsQuery = useQuery({
    queryKey: ['store', storeId, 'settings'],
    queryFn: () => getStoreSettings(storeId!),
    enabled: !!storeId,
  });

  const [form, setForm] = useState<StoreSettingsResponse | null>(null);
  useEffect(() => {
    if (settingsQuery.data) setForm(settingsQuery.data);
  }, [settingsQuery.data]);

  const saveMutation = useMutation({
    mutationFn: (input: StoreSettingsInput) => updateStoreSettings(storeId!, input),
    onSuccess: (data) => {
      setForm(data);
      queryClient.invalidateQueries({ queryKey: ['store', storeId, 'settings'] });
      Alert.alert('Saved', 'Store settings updated.');
    },
    onError: (e) => Alert.alert('Could not save settings', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  const uploadDoc = async (
    kind: 'driverLicence' | 'abn' | 'nic' | 'businessReg',
    fn: (storeId: string, uri: string) => Promise<StoreSettingsResponse>,
  ) => {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      Alert.alert('Permission needed', 'Allow photo library access to upload a document.');
      return;
    }
    const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.8 });
    if (result.canceled || !result.assets[0]) return;
    setUploadingDoc(kind);
    try {
      const updated = await fn(storeId!, result.assets[0].uri);
      setForm(updated);
      queryClient.invalidateQueries({ queryKey: ['store', storeId, 'settings'] });
    } catch (e) {
      Alert.alert('Could not upload document', e instanceof ApiError ? e.message : 'Please try again.');
    } finally {
      setUploadingDoc(null);
    }
  };

  if (settingsQuery.isError) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: theme.background, alignItems: 'center', justifyContent: 'center', padding: Spacing.three }}>
        <ThemedText themeColor="textSecondary">
          {settingsQuery.error instanceof ApiError ? settingsQuery.error.message : 'Could not load store settings.'}
        </ThemedText>
      </SafeAreaView>
    );
  }

  if (storeQuery.isLoading || settingsQuery.isLoading || !form) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: theme.background, alignItems: 'center', justifyContent: 'center' }}>
        <ActivityIndicator />
      </SafeAreaView>
    );
  }

  const submit = () => {
    // Blank optional fields must be omitted, not sent as "" — e.g. contactPhone has a server-side
    // @Size(min=9) that an empty string fails but an absent/undefined field skips.
    const blankToUndefined = (v: string) => (v.trim() ? v : undefined);
    saveMutation.mutate({
      contactEmail: blankToUndefined(form.contactEmail),
      contactPhone: blankToUndefined(form.contactPhone),
      bankAccountName: blankToUndefined(form.bankAccountName),
      bankAccountNumber: blankToUndefined(form.bankAccountNumber),
      bankName: blankToUndefined(form.bankName),
      // Hidden (not just disabled) below when the platform doesn't offer
      // the method at all — clamp here too so a stale true value from
      // before the platform setting changed never gets resubmitted just
      // because its toggle isn't rendered to switch off.
      codEnabled: platformCodEnabled && form.codEnabled,
      onlinePaymentEnabled: form.onlinePaymentEnabled,
      bankTransferEnabled: platformBankTransferEnabled && form.bankTransferEnabled,
      abn: form.abn ? blankToUndefined(form.abn) : undefined,
      driverLicenceNumber: form.driverLicenceNumber ? blankToUndefined(form.driverLicenceNumber) : undefined,
      nicNumber: form.nicNumber ? blankToUndefined(form.nicNumber) : undefined,
      businessRegistrationNumber: form.businessRegistrationNumber ? blankToUndefined(form.businessRegistrationNumber) : undefined,
      stockManagementEnabled: form.stockManagementEnabled,
      pickupEnabled: form.pickupEnabled,
      gstRegistered: form.gstRegistered,
    });
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <ScrollView contentContainerStyle={styles.container}>
        {isActive ? (
          <ThemedText type="small" themeColor="textSecondary">
            Your store is verified — identity fields (seller type, ABN, licence/registration numbers) are locked. Contact support to
            change them.
          </ThemedText>
        ) : null}

        <ThemedText type="smallBold" themeColor="textSecondary">
          CONTACT
        </ThemedText>
        <Field label="Contact email" value={form.contactEmail} onChangeText={(v) => setForm({ ...form, contactEmail: v })} keyboardType="email-address" />
        <Field label="Contact phone" value={form.contactPhone} onChangeText={(v) => setForm({ ...form, contactPhone: v })} keyboardType="phone-pad" />

        <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
          PAYMENT METHODS
        </ThemedText>
        {platformCodEnabled ? (
          <ToggleRow label="Cash on delivery" value={form.codEnabled} onValueChange={(v) => setForm({ ...form, codEnabled: v })} />
        ) : null}
        <ToggleRow label="Online payment (card)" value={form.onlinePaymentEnabled} onValueChange={(v) => setForm({ ...form, onlinePaymentEnabled: v })} />
        {platformBankTransferEnabled ? (
          <ToggleRow label="Bank transfer" value={form.bankTransferEnabled} onValueChange={(v) => setForm({ ...form, bankTransferEnabled: v })} />
        ) : null}
        {proPlanEnabled && (platformCodEnabled || platformBankTransferEnabled) ? (
          <ThemedText type="small" themeColor="textSecondary">
            Cash on delivery and bank transfer require the Pro plan — the server ignores these toggles on the Free plan.
          </ThemedText>
        ) : null}

        {needsBankDetails ? (
          <>
            <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
              BANK DETAILS
            </ThemedText>
            <Field label="Bank name" value={form.bankName} onChangeText={(v) => setForm({ ...form, bankName: v })} />
            <Field label="Account name" value={form.bankAccountName} onChangeText={(v) => setForm({ ...form, bankAccountName: v })} />
            <Field label="Account number" value={form.bankAccountNumber} onChangeText={(v) => setForm({ ...form, bankAccountNumber: v })} />
          </>
        ) : null}

        <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
          TAX &amp; IDENTITY
        </ThemedText>
        <ToggleRow
          label="GST registered"
          value={form.gstRegistered}
          onValueChange={(v) => setForm({ ...form, gstRegistered: v })}
          disabled={isActive}
        />
        <Field label="ABN" value={form.abn ?? ''} onChangeText={(v) => setForm({ ...form, abn: v })} editable={!isActive} />
        <Field label="Driver licence number" value={form.driverLicenceNumber ?? ''} onChangeText={(v) => setForm({ ...form, driverLicenceNumber: v })} editable={!isActive} />
        <Field label="NIC number" value={form.nicNumber ?? ''} onChangeText={(v) => setForm({ ...form, nicNumber: v })} editable={!isActive} />
        <Field label="Business registration number" value={form.businessRegistrationNumber ?? ''} onChangeText={(v) => setForm({ ...form, businessRegistrationNumber: v })} editable={!isActive} />

        {!isActive ? (
          <>
            <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
              VERIFICATION DOCUMENTS
            </ThemedText>
            <DocumentButton
              label="Driver licence"
              url={form.driverLicenceDocumentUrl}
              uploading={uploadingDoc === 'driverLicence'}
              onPick={() => uploadDoc('driverLicence', uploadDriverLicenceDocument)}
            />
            <DocumentButton label="ABN document" url={form.abnDocumentUrl} uploading={uploadingDoc === 'abn'} onPick={() => uploadDoc('abn', uploadAbnDocument)} />
            <DocumentButton label="NIC document" url={form.nicDocumentUrl} uploading={uploadingDoc === 'nic'} onPick={() => uploadDoc('nic', uploadNicDocument)} />
            <DocumentButton
              label="Business registration"
              url={form.businessRegDocumentUrl}
              uploading={uploadingDoc === 'businessReg'}
              onPick={() => uploadDoc('businessReg', uploadBusinessRegDocument)}
            />
          </>
        ) : null}

        <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
          FULFILMENT
        </ThemedText>
        <ToggleRow label="Track stock" value={form.stockManagementEnabled} onValueChange={(v) => setForm({ ...form, stockManagementEnabled: v })} />
        <ToggleRow label="Pickup available" value={form.pickupEnabled} onValueChange={(v) => setForm({ ...form, pickupEnabled: v })} />

        <TouchableOpacity style={[styles.submit, saveMutation.isPending && styles.submitDisabled]} onPress={submit} disabled={saveMutation.isPending}>
          {saveMutation.isPending ? <ActivityIndicator color="#fff" /> : <ThemedText style={styles.submitText}>Save settings</ThemedText>}
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { padding: Spacing.three, gap: Spacing.two, paddingBottom: Spacing.six },
  sectionLabel: { marginTop: Spacing.three },
  field: { gap: 4 },
  input: { height: 44, borderRadius: 10, paddingHorizontal: Spacing.three, fontSize: 16 },
  toggleRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: Spacing.one },
  disabledText: { opacity: 0.5 },
  docButton: { height: 44, borderRadius: 10, borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
  submit: { height: 50, borderRadius: 12, backgroundColor: '#208AEF', alignItems: 'center', justifyContent: 'center', marginTop: Spacing.three },
  submitDisabled: { opacity: 0.6 },
  submitText: { color: '#fff', fontWeight: '700' },
});
