import { useMutation, useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useMemo, useState } from 'react';
import { ActivityIndicator, Alert, ScrollView, StyleSheet, TextInput, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import * as WebBrowser from 'expo-web-browser';

import type { DeliveryMethod, PaymentMethod } from '@storepilot/shared-api';

import { createOrder, getStripeCheckoutUrl } from '@/api/buyer-checkout';
import { getPublicStoreSettings } from '@/api/buyer-stores';
import { SelectField } from '@/components/select-field';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useCart } from '@/hooks/use-cart';
import { useCartReconciliation } from '@/hooks/use-cart-reconciliation';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-client';
import { formatCurrency, usePlatformConfig, useStates } from '@/lib/platform-config';
import { useAuthStore } from '@/store/auth-store';

export default function CheckoutScreen() {
  const theme = useTheme();
  const router = useRouter();
  const platformConfig = usePlatformConfig();
  const { cart, subtotal, clearCart } = useCart();
  useCartReconciliation();
  const authEmail = useAuthStore((s) => s.email);
  const authName = useAuthStore((s) => s.name);

  const settingsQuery = useQuery({
    queryKey: ['store', cart.storeId, 'public-settings'],
    queryFn: () => getPublicStoreSettings(cart.storeId!),
    enabled: !!cart.storeId,
  });
  const settings = settingsQuery.data;
  const statesQuery = useStates();
  const stateOptions = (statesQuery.data ?? []).map((s) => s.name);

  const [deliveryMethod, setDeliveryMethod] = useState<DeliveryMethod>('shipping');
  const [fullName, setFullName] = useState(authName ?? '');
  const [phone, setPhone] = useState('');
  const [addressLine1, setAddressLine1] = useState('');
  const [city, setCity] = useState('');
  const [state, setState] = useState('');
  const [postalCode, setPostalCode] = useState('');
  const [email, setEmail] = useState(authEmail ?? '');
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod | null>(null);

  const availableMethods = useMemo((): PaymentMethod[] => {
    if (!settings) return [];
    const methods: PaymentMethod[] = [];
    if (settings.stripeEnabled && settings.stripeChargesEnabled) methods.push('stripe');
    if (settings.codEnabled) methods.push('cod');
    if (settings.bankTransferEnabled) methods.push('bank-transfer');
    return methods;
  }, [settings]);

  const total = subtotal + (deliveryMethod === 'shipping' ? platformConfig.flatShippingFee : 0);

  const orderMutation = useMutation({
    mutationFn: async () => {
      const order = await createOrder({
        storeId: cart.storeId!,
        items: cart.items.filter((i) => !i.isUnavailable).map((i) => ({ productId: i.productId, quantity: i.quantity })),
        shipping: {
          fullName: fullName.trim(),
          phone: phone.trim(),
          addressLine1: deliveryMethod === 'shipping' ? addressLine1.trim() : undefined,
          city: deliveryMethod === 'shipping' ? city.trim() : undefined,
          state: deliveryMethod === 'shipping' ? state.trim() : undefined,
          postalCode: deliveryMethod === 'shipping' ? postalCode.trim() : undefined,
        },
        paymentMethod: paymentMethod!,
        deliveryMethod,
        email: email.trim(),
      });
      if (paymentMethod === 'stripe') {
        const { checkoutUrl } = await getStripeCheckoutUrl(order.id);
        await WebBrowser.openBrowserAsync(checkoutUrl);
      }
      return order;
    },
    onSuccess: (order) => {
      clearCart();
      Alert.alert('Order placed', `Order ${order.orderNumber} has been placed.`, [
        { text: 'OK', onPress: () => router.replace(`/account/orders/${order.id}` as never) },
      ]);
    },
    onError: (e) => Alert.alert('Could not place order', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  if (settingsQuery.isLoading) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: theme.background, alignItems: 'center', justifyContent: 'center' }}>
        <ActivityIndicator />
      </SafeAreaView>
    );
  }

  const needsShippingFields = deliveryMethod === 'shipping';
  const canSubmit =
    !!paymentMethod &&
    !!fullName.trim() &&
    !!phone.trim() &&
    !!email.trim() &&
    (!needsShippingFields || (!!addressLine1.trim() && !!city.trim() && !!state.trim() && !!postalCode.trim()));

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <ScrollView contentContainerStyle={styles.container}>
        <ThemedText type="smallBold" themeColor="textSecondary">
          DELIVERY
        </ThemedText>
        <View style={styles.methodRowContainer}>
          <TouchableOpacity
            style={[styles.methodChip, { borderColor: theme.textSecondary }, deliveryMethod === 'shipping' && { backgroundColor: theme.backgroundElement }]}
            onPress={() => setDeliveryMethod('shipping')}>
            <ThemedText type="small">Ship to address</ThemedText>
          </TouchableOpacity>
          {settings?.pickupEnabled ? (
            <TouchableOpacity
              style={[styles.methodChip, { borderColor: theme.textSecondary }, deliveryMethod === 'pickup' && { backgroundColor: theme.backgroundElement }]}
              onPress={() => setDeliveryMethod('pickup')}>
              <ThemedText type="small">Pickup</ThemedText>
            </TouchableOpacity>
          ) : null}
        </View>

        <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
          CONTACT
        </ThemedText>
        <TextInput style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]} placeholder="Full name" placeholderTextColor={theme.textSecondary} value={fullName} onChangeText={setFullName} />
        <TextInput style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]} placeholder="Phone" placeholderTextColor={theme.textSecondary} keyboardType="phone-pad" value={phone} onChangeText={setPhone} />
        <TextInput style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]} placeholder="Email" placeholderTextColor={theme.textSecondary} autoCapitalize="none" keyboardType="email-address" value={email} onChangeText={setEmail} />

        {needsShippingFields ? (
          <>
            <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
              SHIPPING ADDRESS
            </ThemedText>
            <TextInput style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]} placeholder="Address" placeholderTextColor={theme.textSecondary} value={addressLine1} onChangeText={setAddressLine1} />
            <TextInput style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]} placeholder="City" placeholderTextColor={theme.textSecondary} value={city} onChangeText={setCity} />
            {stateOptions.length > 0 ? (
              <SelectField placeholder="State/Province" value={state} options={stateOptions} onChange={setState} />
            ) : (
              <TextInput style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]} placeholder="State/Province" placeholderTextColor={theme.textSecondary} value={state} onChangeText={setState} />
            )}
            <TextInput style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]} placeholder="Postal code" placeholderTextColor={theme.textSecondary} value={postalCode} onChangeText={setPostalCode} />
          </>
        ) : null}

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
              onPress={() => setPaymentMethod(method)}>
              <ThemedText type="small">{paymentMethodLabel(method)}</ThemedText>
            </TouchableOpacity>
          ))
        )}

        <View style={[styles.summary, { borderColor: theme.backgroundElement }]}>
          <View style={styles.summaryRow}>
            <ThemedText themeColor="textSecondary">Subtotal</ThemedText>
            <ThemedText>{formatCurrency(subtotal, platformConfig)}</ThemedText>
          </View>
          <View style={styles.summaryRow}>
            <ThemedText themeColor="textSecondary">Shipping</ThemedText>
            <ThemedText>{deliveryMethod === 'shipping' ? formatCurrency(platformConfig.flatShippingFee, platformConfig) : formatCurrency(0, platformConfig)}</ThemedText>
          </View>
          <View style={styles.summaryRow}>
            <ThemedText type="smallBold">Total</ThemedText>
            <ThemedText type="smallBold">{formatCurrency(total, platformConfig)}</ThemedText>
          </View>
        </View>

        <TouchableOpacity
          style={[styles.submitButton, !canSubmit && styles.submitButtonDisabled]}
          disabled={!canSubmit || orderMutation.isPending}
          onPress={() => orderMutation.mutate()}>
          <ThemedText style={styles.submitButtonText}>{orderMutation.isPending ? 'Placing order...' : 'Place order'}</ThemedText>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

function paymentMethodLabel(method: PaymentMethod): string {
  switch (method) {
    case 'stripe':
      return 'Pay online (card)';
    case 'cod':
      return 'Cash on delivery';
    case 'bank-transfer':
      return 'Bank transfer';
    default:
      return method;
  }
}

const styles = StyleSheet.create({
  container: { padding: Spacing.three, gap: Spacing.two, paddingBottom: Spacing.six },
  sectionLabel: { marginTop: Spacing.two },
  methodRowContainer: { flexDirection: 'row', gap: Spacing.two },
  methodChip: { borderWidth: 1, borderRadius: 10, paddingHorizontal: Spacing.three, height: 40, alignItems: 'center', justifyContent: 'center' },
  input: { height: 44, borderRadius: 10, paddingHorizontal: Spacing.three, fontSize: 16 },
  methodRow: { borderWidth: 1, borderRadius: 10, padding: Spacing.two },
  summary: { borderTopWidth: 1, paddingTop: Spacing.three, gap: Spacing.two, marginTop: Spacing.two },
  summaryRow: { flexDirection: 'row', justifyContent: 'space-between' },
  submitButton: { height: 50, borderRadius: 12, backgroundColor: '#208AEF', alignItems: 'center', justifyContent: 'center', marginTop: Spacing.two },
  submitButtonDisabled: { opacity: 0.5 },
  submitButtonText: { color: '#fff', fontWeight: '700' },
});
