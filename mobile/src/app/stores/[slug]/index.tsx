import { useMutation, useQuery } from '@tanstack/react-query';
import { Image } from 'expo-image';
import { Stack, useLocalSearchParams } from 'expo-router';
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
        <View style={styles.header}>
          {store.logoUrl ? (
            <Image source={{ uri: store.logoUrl }} style={[styles.logo, { backgroundColor: theme.backgroundElement }]} contentFit="cover" />
          ) : (
            <View style={[styles.logo, { backgroundColor: theme.backgroundElement }]} />
          )}
          <View style={styles.headerInfo}>
            <ThemedText type="title" style={styles.name}>
              {store.name}
            </ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {categoryLabel} · {store.address.city}
            </ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {store.rating.toFixed(1)} ★ ({store.reviewCount}) · {store.productCount} products
            </ThemedText>
          </View>
          <View style={styles.headerActions}>
            <FollowStoreButton storeId={store.id} />
            <TouchableOpacity
              style={[styles.messageButton, { borderColor: theme.textSecondary }]}
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
        <ThemedText type="small" themeColor="textSecondary">
          {store.description}
        </ThemedText>

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
  header: { flexDirection: 'row', alignItems: 'flex-start', gap: Spacing.two, padding: Spacing.three },
  logo: { width: 64, height: 64, borderRadius: 12 },
  headerInfo: { flex: 1, gap: 2 },
  headerActions: { gap: Spacing.two, alignItems: 'flex-end' },
  messageButton: { height: 36, paddingHorizontal: 16, borderRadius: 18, borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
  name: { fontSize: 22, lineHeight: 28 },
  tagline: { paddingHorizontal: Spacing.three },
  sectionLabel: { marginTop: Spacing.three, marginHorizontal: Spacing.three },
  row: { gap: Spacing.three, paddingHorizontal: Spacing.three, paddingVertical: Spacing.two },
  serviceRow: { marginHorizontal: Spacing.three, borderTopWidth: 1, paddingVertical: Spacing.two, gap: 2 },
});
