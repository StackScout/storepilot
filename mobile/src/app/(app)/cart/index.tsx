import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { ScrollView, StyleSheet, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { EmptyState } from '@/components/empty-state';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useCart } from '@/hooks/use-cart';
import { useCartReconciliation } from '@/hooks/use-cart-reconciliation';
import { useTheme } from '@/hooks/use-theme';
import { formatCurrency, usePlatformConfig } from '@/lib/platform-config';
import type { Href } from 'expo-router';

export default function CartScreen() {
  const theme = useTheme();
  const router = useRouter();
  const platformConfig = usePlatformConfig();
  const { cart, subtotal, isHydrated, updateQuantity, removeItem } = useCart();
  useCartReconciliation();

  const hasUnavailable = cart.items.some((i) => i.isUnavailable);

  if (isHydrated && cart.items.length === 0) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }}>
        <EmptyState
          title="Your cart is empty"
          description="Add products from a store to see them here."
          action={
            <TouchableOpacity style={[styles.browseButton]} onPress={() => router.push('/search' as Href)}>
              <ThemedText style={styles.browseButtonText}>Browse products</ThemedText>
            </TouchableOpacity>
          }
        />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <ScrollView contentContainerStyle={styles.container}>
        {cart.storeName ? (
          <ThemedText type="small" themeColor="textSecondary">
            Items from {cart.storeName}
          </ThemedText>
        ) : null}

        {hasUnavailable ? (
          <ThemedText type="small" themeColor="textSecondary" style={styles.warning}>
            Some items are no longer available and won&apos;t be included in your order. Remove them to continue.
          </ThemedText>
        ) : null}

        {cart.items.map((item) => (
          <View key={item.productId} style={[styles.item, item.isUnavailable && styles.itemUnavailable]}>
            {item.isUnavailable ? (
              <View style={[styles.thumb, { backgroundColor: theme.backgroundElement }]}>
                <Image source={{ uri: item.productImageUrl }} style={styles.thumbImage} contentFit="cover" />
              </View>
            ) : (
              <TouchableOpacity
                style={[styles.thumb, { backgroundColor: theme.backgroundElement }]}
                onPress={() => router.push(`/stores/${cart.storeSlug}/products/${item.productSlug}` as Href)}>
                <Image source={{ uri: item.productImageUrl }} style={styles.thumbImage} contentFit="cover" />
              </TouchableOpacity>
            )}
            <View style={styles.itemInfo}>
              {item.isUnavailable ? (
                <ThemedText type="smallBold" numberOfLines={2}>
                  {item.productName}
                </ThemedText>
              ) : (
                <TouchableOpacity onPress={() => router.push(`/stores/${cart.storeSlug}/products/${item.productSlug}` as Href)}>
                  <ThemedText type="smallBold" numberOfLines={2}>
                    {item.productName}
                  </ThemedText>
                </TouchableOpacity>
              )}
              {item.isUnavailable ? (
                <ThemedText type="small" style={{ color: '#D64545' }}>
                  No longer available
                </ThemedText>
              ) : (
                <ThemedText type="small" themeColor="textSecondary">
                  {formatCurrency(item.unitPrice, platformConfig)}
                </ThemedText>
              )}
              <View style={styles.itemActions}>
                {item.isUnavailable ? (
                  <ThemedText type="small" themeColor="textSecondary">
                    Qty {item.quantity}
                  </ThemedText>
                ) : (
                  <View style={styles.stepper}>
                    <TouchableOpacity
                      style={[styles.stepperButton, { backgroundColor: theme.backgroundElement }]}
                      onPress={() => updateQuantity(item.productId, item.quantity - 1)}>
                      <ThemedText>−</ThemedText>
                    </TouchableOpacity>
                    <ThemedText style={styles.stepperValue}>{item.quantity}</ThemedText>
                    <TouchableOpacity
                      style={[styles.stepperButton, { backgroundColor: theme.backgroundElement }]}
                      disabled={item.trackStock && item.quantity >= item.stockQuantity}
                      onPress={() => updateQuantity(item.productId, item.quantity + 1)}>
                      <ThemedText>+</ThemedText>
                    </TouchableOpacity>
                  </View>
                )}
                <TouchableOpacity onPress={() => removeItem(item.productId)}>
                  <ThemedText type="small" themeColor="textSecondary">
                    Remove
                  </ThemedText>
                </TouchableOpacity>
              </View>
            </View>
          </View>
        ))}

        <View style={[styles.summary, { borderColor: theme.backgroundElement }]}>
          <View style={styles.summaryRow}>
            <ThemedText themeColor="textSecondary">Subtotal</ThemedText>
            <ThemedText>{formatCurrency(subtotal, platformConfig)}</ThemedText>
          </View>
          <View style={styles.summaryRow}>
            <ThemedText themeColor="textSecondary">Shipping (estimate)</ThemedText>
            <ThemedText>{formatCurrency(platformConfig.flatShippingFee, platformConfig)}</ThemedText>
          </View>
          <View style={styles.summaryRow}>
            <ThemedText type="smallBold">Total</ThemedText>
            <ThemedText type="smallBold">{formatCurrency(subtotal + platformConfig.flatShippingFee, platformConfig)}</ThemedText>
          </View>
        </View>

        <TouchableOpacity
          style={[styles.checkoutButton, hasUnavailable && styles.checkoutButtonDisabled]}
          disabled={hasUnavailable}
          onPress={() => router.push('/cart/checkout' as Href)}>
          <ThemedText style={styles.checkoutButtonText}>Proceed to checkout</ThemedText>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { padding: Spacing.three, gap: Spacing.three, paddingBottom: Spacing.six },
  warning: { color: '#B98900' },
  item: { flexDirection: 'row', gap: Spacing.two },
  itemUnavailable: { opacity: 0.5 },
  thumb: { width: 80, height: 80, borderRadius: 10, overflow: 'hidden' },
  thumbImage: { width: '100%', height: '100%' },
  itemInfo: { flex: 1, gap: 4, justifyContent: 'center' },
  itemActions: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 4 },
  stepper: { flexDirection: 'row', alignItems: 'center', gap: Spacing.two },
  stepperButton: { width: 28, height: 28, borderRadius: 6, alignItems: 'center', justifyContent: 'center' },
  stepperValue: { minWidth: 20, textAlign: 'center' },
  summary: { borderTopWidth: 1, paddingTop: Spacing.three, gap: Spacing.two },
  summaryRow: { flexDirection: 'row', justifyContent: 'space-between' },
  checkoutButton: { height: 50, borderRadius: 12, backgroundColor: '#208AEF', alignItems: 'center', justifyContent: 'center' },
  checkoutButtonDisabled: { opacity: 0.5 },
  checkoutButtonText: { color: '#fff', fontWeight: '700' },
  browseButton: { height: 44, paddingHorizontal: Spacing.four, borderRadius: 10, backgroundColor: '#208AEF', alignItems: 'center', justifyContent: 'center' },
  browseButtonText: { color: '#fff', fontWeight: '600' },
});
