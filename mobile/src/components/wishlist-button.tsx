import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { router, type Href } from 'expo-router';
import { Alert, StyleSheet, TouchableOpacity } from 'react-native';

import { addToWishlist, getWishlistStatus, removeFromWishlist } from '@/api/buyer-products';
import { ThemedText } from '@/components/themed-text';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-client';
import { useAuthStore } from '@/store/auth-store';

export function WishlistButton({ productId }: { productId: string }) {
  const theme = useTheme();
  const queryClient = useQueryClient();
  const isSignedIn = useAuthStore((s) => s.isSignedIn);
  const role = useAuthStore((s) => s.role);

  const statusQuery = useQuery({ queryKey: ['product', productId, 'wishlist'], queryFn: () => getWishlistStatus(productId) });

  const mutation = useMutation({
    mutationFn: async () => {
      if (statusQuery.data?.wishlisted) {
        await removeFromWishlist(productId);
      } else {
        await addToWishlist(productId);
      }
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['product', productId, 'wishlist'] }),
    onError: (e) => Alert.alert('Could not update', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  const wishlisted = statusQuery.data?.wishlisted ?? false;

  return (
    <TouchableOpacity
      style={[styles.button, { borderColor: theme.textSecondary }]}
      onPress={() => {
        if (!isSignedIn || role !== 'buyer') {
          router.push('/account/login' as Href);
          return;
        }
        mutation.mutate();
      }}>
      <ThemedText style={{ fontSize: 18, color: wishlisted ? '#D64545' : theme.text }}>{wishlisted ? '♥' : '♡'}</ThemedText>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  button: { width: 36, height: 36, borderRadius: 18, borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
});
