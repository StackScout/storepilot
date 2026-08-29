import { useQuery } from '@tanstack/react-query';
import { Image } from 'expo-image';
import { Stack, useLocalSearchParams, useRouter, type Href } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Alert, ScrollView, StyleSheet, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { getStoreBySlug } from '@/api/buyer-stores';
import { listProductsByStore } from '@/api/buyer-products';
import { createProductReview, listProductReviews } from '@/api/buyer-reviews';
import { ReviewsSection } from '@/components/reviews-section';
import { ThemedText } from '@/components/themed-text';
import { WishlistButton } from '@/components/wishlist-button';
import { Spacing } from '@/constants/theme';
import { useCart } from '@/hooks/use-cart';
import { useStoreHrefs } from '@/hooks/use-store-href';
import { useTheme } from '@/hooks/use-theme';
import { formatCurrency, usePlatformConfig } from '@/lib/platform-config';

export default function ProductScreen() {
  const theme = useTheme();
  const router = useRouter();
  const hrefs = useStoreHrefs();
  const platformConfig = usePlatformConfig();
  const { slug, productSlug } = useLocalSearchParams<{ slug: string; productSlug: string }>();
  const { cart, addItem, replaceCartWithItem } = useCart();
  const [quantity, setQuantity] = useState(1);

  const storeQuery = useQuery({ queryKey: ['store', slug], queryFn: () => getStoreBySlug(slug!), enabled: !!slug });
  const store = storeQuery.data;

  const productsQuery = useQuery({ queryKey: ['store', store?.id, 'products'], queryFn: () => listProductsByStore(store!.id), enabled: !!store });
  const product = productsQuery.data?.find((p) => p.slug === productSlug);

  if (storeQuery.isLoading || productsQuery.isLoading || !product) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: theme.background, alignItems: 'center', justifyContent: 'center' }}>
        <ActivityIndicator />
      </SafeAreaView>
    );
  }

  const cap = product.trackStock ? product.stockQuantity : Infinity;
  const outOfStock = product.status === 'out-of-stock' || (product.trackStock && product.stockQuantity <= 0);

  const handleAddToCart = () => {
    const added = addItem(product, quantity);
    if (added) {
      Alert.alert('Added to cart', `${product.name} added to your cart.`, [
        { text: 'Continue shopping', style: 'cancel' },
        { text: 'View cart', onPress: () => router.push('/cart' as Href) },
      ]);
      return;
    }
    Alert.alert(
      'Replace cart?',
      `Your cart has items from ${cart.storeName}. Adding this will start a new cart from ${product.storeName}.`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Replace cart',
          onPress: () => {
            replaceCartWithItem(product, quantity);
            Alert.alert('Added to cart', `${product.name} added to your cart.`, [
              { text: 'Continue shopping', style: 'cancel' },
              { text: 'View cart', onPress: () => router.push('/cart' as Href) },
            ]);
          },
        },
      ],
    );
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <Stack.Screen options={{ title: product.name }} />
      <ScrollView contentContainerStyle={styles.container}>
        <Image source={{ uri: product.images[0]?.url }} style={[styles.image, { backgroundColor: theme.backgroundElement }]} contentFit="cover" />

        <View style={styles.headerRow}>
          <TouchableOpacity onPress={() => router.push(hrefs.store(product.storeSlug))}>
            <ThemedText type="small" themeColor="textSecondary">
              {product.storeName}
            </ThemedText>
          </TouchableOpacity>
          <WishlistButton productId={product.id} />
        </View>

        <ThemedText type="title" style={styles.name}>
          {product.name}
        </ThemedText>
        <ThemedText type="small" themeColor="textSecondary">
          {product.rating.toFixed(1)} ★ ({product.reviewCount})
        </ThemedText>

        <View style={styles.priceRow}>
          <ThemedText type="title" style={styles.price}>
            {formatCurrency(product.price, platformConfig)}
          </ThemedText>
          {product.compareAtPrice ? (
            <ThemedText type="small" themeColor="textSecondary" style={styles.strike}>
              {formatCurrency(product.compareAtPrice, platformConfig)}
            </ThemedText>
          ) : null}
        </View>

        {outOfStock ? (
          <ThemedText style={{ color: '#D64545' }}>Out of stock</ThemedText>
        ) : product.trackStock ? (
          <ThemedText type="small" themeColor="textSecondary">
            Only {product.stockQuantity} left in stock
          </ThemedText>
        ) : null}

        <ThemedText style={styles.description}>{product.description}</ThemedText>

        {!outOfStock ? (
          <View style={styles.addRow}>
            <View style={styles.stepper}>
              <TouchableOpacity style={[styles.stepperButton, { backgroundColor: theme.backgroundElement }]} disabled={quantity <= 1} onPress={() => setQuantity((q) => q - 1)}>
                <ThemedText>−</ThemedText>
              </TouchableOpacity>
              <ThemedText style={styles.stepperValue}>{quantity}</ThemedText>
              <TouchableOpacity
                style={[styles.stepperButton, { backgroundColor: theme.backgroundElement }]}
                disabled={quantity >= cap}
                onPress={() => setQuantity((q) => Math.min(q + 1, cap))}>
                <ThemedText>+</ThemedText>
              </TouchableOpacity>
            </View>
            <TouchableOpacity style={styles.addButton} onPress={handleAddToCart}>
              <ThemedText style={styles.addButtonText}>Add to cart</ThemedText>
            </TouchableOpacity>
          </View>
        ) : null}

        <ReviewsSection
          queryKey={['product', product.id, 'reviews']}
          listReviews={() => listProductReviews(product.id)}
          createReview={(input) => createProductReview(product.id, input)}
        />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { padding: Spacing.three, gap: Spacing.two, paddingBottom: Spacing.six },
  image: { width: '100%', aspectRatio: 1, borderRadius: 16 },
  headerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  name: { fontSize: 22, lineHeight: 28 },
  priceRow: { flexDirection: 'row', alignItems: 'baseline', gap: Spacing.two },
  price: { fontSize: 24, lineHeight: 30 },
  strike: { textDecorationLine: 'line-through' },
  description: { marginTop: Spacing.two },
  addRow: { flexDirection: 'row', alignItems: 'center', gap: Spacing.three, marginTop: Spacing.two },
  stepper: { flexDirection: 'row', alignItems: 'center', gap: Spacing.two },
  stepperButton: { width: 36, height: 36, borderRadius: 8, alignItems: 'center', justifyContent: 'center' },
  stepperValue: { minWidth: 24, textAlign: 'center' },
  addButton: { flex: 1, height: 48, borderRadius: 12, backgroundColor: '#208AEF', alignItems: 'center', justifyContent: 'center' },
  addButtonText: { color: '#fff', fontWeight: '700' },
});
