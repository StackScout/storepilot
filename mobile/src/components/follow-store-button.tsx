import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, StyleSheet, TouchableOpacity } from 'react-native';

import { followStore, getFollowStatus, unfollowStore } from '@/api/buyer-stores';
import { ThemedText } from '@/components/themed-text';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-client';
import { useAuthStore } from '@/store/auth-store';
import { router, type Href } from 'expo-router';

export function FollowStoreButton({ storeId }: { storeId: string }) {
  const theme = useTheme();
  const queryClient = useQueryClient();
  const isSignedIn = useAuthStore((s) => s.isSignedIn);
  const role = useAuthStore((s) => s.role);

  const statusQuery = useQuery({ queryKey: ['store', storeId, 'follow'], queryFn: () => getFollowStatus(storeId) });

  const mutation = useMutation({
    mutationFn: async () => {
      if (statusQuery.data?.following) {
        await unfollowStore(storeId);
      } else {
        await followStore(storeId);
      }
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['store', storeId, 'follow'] }),
    onError: (e) => Alert.alert('Could not update', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  const following = statusQuery.data?.following ?? false;

  return (
    <TouchableOpacity
      style={[styles.button, { borderColor: theme.textSecondary }, following && { backgroundColor: theme.backgroundElement }]}
      onPress={() => {
        if (!isSignedIn || role !== 'buyer') {
          router.push('/account/login' as Href);
          return;
        }
        mutation.mutate();
      }}>
      <ThemedText type="small">{following ? 'Following' : 'Follow'}</ThemedText>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  button: { height: 36, paddingHorizontal: 16, borderRadius: 18, borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
});
