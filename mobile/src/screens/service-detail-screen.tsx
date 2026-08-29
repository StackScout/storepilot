import { useMutation, useQuery } from '@tanstack/react-query';
import { Image } from 'expo-image';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { useMemo, useState } from 'react';
import { ActivityIndicator, Alert, ScrollView, StyleSheet, TextInput, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import type { PaymentMethod } from '@storepilot/shared-api';

import { createBooking } from '@/api/buyer-checkout';
import { getSlots, listServicesByStore } from '@/api/buyer-services';
import { getPublicStoreSettings, getStoreBySlug } from '@/api/buyer-stores';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-client';
import { formatCurrency, usePlatformConfig } from '@/lib/platform-config';
import { formatDuration } from '@/lib/format';
import { useAuthStore } from '@/store/auth-store';

/** PayHere requires an HTML-form POST, not a simple redirect URL — not yet supported natively on mobile (see the pushed web checkout flow for that). Only offering payment methods with a clean redirect/no-redirect path here. */
export default function ServiceScreen() {
  const theme = useTheme();
  const router = useRouter();
  const platformConfig = usePlatformConfig();
  const { slug, serviceSlug } = useLocalSearchParams<{ slug: string; serviceSlug: string }>();
  const authEmail = useAuthStore((s) => s.email);
  const authName = useAuthStore((s) => s.name);

  const storeQuery = useQuery({ queryKey: ['store', slug], queryFn: () => getStoreBySlug(slug!), enabled: !!slug });
  const store = storeQuery.data;

  const servicesQuery = useQuery({ queryKey: ['store', store?.id, 'services'], queryFn: () => listServicesByStore(store!.id), enabled: !!store });
  const service = servicesQuery.data?.find((s) => s.slug === serviceSlug);

  const settingsQuery = useQuery({ queryKey: ['store', store?.id, 'public-settings'], queryFn: () => getPublicStoreSettings(store!.id), enabled: !!store });

  const slotsQuery = useQuery({
    queryKey: ['service', service?.id, 'slots'],
    queryFn: () => getSlots(store!.id, service!.id),
    enabled: !!store && !!service,
  });

  const [selectedSlot, setSelectedSlot] = useState<string | null>(null);
  const [buyerName, setBuyerName] = useState(authName ?? '');
  const [buyerPhone, setBuyerPhone] = useState('');
  const [buyerEmail, setBuyerEmail] = useState(authEmail ?? '');
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod | null>(null);
  const [repeatWeekly, setRepeatWeekly] = useState(false);
  const [occurrenceCount, setOccurrenceCount] = useState(4);

  /** Only offered for cod/bank-transfer — mirrors the web app's ServiceBookingForm exactly (a Stripe/PayHere charge can't cleanly represent "N future sessions" as one payment). */
  const canRepeatWeekly = paymentMethod === 'cod' || paymentMethod === 'bank-transfer';

  const availableMethods = useMemo((): PaymentMethod[] => {
    const settings = settingsQuery.data;
    if (!settings) return [];
    const methods: PaymentMethod[] = [];
    if (settings.stripeEnabled && settings.stripeChargesEnabled) methods.push('stripe');
    if (settings.codEnabled) methods.push('cod');
    if (settings.bankTransferEnabled) methods.push('bank-transfer');
    return methods;
  }, [settingsQuery.data]);

  const bookMutation = useMutation({
    mutationFn: () =>
      createBooking({
        storeId: store!.id,
        serviceId: service!.id,
        scheduledStart: selectedSlot!,
        paymentMethod: paymentMethod!,
        buyerName: buyerName.trim(),
        buyerPhone: buyerPhone.trim(),
        buyerEmail: buyerEmail.trim(),
        occurrenceCount: canRepeatWeekly && repeatWeekly ? occurrenceCount : undefined,
      }),
    onSuccess: (booking) => {
      Alert.alert('Booking requested', `Your booking ${booking.bookingNumber} has been requested.`, [
        { text: 'OK', onPress: () => router.replace(`/account/bookings/${booking.id}` as never) },
      ]);
    },
    onError: (e) => Alert.alert('Could not book', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  if (storeQuery.isLoading || servicesQuery.isLoading || !service || !store) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: theme.background, alignItems: 'center', justifyContent: 'center' }}>
        <ActivityIndicator />
      </SafeAreaView>
    );
  }

  const canSubmit = !!selectedSlot && !!paymentMethod && !!buyerName.trim() && !!buyerPhone.trim() && !!buyerEmail.trim();

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <Stack.Screen options={{ title: service.name }} />
      <ScrollView contentContainerStyle={styles.container}>
        {service.images[0] ? (
          <Image source={{ uri: service.images[0].url }} style={[styles.image, { backgroundColor: theme.backgroundElement }]} contentFit="cover" />
        ) : null}
        <ThemedText type="small" themeColor="textSecondary">
          {store.name}
        </ThemedText>
        <ThemedText type="title" style={styles.name}>
          {service.name}
        </ThemedText>
        <ThemedText type="smallBold">
          {formatCurrency(service.price, platformConfig)} · {formatDuration(service.durationMinutes)}
        </ThemedText>
        <ThemedText style={styles.description}>{service.description}</ThemedText>

        <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
          PICK A TIME
        </ThemedText>
        {slotsQuery.isLoading ? (
          <ActivityIndicator style={styles.slotsLoading} />
        ) : (slotsQuery.data ?? []).every((d) => d.slots.length === 0) ? (
          <ThemedText type="small" themeColor="textSecondary">
            No available slots in the next 30 days.
          </ThemedText>
        ) : (
          (slotsQuery.data ?? [])
            .filter((day) => day.slots.length > 0)
            .map((day) => (
              <View key={day.date} style={styles.day}>
                <ThemedText type="small" themeColor="textSecondary">
                  {new Date(day.date).toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' })}
                </ThemedText>
                <View style={styles.slotRow}>
                  {day.slots.map((slot) => {
                    const selected = selectedSlot === slot.start;
                    return (
                      <TouchableOpacity
                        key={slot.start}
                        style={[styles.slot, { borderColor: theme.textSecondary }, selected && { backgroundColor: '#208AEF', borderColor: '#208AEF' }]}
                        onPress={() => setSelectedSlot(slot.start)}>
                        <ThemedText type="small" style={selected ? styles.slotTextSelected : undefined}>
                          {new Date(slot.start).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })}
                        </ThemedText>
                      </TouchableOpacity>
                    );
                  })}
                </View>
              </View>
            ))
        )}

        {selectedSlot ? (
          <>
            <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
              YOUR DETAILS
            </ThemedText>
            <TextInput
              style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
              placeholder="Full name"
              placeholderTextColor={theme.textSecondary}
              value={buyerName}
              onChangeText={setBuyerName}
            />
            <TextInput
              style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
              placeholder="Phone"
              placeholderTextColor={theme.textSecondary}
              keyboardType="phone-pad"
              value={buyerPhone}
              onChangeText={setBuyerPhone}
            />
            <TextInput
              style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
              placeholder="Email"
              placeholderTextColor={theme.textSecondary}
              autoCapitalize="none"
              keyboardType="email-address"
              value={buyerEmail}
              onChangeText={setBuyerEmail}
            />

            <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
              PAYMENT METHOD
            </ThemedText>
            {availableMethods.length === 0 ? (
              <ThemedText type="small" themeColor="textSecondary">
                This store hasn&apos;t set up any payment methods yet.
              </ThemedText>
            ) : (
              availableMethods.map((method) => (
                <TouchableOpacity
                  key={method}
                  style={[styles.methodRow, { borderColor: theme.textSecondary }, paymentMethod === method && { backgroundColor: theme.backgroundElement }]}
                  onPress={() => {
                    setPaymentMethod(method);
                    if (method !== 'cod' && method !== 'bank-transfer') setRepeatWeekly(false);
                  }}>
                  <ThemedText type="small">{paymentMethodLabel(method)}</ThemedText>
                </TouchableOpacity>
              ))
            )}

            {canRepeatWeekly ? (
              <View style={[styles.repeatCard, { borderColor: theme.textSecondary }]}>
                <TouchableOpacity style={styles.repeatToggle} onPress={() => setRepeatWeekly((v) => !v)}>
                  <View style={[styles.checkbox, { borderColor: theme.textSecondary }, repeatWeekly && styles.checkboxChecked]}>
                    {repeatWeekly ? <ThemedText style={styles.checkboxMark}>✓</ThemedText> : null}
                  </View>
                  <View style={styles.repeatInfo}>
                    <ThemedText type="smallBold">Repeat weekly</ThemedText>
                    <ThemedText type="small" themeColor="textSecondary">
                      Book this same day and time every week, for a set number of sessions
                    </ThemedText>
                  </View>
                </TouchableOpacity>
                {repeatWeekly ? (
                  <>
                    <ThemedText type="small" themeColor="textSecondary" style={styles.repeatLabel}>
                      Number of sessions
                    </ThemedText>
                    <View style={styles.occurrenceRow}>
                      {[2, 3, 4, 5, 6, 8, 10, 12].map((n) => (
                        <TouchableOpacity
                          key={n}
                          style={[styles.occurrenceChip, { borderColor: theme.textSecondary }, occurrenceCount === n && { backgroundColor: '#208AEF', borderColor: '#208AEF' }]}
                          onPress={() => setOccurrenceCount(n)}>
                          <ThemedText type="small" style={occurrenceCount === n ? styles.occurrenceChipTextSelected : undefined}>
                            {n}
                          </ThemedText>
                        </TouchableOpacity>
                      ))}
                    </View>
                  </>
                ) : null}
              </View>
            ) : null}

            <TouchableOpacity
              style={[styles.submitButton, !canSubmit && styles.submitButtonDisabled]}
              disabled={!canSubmit || bookMutation.isPending}
              onPress={() => bookMutation.mutate()}>
              <ThemedText style={styles.submitButtonText}>
                {bookMutation.isPending ? 'Booking...' : canRepeatWeekly && repeatWeekly ? `Request ${occurrenceCount} bookings` : 'Request booking'}
              </ThemedText>
            </TouchableOpacity>
          </>
        ) : null}
      </ScrollView>
    </SafeAreaView>
  );
}

function paymentMethodLabel(method: PaymentMethod): string {
  switch (method) {
    case 'stripe':
      return 'Pay online (card)';
    case 'cod':
      return 'Pay at venue';
    case 'bank-transfer':
      return 'Bank transfer';
    default:
      return method;
  }
}

const styles = StyleSheet.create({
  container: { padding: Spacing.three, gap: Spacing.two, paddingBottom: Spacing.six },
  image: { width: '100%', aspectRatio: 16 / 9, borderRadius: 16 },
  name: { fontSize: 22, lineHeight: 28 },
  description: { marginTop: Spacing.two },
  sectionLabel: { marginTop: Spacing.three },
  slotsLoading: { marginTop: Spacing.two },
  day: { gap: Spacing.half },
  slotRow: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.two },
  slot: { borderWidth: 1, borderRadius: 8, paddingHorizontal: Spacing.two, height: 36, alignItems: 'center', justifyContent: 'center' },
  slotTextSelected: { color: '#fff' },
  input: { height: 44, borderRadius: 10, paddingHorizontal: Spacing.three, fontSize: 16 },
  methodRow: { borderWidth: 1, borderRadius: 10, padding: Spacing.two },
  repeatCard: { borderWidth: 1, borderRadius: 10, padding: Spacing.two, gap: Spacing.two },
  repeatToggle: { flexDirection: 'row', gap: Spacing.two },
  checkbox: { width: 20, height: 20, borderRadius: 4, borderWidth: 1, alignItems: 'center', justifyContent: 'center', marginTop: 2 },
  checkboxChecked: { backgroundColor: '#208AEF', borderColor: '#208AEF' },
  checkboxMark: { color: '#fff', fontSize: 12, fontWeight: '700' },
  repeatInfo: { flex: 1, gap: 2 },
  repeatLabel: { marginTop: Spacing.half },
  occurrenceRow: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.two },
  occurrenceChip: { width: 36, height: 36, borderRadius: 18, borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
  occurrenceChipTextSelected: { color: '#fff' },
  submitButton: { height: 50, borderRadius: 12, backgroundColor: '#208AEF', alignItems: 'center', justifyContent: 'center', marginTop: Spacing.two },
  submitButtonDisabled: { opacity: 0.5 },
  submitButtonText: { color: '#fff', fontWeight: '700' },
});
