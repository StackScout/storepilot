import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { StyleSheet, TouchableOpacity } from 'react-native';

import type { Product } from '@storepilot/shared-api';

import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { usePlatformConfig, formatCurrency } from '@/lib/platform-config';
import { useStoreHrefs } from '@/hooks/use-store-href';
import { useTheme } from '@/hooks/use-theme';

export function ProductTile({ product }: { product: Product }) {
  const theme = useTheme();
  const router = useRouter();
  const hrefs = useStoreHrefs();
  const platformConfig = usePlatformConfig();

  return (
    <TouchableOpacity
      style={styles.container}
      onPress={() => router.push(hrefs.product(product.storeSlug, product.slug))}>
      <Image source={{ uri: product.images[0]?.url }} style={[styles.image, { backgroundColor: theme.backgroundElement }]} contentFit="cover" />
      <ThemedText type="small" themeColor="textSecondary" numberOfLines={1}>
        {product.storeName}
      </ThemedText>
      <ThemedText type="smallBold" numberOfLines={2} style={styles.name}>
        {product.name}
      </ThemedText>
      <ThemedText type="smallBold">{formatCurrency(product.price, platformConfig)}</ThemedText>
      {product.compareAtPrice ? (
        <ThemedText type="small" themeColor="textSecondary" style={styles.strike}>
          {formatCurrency(product.compareAtPrice, platformConfig)}
        </ThemedText>
      ) : null}
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  container: { width: 160, gap: 2 },
  image: { width: 160, height: 160, borderRadius: 12, marginBottom: Spacing.half },
  name: { minHeight: 34 },
  strike: { textDecorationLine: 'line-through' },
});
