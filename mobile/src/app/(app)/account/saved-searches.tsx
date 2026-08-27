import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { router, type Href } from 'expo-router';
import { Alert, FlatList, StyleSheet, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { deleteSavedSearch, listSavedSearches } from '@/api/buyer-saved-searches';
import { EmptyState } from '@/components/empty-state';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-client';

export default function SavedSearchesScreen() {
  const theme = useTheme();
  const queryClient = useQueryClient();
  const savedSearchesQuery = useQuery({ queryKey: ['me', 'saved-searches'], queryFn: listSavedSearches });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteSavedSearch(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['me', 'saved-searches'] }),
    onError: (e) => Alert.alert('Could not delete', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <FlatList
        data={savedSearchesQuery.data ?? []}
        keyExtractor={(s) => s.id}
        contentContainerStyle={styles.list}
        ListEmptyComponent={!savedSearchesQuery.isLoading ? <EmptyState title="No saved searches" description="Save a search from the search page to see it here." /> : null}
        renderItem={({ item }) => (
          <TouchableOpacity
            style={[styles.row, { borderColor: theme.backgroundElement }]}
            onPress={() => router.push(`/search?${item.queryString}` as Href)}>
            <View style={styles.rowInfo}>
              <ThemedText type="smallBold">{item.name}</ThemedText>
              <ThemedText type="small" themeColor="textSecondary" numberOfLines={1}>
                {item.queryString}
              </ThemedText>
            </View>
            <TouchableOpacity onPress={() => deleteMutation.mutate(item.id)}>
              <ThemedText type="small" themeColor="textSecondary">
                Remove
              </ThemedText>
            </TouchableOpacity>
          </TouchableOpacity>
        )}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  list: { padding: Spacing.three, gap: Spacing.two },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', borderWidth: 1, borderRadius: 12, padding: Spacing.three },
  rowInfo: { flex: 1, gap: 2 },
});
