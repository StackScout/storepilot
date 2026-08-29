import { useMutation, useQuery } from '@tanstack/react-query';
import { Image } from 'expo-image';
import { Stack, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Alert, FlatList, ScrollView, StyleSheet, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { listCategories } from '@/api/categories';
import { listProductsByStore } from '@/api/buyer-products';
import { listServicesByStore } from '@/api/buyer-services';
import { getStoreBySlug } from '@/api/buyer-stores';
import { createStoreReview, listStoreReviews } from '@/api/buyer-reviews';
import { getOrCreateConversation } from '@/api/buyer-messaging';
import { useAuthStore } from '@/store/auth-store';
import { EmptyState } from '@/components/empty-state';
import { FollowStoreButton } from '@/components/follow-store-button';
import { ProductTile } from '@/components/product-tile';
import { ReviewsSection } from '@/components/reviews-section';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { formatCurrency, usePlatformConfig } from '@/lib/platform-config';
import { router, type Href } from 'expo-router';

export default function StoreScreen() {
  const theme = useTheme();
  const platformConfig = usePlatformConfig();
  const { slug } = useLocalSearchParams<{ slug: string }>();

  const isSignedIn = useAuthStore((s) => s.isSignedIn);
  const role = useAuthStore((s) => s.role);

  const storeQuery = useQuery({ queryKey: ['store', slug], queryFn: () => getStoreBySlug(slug!), enabled: !!slug });
  const store = storeQuery.data;

  const messageMutation = useMutation({
    mutationFn: () => getOrCreateConversation(store!.id),
    onSuccess: (conversation) => router.push(`/account/messages/${conversation.id}` as Href),
    onError: () => Alert.alert('Could not start conversation', 'Please try again.'),
  });

  const productsQuery = useQuery({ queryKey: ['store', store?.id, 'products'], queryFn: () => listProductsByStore(store!.id), enabled: !!store });
  const servicesQuery = useQuery({ queryKey: ['store', store?.id, 'services'], queryFn: () => listServicesByStore(store!.id), enabled: !!store });
  const categoriesQuery = useQuery({ queryKey: ['categories'], queryFn: listCategories, staleTime: 5 * 60_000 });
  const categoryLabel = categoriesQuery.data?.find((c) => c.wireValue === store?.category)?.name ?? store?.category;
  const [descriptionExpanded, setDescriptionExpanded] = useState(false);

  if (storeQuery.isLoading || !store) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: theme.background, alignItems: 'center', justifyContent: 'center' }}>
        <ActivityIndicator />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <Stack.Screen options={{ title: store.name }} />
      <ScrollView contentContainerStyle={styles.container}>
        {store.bannerUrl ? <Image source={{ uri: store.bannerUrl }} style={styles.banner} contentFit="cover" /> : null}

        <View style={styles.logoRow}>
          {store.logoUrl ? (
            <Image source={{ uri: store.logoUrl }} style={[styles.logo, { backgroundColor: theme.background, borderColor: theme.background }]} contentFit="cover" />
          ) : (
            <View style={[styles.logo, { backgroundColor: theme.backgroundElement, borderColor: theme.background }]} />
          )}
        </View>

        <View style={styles.header}>
          <ThemedText type="title" style={styles.name}>
            {store.name}
          </ThemedText>
          <ThemedText type="small" themeColor="textSecondary">
            {categoryLabel} · {store.address.city}
          </ThemedText>
          <ThemedText type="small" themeColor="textSecondary" style={styles.stats}>
            {store.rating.toFixed(1)} ★ ({store.reviewCount}) · {store.productCount} products
          </ThemedText>

          <View style={styles.headerActions}>
            <FollowStoreButton storeId={store.id} style={styles.actionButton} />
            <TouchableOpacity
              style={[styles.messageButton, styles.actionButton, { borderColor: theme.textSecondary }]}
              onPress={() => {
                if (!isSignedIn || role !== 'buyer') {
                  router.push('/account/login' as Href);
                  return;
                }
                messageMutation.mutate();
              }}>
              <ThemedText type="small">Message</ThemedText>
            </TouchableOpacity>
          </View>
        </View>

        <ThemedText style={styles.tagline}>{store.tagline}</ThemedText>
        {descriptionExpanded ? (
          <ThemedText type="small" themeColor="textSecondary" style={styles.description}>
            {store.description}
          </ThemedText>
        ) : null}
        <TouchableOpacity onPress={() => setDescriptionExpanded((v) => !v)} style={styles.readMore}>
          <ThemedText type="small" themeColor="textSecondary" style={{ textDecorationLine: 'underline' }}>
            {descriptionExpanded ? 'Show less' : 'Read more'}
          </ThemedText>
        </TouchableOpacity>

        {(productsQuery.data ?? []).length > 0 ? (
          <>
            <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
              PRODUCTS
            </ThemedText>
            <FlatList
              horizontal
              showsHorizontalScrollIndicator={false}
              data={productsQuery.data}
              keyExtractor={(p) => p.id}
              contentContainerStyle={styles.row}
              renderItem={({ item }) => <ProductTile product={item} />}
            />
          </>
        ) : null}

        {(servicesQuery.data ?? []).length > 0 ? (
          <>
            <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
              SERVICES
            </ThemedText>
            {servicesQuery.data!.map((service) => (
              <TouchableOpacity
                key={service.id}
                style={[styles.serviceRow, { borderColor: theme.backgroundElement }]}
                onPress={() => router.push(`/stores/${store.slug}/services/${service.slug}` as Href)}>
                <ThemedText type="smallBold">{service.name}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {formatCurrency(service.price, platformConfig)} · {service.durationMinutes} min
                </ThemedText>
              </TouchableOpacity>
            ))}
          </>
        ) : null}

        {(productsQuery.data ?? []).length === 0 && (servicesQuery.data ?? []).length === 0 && !productsQuery.isLoading && !servicesQuery.isLoading ? (
          <EmptyState title="Nothing here yet" description="This store hasn't listed any products or services yet." />
        ) : null}

        <ReviewsSection
          queryKey={['store', store.id, 'reviews']}
          listReviews={() => listStoreReviews(store.id)}
          createReview={(input) => createStoreReview(store.id, input)}
        />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { paddingBottom: Spacing.six },
  banner: { width: '100%', height: 140 },
  // Overlaps the banner by half its own height, matching the standard
  // profile-header pattern (Etsy, Instagram, etc.) instead of a small
  // inline logo squeezed beside the name/stats text.
  logoRow: { paddingHorizontal: Spacing.three, marginTop: -44 },
  logo: { width: 88, height: 88, borderRadius: 20, borderWidth: 3 },
  header: { paddingHorizontal: Spacing.three, paddingTop: Spacing.two, gap: 4 },
  headerActions: { flexDirection: 'row', gap: Spacing.two, marginTop: Spacing.two },
  actionButton: { flex: 1 },
  messageButton: { height: 36, paddingHorizontal: 16, borderRadius: 18, borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
  name: { fontSize: 22, lineHeight: 28 },
  stats: { marginTop: 2 },
  tagline: { paddingHorizontal: Spacing.three, marginTop: Spacing.three },
  description: { paddingHorizontal: Spacing.three, marginTop: 2 },
  readMore: { paddingHorizontal: Spacing.three, marginTop: 2 },
  sectionLabel: { marginTop: Spacing.three, marginHorizontal: Spacing.three },
  row: { gap: Spacing.three, paddingHorizontal: Spacing.three, paddingVertical: Spacing.two },
  serviceRow: { marginHorizontal: Spacing.three, borderTopWidth: 1, paddingVertical: Spacing.two, gap: 2 },
});
