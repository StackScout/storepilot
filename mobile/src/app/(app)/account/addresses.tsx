import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Alert, ScrollView, StyleSheet, TextInput, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import type { AddressInput } from '@storepilot/shared-api';

import { createAddress, deleteAddress, listAddresses, setDefaultAddress } from '@/api/buyer-addresses';
import { EmptyState } from '@/components/empty-state';
import { SelectField } from '@/components/select-field';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-client';
import { useStates } from '@/lib/platform-config';

const BLANK: AddressInput = { label: '', shipping: { fullName: '', phone: '', addressLine1: '', city: '', state: '', postalCode: '' }, isDefault: false };

export default function AddressesScreen() {
  const theme = useTheme();
  const queryClient = useQueryClient();
  const addressesQuery = useQuery({ queryKey: ['me', 'addresses'], queryFn: listAddresses });
  const statesQuery = useStates();
  const stateOptions = (statesQuery.data ?? []).map((s) => s.name);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<AddressInput>(BLANK);

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['me', 'addresses'] });

  const createMutation = useMutation({
    mutationFn: () => createAddress(form),
    onSuccess: () => {
      invalidate();
      setShowForm(false);
      setForm(BLANK);
    },
    onError: (e) => Alert.alert('Could not save address', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteAddress(id),
    onSuccess: invalidate,
    onError: (e) => Alert.alert('Could not delete', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  const defaultMutation = useMutation({
    mutationFn: (id: string) => setDefaultAddress(id),
    onSuccess: invalidate,
    onError: (e) => Alert.alert('Could not update', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  const canSubmit = !!form.shipping.fullName && !!form.shipping.phone && !!form.shipping.addressLine1 && !!form.shipping.city && !!form.shipping.state && !!form.shipping.postalCode;

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <ScrollView contentContainerStyle={styles.container}>
        {(addressesQuery.data ?? []).length === 0 && !showForm ? <EmptyState title="No saved addresses" /> : null}

        {(addressesQuery.data ?? []).map((address) => (
          <View key={address.id} style={[styles.card, { borderColor: theme.backgroundElement }]}>
            <View style={styles.cardHeader}>
              <ThemedText type="smallBold">{address.label || address.shipping.fullName}</ThemedText>
              {address.isDefault ? (
                <ThemedText type="small" themeColor="textSecondary">
                  Default
                </ThemedText>
              ) : null}
            </View>
            <ThemedText type="small" themeColor="textSecondary">
              {address.shipping.addressLine1}, {address.shipping.city}, {address.shipping.state} {address.shipping.postalCode}
            </ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {address.shipping.phone}
            </ThemedText>
            <View style={styles.cardActions}>
              {!address.isDefault ? (
                <TouchableOpacity onPress={() => defaultMutation.mutate(address.id)}>
                  <ThemedText type="small">Set as default</ThemedText>
                </TouchableOpacity>
              ) : null}
              <TouchableOpacity onPress={() => deleteMutation.mutate(address.id)}>
                <ThemedText type="small" style={{ color: '#D64545' }}>
                  Delete
                </ThemedText>
              </TouchableOpacity>
            </View>
          </View>
        ))}

        {showForm ? (
          <View style={styles.form}>
            <TextInput
              style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
              placeholder="Label (e.g. Home)"
              placeholderTextColor={theme.textSecondary}
              value={form.label}
              onChangeText={(v) => setForm((f) => ({ ...f, label: v }))}
            />
            <TextInput
              style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
              placeholder="Full name"
              placeholderTextColor={theme.textSecondary}
              value={form.shipping.fullName}
              onChangeText={(v) => setForm((f) => ({ ...f, shipping: { ...f.shipping, fullName: v } }))}
            />
            <TextInput
              style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
              placeholder="Phone"
              placeholderTextColor={theme.textSecondary}
              keyboardType="phone-pad"
              value={form.shipping.phone}
              onChangeText={(v) => setForm((f) => ({ ...f, shipping: { ...f.shipping, phone: v } }))}
            />
            <TextInput
              style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
              placeholder="Address"
              placeholderTextColor={theme.textSecondary}
              value={form.shipping.addressLine1}
              onChangeText={(v) => setForm((f) => ({ ...f, shipping: { ...f.shipping, addressLine1: v } }))}
            />
            <TextInput
              style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
              placeholder="City"
              placeholderTextColor={theme.textSecondary}
              value={form.shipping.city}
              onChangeText={(v) => setForm((f) => ({ ...f, shipping: { ...f.shipping, city: v } }))}
            />
            {stateOptions.length > 0 ? (
              <SelectField
                placeholder="State/Province"
                value={form.shipping.state ?? ''}
                options={stateOptions}
                onChange={(v) => setForm((f) => ({ ...f, shipping: { ...f.shipping, state: v } }))}
              />
            ) : (
              <TextInput
                style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
                placeholder="State/Province"
                placeholderTextColor={theme.textSecondary}
                value={form.shipping.state}
                onChangeText={(v) => setForm((f) => ({ ...f, shipping: { ...f.shipping, state: v } }))}
              />
            )}
            <TextInput
              style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
              placeholder="Postal code"
              placeholderTextColor={theme.textSecondary}
              value={form.shipping.postalCode}
              onChangeText={(v) => setForm((f) => ({ ...f, shipping: { ...f.shipping, postalCode: v } }))}
            />
            <TouchableOpacity style={[styles.saveButton, !canSubmit && styles.saveButtonDisabled]} disabled={!canSubmit || createMutation.isPending} onPress={() => createMutation.mutate()}>
              <ThemedText style={styles.saveButtonText}>{createMutation.isPending ? 'Saving...' : 'Save address'}</ThemedText>
            </TouchableOpacity>
          </View>
        ) : (
          <TouchableOpacity style={[styles.addButton, { borderColor: theme.textSecondary }]} onPress={() => setShowForm(true)}>
            <ThemedText>+ Add address</ThemedText>
          </TouchableOpacity>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { padding: Spacing.three, gap: Spacing.three, paddingBottom: Spacing.six },
  card: { borderWidth: 1, borderRadius: 12, padding: Spacing.three, gap: 4 },
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between' },
  cardActions: { flexDirection: 'row', gap: Spacing.three, marginTop: Spacing.two },
  form: { gap: Spacing.two },
  input: { height: 44, borderRadius: 10, paddingHorizontal: Spacing.three, fontSize: 16 },
  saveButton: { height: 48, borderRadius: 12, backgroundColor: '#208AEF', alignItems: 'center', justifyContent: 'center' },
  saveButtonDisabled: { opacity: 0.5 },
  saveButtonText: { color: '#fff', fontWeight: '700' },
  addButton: { height: 44, borderRadius: 10, borderWidth: 1, borderStyle: 'dashed', alignItems: 'center', justifyContent: 'center' },
});
