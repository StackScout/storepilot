import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { ActivityIndicator, Alert, ScrollView, StyleSheet, TextInput, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { getMyStore } from '@/api/stores';
import { inviteStaff, listPendingInvites, listStaff, removeStaff, revokeInvite } from '@/api/store-staff';
import type { StoreStaffInviteResponse, StoreStaffMemberResponse } from '@/api/types';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-client';
import { formatDate } from '@/lib/format';

function StaffRow({ item, storeId }: { item: StoreStaffMemberResponse; storeId: string }) {
  const theme = useTheme();
  const queryClient = useQueryClient();

  const removeMutation = useMutation({
    mutationFn: () => removeStaff(storeId, item.id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['store', storeId, 'staff'] }),
    onError: (e) => Alert.alert('Could not remove staff member', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  const confirmRemove = () => {
    Alert.alert('Remove staff member', `Remove ${item.name} from this store? They'll lose access immediately.`, [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Remove', style: 'destructive', onPress: () => removeMutation.mutate() },
    ]);
  };

  return (
    <View style={[styles.row, { backgroundColor: theme.backgroundElement }]}>
      <ThemedText type="smallBold">{item.name}</ThemedText>
      <ThemedText type="small" themeColor="textSecondary">
        {item.email} · joined {formatDate(item.joinedAt)}
      </ThemedText>
      <TouchableOpacity onPress={confirmRemove} style={styles.actionLink}>
        <ThemedText style={styles.destructiveText}>Remove</ThemedText>
      </TouchableOpacity>
    </View>
  );
}

function InviteRow({ item, storeId }: { item: StoreStaffInviteResponse; storeId: string }) {
  const theme = useTheme();
  const queryClient = useQueryClient();

  const revokeMutation = useMutation({
    mutationFn: () => revokeInvite(storeId, item.id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['store', storeId, 'staff-invites'] }),
    onError: (e) => Alert.alert('Could not revoke invite', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  const confirmRevoke = () => {
    Alert.alert('Revoke invite', `Revoke the invite sent to ${item.email}?`, [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Revoke', style: 'destructive', onPress: () => revokeMutation.mutate() },
    ]);
  };

  return (
    <View style={[styles.row, { backgroundColor: theme.backgroundElement }]}>
      <ThemedText type="smallBold">{item.name}</ThemedText>
      <ThemedText type="small" themeColor="textSecondary">
        {item.email} · expires {formatDate(item.expiresAt)}
      </ThemedText>
      <TouchableOpacity onPress={confirmRevoke} style={styles.actionLink}>
        <ThemedText style={styles.destructiveText}>Revoke</ThemedText>
      </TouchableOpacity>
    </View>
  );
}

export default function StaffScreen() {
  const theme = useTheme();
  const queryClient = useQueryClient();
  const storeQuery = useQuery({ queryKey: ['me', 'store'], queryFn: getMyStore });
  const storeId = storeQuery.data?.id;

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');

  const staffQuery = useQuery({
    queryKey: ['store', storeId, 'staff'],
    queryFn: () => listStaff(storeId!),
    enabled: !!storeId,
  });

  const invitesQuery = useQuery({
    queryKey: ['store', storeId, 'staff-invites'],
    queryFn: () => listPendingInvites(storeId!),
    enabled: !!storeId,
  });

  const inviteMutation = useMutation({
    mutationFn: () => inviteStaff(storeId!, { name: name.trim(), email: email.trim() }),
    onSuccess: () => {
      setName('');
      setEmail('');
      queryClient.invalidateQueries({ queryKey: ['store', storeId, 'staff-invites'] });
    },
    onError: (e) => Alert.alert('Could not send invite', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  const canInvite = name.trim().length > 0 && email.trim().length > 0;

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <ScrollView contentContainerStyle={styles.container}>
        <ThemedText type="smallBold">Invite a staff member</ThemedText>
        <ThemedText type="small" themeColor="textSecondary">
          They'll get full access to orders, products, and bookings — but never revenue, payouts, or store settings.
        </ThemedText>

        <TextInput
          style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
          placeholder="Full name"
          placeholderTextColor={theme.textSecondary}
          value={name}
          onChangeText={setName}
        />
        <TextInput
          style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
          placeholder="Email address"
          placeholderTextColor={theme.textSecondary}
          autoCapitalize="none"
          keyboardType="email-address"
          value={email}
          onChangeText={setEmail}
        />
        <TouchableOpacity
          style={[styles.submit, (!canInvite || inviteMutation.isPending) && styles.submitDisabled]}
          disabled={!canInvite || inviteMutation.isPending}
          onPress={() => inviteMutation.mutate()}>
          {inviteMutation.isPending ? <ActivityIndicator color="#fff" /> : <ThemedText style={styles.submitText}>Send invite</ThemedText>}
        </TouchableOpacity>

        <View style={styles.section}>
          <ThemedText type="smallBold">Staff</ThemedText>
          {(staffQuery.data ?? []).length === 0 && !staffQuery.isLoading ? (
            <ThemedText type="small" themeColor="textSecondary">
              No staff members yet.
            </ThemedText>
          ) : null}
          {(staffQuery.data ?? []).map((item) => (
            <StaffRow key={item.id} item={item} storeId={storeId!} />
          ))}
        </View>

        <View style={styles.section}>
          <ThemedText type="smallBold">Pending invites</ThemedText>
          {(invitesQuery.data ?? []).length === 0 && !invitesQuery.isLoading ? (
            <ThemedText type="small" themeColor="textSecondary">
              No pending invites.
            </ThemedText>
          ) : null}
          {(invitesQuery.data ?? []).map((item) => (
            <InviteRow key={item.id} item={item} storeId={storeId!} />
          ))}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { padding: Spacing.three, gap: Spacing.two, paddingBottom: Spacing.six },
  input: { height: 48, borderRadius: 10, paddingHorizontal: Spacing.three, fontSize: 16 },
  submit: { height: 50, borderRadius: 12, backgroundColor: '#208AEF', alignItems: 'center', justifyContent: 'center' },
  submitDisabled: { opacity: 0.6 },
  submitText: { color: '#fff', fontWeight: '700' },
  section: { marginTop: Spacing.four, gap: Spacing.two },
  row: { borderRadius: 14, padding: Spacing.three, gap: Spacing.half },
  actionLink: { marginTop: Spacing.half, alignSelf: 'flex-start' },
  destructiveText: { color: '#D64545', fontWeight: '600' },
});
