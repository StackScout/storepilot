import { router } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, StyleSheet, TextInput, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { requestBookingLookupCode, verifyBookingLookupCode } from '@/api/buyer-bookings';
import { requestOrderLookupCode, verifyOrderLookupCode } from '@/api/buyer-orders';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-client';

type Kind = 'order' | 'booking';

export default function TrackScreen() {
  const theme = useTheme();
  const [kind, setKind] = useState<Kind>('order');
  const [number, setNumber] = useState('');
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [codeSent, setCodeSent] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const requestCode = async () => {
    setError(null);
    setSubmitting(true);
    try {
      if (kind === 'order') await requestOrderLookupCode(number.trim(), phone.trim());
      else await requestBookingLookupCode(number.trim(), phone.trim());
      setCodeSent(true);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Something went wrong — please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  const verify = async () => {
    setError(null);
    setSubmitting(true);
    try {
      if (kind === 'order') {
        const order = await verifyOrderLookupCode(number.trim(), phone.trim(), code);
        router.replace(`/account/orders/${order.id}` as never);
      } else {
        const booking = await verifyBookingLookupCode(number.trim(), phone.trim(), code);
        router.replace(`/account/bookings/${booking.id}` as never);
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Invalid or expired code.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }}>
      <View style={styles.container}>
        <View style={styles.kindRow}>
          <TouchableOpacity style={[styles.kindChip, { borderColor: theme.textSecondary }, kind === 'order' && { backgroundColor: theme.backgroundElement }]} onPress={() => setKind('order')}>
            <ThemedText type="small">Order</ThemedText>
          </TouchableOpacity>
          <TouchableOpacity style={[styles.kindChip, { borderColor: theme.textSecondary }, kind === 'booking' && { backgroundColor: theme.backgroundElement }]} onPress={() => setKind('booking')}>
            <ThemedText type="small">Booking</ThemedText>
          </TouchableOpacity>
        </View>

        <TextInput
          style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
          placeholder={kind === 'order' ? 'Order number' : 'Booking number'}
          placeholderTextColor={theme.textSecondary}
          autoCapitalize="characters"
          value={number}
          onChangeText={setNumber}
        />
        <TextInput
          style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
          placeholder="Phone number used at checkout"
          placeholderTextColor={theme.textSecondary}
          keyboardType="phone-pad"
          value={phone}
          onChangeText={setPhone}
        />

        {codeSent ? (
          <TextInput
            style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
            placeholder="6-digit code"
            placeholderTextColor={theme.textSecondary}
            keyboardType="number-pad"
            maxLength={6}
            value={code}
            onChangeText={setCode}
          />
        ) : null}

        {error ? <ThemedText style={styles.error}>{error}</ThemedText> : null}

        <TouchableOpacity
          style={[styles.button, { opacity: submitting ? 0.6 : 1 }]}
          disabled={submitting || !number.trim() || !phone.trim() || (codeSent && code.length !== 6)}
          onPress={codeSent ? verify : requestCode}>
          {submitting ? <ActivityIndicator color="#fff" /> : <ThemedText style={styles.buttonText}>{codeSent ? 'Verify' : 'Send code'}</ThemedText>}
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { padding: Spacing.four, gap: Spacing.three },
  kindRow: { flexDirection: 'row', gap: Spacing.two },
  kindChip: { flex: 1, height: 40, borderWidth: 1, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
  input: { height: 48, borderRadius: 10, paddingHorizontal: Spacing.three, fontSize: 16 },
  error: { color: '#D64545', textAlign: 'center' },
  button: { height: 48, borderRadius: 10, backgroundColor: '#208AEF', alignItems: 'center', justifyContent: 'center' },
  buttonText: { color: '#fff', fontSize: 16, fontWeight: '600' },
});
