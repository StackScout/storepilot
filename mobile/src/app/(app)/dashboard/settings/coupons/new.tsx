import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Alert, ScrollView, StyleSheet, Switch, TextInput, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { createCoupon } from '@/api/coupons';
import { getMyStore } from '@/api/stores';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-client';

export default function NewCouponScreen() {
  const theme = useTheme();
  const router = useRouter();
  const queryClient = useQueryClient();
  const storeQuery = useQuery({ queryKey: ['me', 'store'], queryFn: getMyStore });
  const storeId = storeQuery.data?.id;

  const [code, setCode] = useState('');
  const [discountType, setDiscountType] = useState<'percent' | 'fixed'>('percent');
  const [discountValue, setDiscountValue] = useState('');
  const [minSubtotal, setMinSubtotal] = useState('0');
  const [maxUses, setMaxUses] = useState('');
  const [appliesToOrders, setAppliesToOrders] = useState(true);
  const [appliesToBookings, setAppliesToBookings] = useState(true);

  const createMutation = useMutation({
    mutationFn: () => {
      const value = discountType === 'percent' ? Math.min(100, Math.max(1, parseInt(discountValue || '0', 10))) : Math.round(parseFloat(discountValue || '0') * 100);
      return createCoupon(storeId!, {
        code: code.trim(),
        discountType,
        discountValue: value,
        appliesToOrders,
        appliesToBookings,
        maxUses: maxUses ? parseInt(maxUses, 10) : undefined,
        minSubtotal: Math.round(parseFloat(minSubtotal || '0') * 100),
        expiresAt: undefined,
        active: true,
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['store', storeId, 'coupons'] });
      router.back();
    },
    onError: (e) => Alert.alert('Could not create coupon', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  const canSubmit = code.trim().length > 0 && parseFloat(discountValue || '0') > 0;

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <ScrollView contentContainerStyle={styles.container}>
        <TextInput
          style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
          placeholder="Coupon code"
          placeholderTextColor={theme.textSecondary}
          autoCapitalize="characters"
          value={code}
          onChangeText={setCode}
        />

        <View style={styles.typeRow}>
          <TouchableOpacity
            style={[styles.typeChip, { backgroundColor: discountType === 'percent' ? '#208AEF' : theme.backgroundElement }]}
            onPress={() => setDiscountType('percent')}>
            <ThemedText style={discountType === 'percent' ? styles.typeChipTextActive : undefined} themeColor={discountType === 'percent' ? undefined : 'textSecondary'}>
              Percent off
            </ThemedText>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.typeChip, { backgroundColor: discountType === 'fixed' ? '#208AEF' : theme.backgroundElement }]}
            onPress={() => setDiscountType('fixed')}>
            <ThemedText style={discountType === 'fixed' ? styles.typeChipTextActive : undefined} themeColor={discountType === 'fixed' ? undefined : 'textSecondary'}>
              Fixed amount off
            </ThemedText>
          </TouchableOpacity>
        </View>

        <TextInput
          style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
          placeholder={discountType === 'percent' ? 'Percent off (1-100)' : 'Amount off'}
          placeholderTextColor={theme.textSecondary}
          keyboardType="decimal-pad"
          value={discountValue}
          onChangeText={setDiscountValue}
        />
        <TextInput
          style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
          placeholder="Minimum order subtotal (optional)"
          placeholderTextColor={theme.textSecondary}
          keyboardType="decimal-pad"
          value={minSubtotal}
          onChangeText={setMinSubtotal}
        />
        <TextInput
          style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
          placeholder="Max uses (optional, blank = unlimited)"
          placeholderTextColor={theme.textSecondary}
          keyboardType="number-pad"
          value={maxUses}
          onChangeText={setMaxUses}
        />

        <View style={styles.switchRow}>
          <ThemedText>Applies to orders</ThemedText>
          <Switch value={appliesToOrders} onValueChange={setAppliesToOrders} />
        </View>
        <View style={styles.switchRow}>
          <ThemedText>Applies to bookings</ThemedText>
          <Switch value={appliesToBookings} onValueChange={setAppliesToBookings} />
        </View>

        <TouchableOpacity
          style={[styles.submit, (!canSubmit || createMutation.isPending) && styles.submitDisabled]}
          disabled={!canSubmit || createMutation.isPending}
          onPress={() => createMutation.mutate()}>
          {createMutation.isPending ? <ActivityIndicator color="#fff" /> : <ThemedText style={styles.submitText}>Save coupon</ThemedText>}
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { padding: Spacing.three, gap: Spacing.two, paddingBottom: Spacing.six },
  input: { height: 48, borderRadius: 10, paddingHorizontal: Spacing.three, fontSize: 16 },
  typeRow: { flexDirection: 'row', gap: Spacing.two },
  typeChip: { flex: 1, height: 44, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
  typeChipTextActive: { color: '#fff', fontWeight: '600' },
  switchRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: Spacing.one },
  submit: { height: 50, borderRadius: 12, backgroundColor: '#208AEF', alignItems: 'center', justifyContent: 'center', marginTop: Spacing.three },
  submitDisabled: { opacity: 0.6 },
  submitText: { color: '#fff', fontWeight: '700' },
});
