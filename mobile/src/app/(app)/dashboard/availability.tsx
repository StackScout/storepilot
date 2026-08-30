import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Alert, ScrollView, StyleSheet, Switch, TextInput, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { createException, deleteException, getAvailability, upsertWeeklyRules } from '@/api/availability';
import { getMyStore } from '@/api/stores';
import type { AvailabilityExceptionInput, WeeklyAvailabilityRule } from '@/api/types';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-client';

const DAY_LABELS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

function defaultRules(): WeeklyAvailabilityRule[] {
  return DAY_LABELS.map((_, i) => ({
    dayOfWeek: i + 1,
    isOpen: i < 5,
    openTime: i < 5 ? '09:00' : undefined,
    closeTime: i < 5 ? '17:00' : undefined,
  }));
}

const EMPTY_EXCEPTION: AvailabilityExceptionInput = { date: '', isOpen: false };

export default function AvailabilityScreen() {
  const theme = useTheme();
  const queryClient = useQueryClient();

  const storeQuery = useQuery({ queryKey: ['me', 'store'], queryFn: getMyStore });
  const storeId = storeQuery.data?.id;

  const availabilityQuery = useQuery({
    queryKey: ['store', storeId, 'availability'],
    queryFn: () => getAvailability(storeId!),
    enabled: !!storeId,
  });

  const [rules, setRules] = useState<WeeklyAvailabilityRule[]>(defaultRules());
  const [leadTimeMinutes, setLeadTimeMinutes] = useState('120');

  useEffect(() => {
    if (!availabilityQuery.data) return;
    if (availabilityQuery.data.weeklyRules.length === 7) {
      setRules([...availabilityQuery.data.weeklyRules].sort((a, b) => a.dayOfWeek - b.dayOfWeek));
    }
    setLeadTimeMinutes(String(availabilityQuery.data.leadTimeMinutes));
  }, [availabilityQuery.data]);

  const updateRule = (dayOfWeek: number, patch: Partial<WeeklyAvailabilityRule>) => {
    setRules((prev) => prev.map((r) => (r.dayOfWeek === dayOfWeek ? { ...r, ...patch } : r)));
  };

  const weeklyMutation = useMutation({
    mutationFn: () => {
      for (const rule of rules) {
        if (rule.isOpen && (!rule.openTime || !rule.closeTime || rule.openTime >= rule.closeTime)) {
          throw new Error(`${DAY_LABELS[rule.dayOfWeek - 1]} needs a valid opening time before its closing time`);
        }
      }
      return upsertWeeklyRules(storeId!, { rules, leadTimeMinutes: parseInt(leadTimeMinutes || '0', 10) });
    },
    onSuccess: (data) => {
      queryClient.setQueryData(['store', storeId, 'availability'], data);
      Alert.alert('Saved', 'Weekly hours saved.');
    },
    onError: (e) => Alert.alert('Could not save', e instanceof Error ? e.message : "Couldn't save weekly hours"),
  });

  const [newException, setNewException] = useState<AvailabilityExceptionInput>(EMPTY_EXCEPTION);

  const createExceptionMutation = useMutation({
    mutationFn: (input: AvailabilityExceptionInput) => createException(storeId!, input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['store', storeId, 'availability'] });
      setNewException(EMPTY_EXCEPTION);
    },
    onError: (e) => Alert.alert('Could not add exception', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  const deleteExceptionMutation = useMutation({
    mutationFn: (exceptionId: string) => deleteException(storeId!, exceptionId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['store', storeId, 'availability'] }),
    onError: () => Alert.alert('Could not remove exception', 'Please try again.'),
  });

  const canAddException =
    !!newException.date &&
    (!newException.isOpen || (!!newException.openTime && !!newException.closeTime)) &&
    !createExceptionMutation.isPending;

  if (storeQuery.isLoading || availabilityQuery.isLoading) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: theme.background, alignItems: 'center', justifyContent: 'center' }}>
        <ActivityIndicator />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <ScrollView contentContainerStyle={styles.container}>
        <ThemedText type="small" themeColor="textSecondary">
          Set your weekly open hours and any one-off closures or special openings — buyers can only book slots inside these windows.
        </ThemedText>

        <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
          WEEKLY HOURS
        </ThemedText>
        {rules.map((rule) => (
          <View key={rule.dayOfWeek} style={[styles.dayRow, { borderColor: theme.backgroundElement }]}>
            <View style={styles.dayHeader}>
              <ThemedText type="smallBold">{DAY_LABELS[rule.dayOfWeek - 1]}</ThemedText>
              <Switch value={rule.isOpen} onValueChange={(v) => updateRule(rule.dayOfWeek, { isOpen: v })} />
            </View>
            {rule.isOpen ? (
              <View style={styles.timeRow}>
                <TextInput
                  style={[styles.timeInput, { color: theme.text, backgroundColor: theme.backgroundElement }]}
                  placeholder="09:00"
                  placeholderTextColor={theme.textSecondary}
                  value={rule.openTime ?? ''}
                  onChangeText={(v) => updateRule(rule.dayOfWeek, { openTime: v })}
                />
                <ThemedText themeColor="textSecondary">to</ThemedText>
                <TextInput
                  style={[styles.timeInput, { color: theme.text, backgroundColor: theme.backgroundElement }]}
                  placeholder="17:00"
                  placeholderTextColor={theme.textSecondary}
                  value={rule.closeTime ?? ''}
                  onChangeText={(v) => updateRule(rule.dayOfWeek, { closeTime: v })}
                />
              </View>
            ) : (
              <ThemedText type="small" themeColor="textSecondary">
                Closed
              </ThemedText>
            )}
          </View>
        ))}

        <ThemedText type="small" themeColor="textSecondary" style={styles.fieldLabel}>
          Lead time (minutes)
        </ThemedText>
        <TextInput
          style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
          keyboardType="number-pad"
          value={leadTimeMinutes}
          onChangeText={setLeadTimeMinutes}
        />
        <ThemedText type="small" themeColor="textSecondary">
          How much notice you need before a booking — also the cutoff for buyers cancelling close to the start time.
        </ThemedText>

        <TouchableOpacity style={[styles.submit, weeklyMutation.isPending && styles.submitDisabled]} onPress={() => weeklyMutation.mutate()} disabled={weeklyMutation.isPending}>
          {weeklyMutation.isPending ? <ActivityIndicator color="#fff" /> : <ThemedText style={styles.submitText}>Save weekly hours</ThemedText>}
        </TouchableOpacity>

        <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
          EXCEPTIONS
        </ThemedText>
        <ThemedText type="small" themeColor="textSecondary">
          Holidays, one-off closures, or a special opening on a normally-closed day.
        </ThemedText>

        {(availabilityQuery.data?.exceptions ?? []).length === 0 ? (
          <ThemedText type="small" themeColor="textSecondary" style={styles.empty}>
            No exceptions yet.
          </ThemedText>
        ) : (
          availabilityQuery.data!.exceptions.map((exception) => (
            <TouchableOpacity
              key={exception.id}
              style={[styles.exceptionRow, { borderColor: theme.backgroundElement }]}
              onLongPress={() =>
                Alert.alert('Remove exception?', undefined, [
                  { text: 'Cancel', style: 'cancel' },
                  { text: 'Remove', style: 'destructive', onPress: () => deleteExceptionMutation.mutate(exception.id) },
                ])
              }>
              <View>
                <ThemedText type="smallBold">{exception.date}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {exception.isOpen ? `Open ${exception.openTime}–${exception.closeTime}` : 'Closed'}
                  {exception.note ? ` · ${exception.note}` : ''}
                </ThemedText>
              </View>
              <ThemedText type="small" themeColor="textSecondary">
                Hold to remove
              </ThemedText>
            </TouchableOpacity>
          ))
        )}

        <ThemedText type="small" themeColor="textSecondary" style={styles.fieldLabel}>
          Date (YYYY-MM-DD)
        </ThemedText>
        <TextInput
          style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
          placeholder="2026-12-25"
          placeholderTextColor={theme.textSecondary}
          value={newException.date}
          onChangeText={(v) => setNewException((prev) => ({ ...prev, date: v }))}
        />
        <View style={styles.switchRow}>
          <ThemedText>Special opening (instead of a closure)</ThemedText>
          <Switch value={newException.isOpen} onValueChange={(v) => setNewException((prev) => ({ ...prev, isOpen: v }))} />
        </View>
        {newException.isOpen ? (
          <View style={styles.timeRow}>
            <TextInput
              style={[styles.timeInput, { color: theme.text, backgroundColor: theme.backgroundElement }]}
              placeholder="Open 09:00"
              placeholderTextColor={theme.textSecondary}
              value={newException.openTime ?? ''}
              onChangeText={(v) => setNewException((prev) => ({ ...prev, openTime: v }))}
            />
            <TextInput
              style={[styles.timeInput, { color: theme.text, backgroundColor: theme.backgroundElement }]}
              placeholder="Close 17:00"
              placeholderTextColor={theme.textSecondary}
              value={newException.closeTime ?? ''}
              onChangeText={(v) => setNewException((prev) => ({ ...prev, closeTime: v }))}
            />
          </View>
        ) : null}
        <TextInput
          style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
          placeholder="Note (optional) — e.g. Closed for public holiday"
          placeholderTextColor={theme.textSecondary}
          value={newException.note ?? ''}
          onChangeText={(v) => setNewException((prev) => ({ ...prev, note: v }))}
        />
        <TouchableOpacity
          style={[styles.submit, styles.addExceptionButton, !canAddException && styles.submitDisabled]}
          onPress={() => createExceptionMutation.mutate(newException)}
          disabled={!canAddException}>
          {createExceptionMutation.isPending ? <ActivityIndicator /> : <ThemedText>+ Add exception</ThemedText>}
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { padding: Spacing.three, gap: Spacing.two, paddingBottom: Spacing.six },
  sectionLabel: { marginTop: Spacing.three },
  fieldLabel: { marginTop: Spacing.two },
  dayRow: { borderBottomWidth: 1, paddingVertical: Spacing.two, gap: Spacing.one },
  dayHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  timeRow: { flexDirection: 'row', alignItems: 'center', gap: Spacing.two },
  timeInput: { flex: 1, height: 44, borderRadius: 10, paddingHorizontal: Spacing.three, fontSize: 16 },
  input: { height: 44, borderRadius: 10, paddingHorizontal: Spacing.three, fontSize: 16 },
  switchRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: Spacing.one },
  exceptionRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', borderBottomWidth: 1, paddingVertical: Spacing.two },
  empty: { paddingVertical: Spacing.two },
  submit: { height: 50, borderRadius: 12, backgroundColor: '#208AEF', alignItems: 'center', justifyContent: 'center', marginTop: Spacing.two },
  addExceptionButton: { backgroundColor: 'transparent', borderWidth: 1, borderColor: '#208AEF' },
  submitDisabled: { opacity: 0.5 },
  submitText: { color: '#fff', fontWeight: '700' },
});
