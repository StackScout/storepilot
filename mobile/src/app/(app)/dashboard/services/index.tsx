import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { Alert, FlatList, StyleSheet, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { deleteService, listStoreServices } from '@/api/bookable-services';
import { getMyStore } from '@/api/stores';
import type { BookableServiceResponse } from '@/api/types';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-client';
import { formatMoney } from '@/lib/format';

const STATUS_COLORS: Record<string, string> = { active: '#1E9E5A', draft: '#60646C' };

function ServiceRow({ service, onPress, onDelete }: { service: BookableServiceResponse; onPress: () => void; onDelete: () => void }) {
  const theme = useTheme();
  return (
    <TouchableOpacity
      style={[styles.row, { backgroundColor: theme.backgroundElement }]}
      onPress={onPress}
      onLongPress={() =>
        Alert.alert('Delete service?', `This will permanently remove "${service.name}". This can't be undone.`, [
          { text: 'Cancel', style: 'cancel' },
          { text: 'Delete', style: 'destructive', onPress: onDelete },
        ])
      }>
      {service.images[0] ? (
        <Image source={{ uri: service.images[0].url }} style={styles.thumb} contentFit="cover" />
      ) : (
        <View style={[styles.thumb, { backgroundColor: theme.backgroundSelected }]} />
      )}
      <View style={styles.rowText}>
        <ThemedText type="smallBold" numberOfLines={1}>
          {service.name}
        </ThemedText>
        <ThemedText type="small" themeColor="textSecondary">
          {formatMoney(service.price)} · {service.durationMinutes} min
        </ThemedText>
        <ThemedText type="small" style={{ color: STATUS_COLORS[service.status] }}>
          {service.status}
        </ThemedText>
      </View>
    </TouchableOpacity>
  );
}

export default function ServicesListScreen() {
  const theme = useTheme();
  const router = useRouter();
  const queryClient = useQueryClient();

  const storeQuery = useQuery({ queryKey: ['me', 'store'], queryFn: getMyStore });
  const storeId = storeQuery.data?.id;

  const servicesQuery = useQuery({
    queryKey: ['store', storeId, 'services'],
    queryFn: () => listStoreServices(storeId!),
    enabled: !!storeId,
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteService(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['store', storeId, 'services'] }),
    onError: (e) =>
      Alert.alert('Could not delete service', e instanceof ApiError ? e.message : "Cancel or complete any upcoming bookings for it first."),
  });

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <FlatList
        data={servicesQuery.data ?? []}
        keyExtractor={(s) => s.id}
        contentContainerStyle={styles.list}
        renderItem={({ item }) => (
          <ServiceRow service={item} onPress={() => router.push(`/dashboard/services/${item.id}`)} onDelete={() => deleteMutation.mutate(item.id)} />
        )}
        ListEmptyComponent={
          !servicesQuery.isLoading ? (
            <ThemedText themeColor="textSecondary" style={styles.empty}>
              No services yet — add your first bookable service so buyers can start booking appointments.
            </ThemedText>
          ) : null
        }
      />
      <TouchableOpacity style={styles.fab} onPress={() => router.push('/dashboard/services/new')}>
        <ThemedText style={styles.fabText}>+ Add service</ThemedText>
      </TouchableOpacity>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  list: { padding: Spacing.three, gap: Spacing.two, paddingBottom: Spacing.six },
  row: { borderRadius: 14, padding: Spacing.two, flexDirection: 'row', alignItems: 'center', gap: Spacing.two },
  thumb: { width: 56, height: 56, borderRadius: 10 },
  rowText: { flex: 1, gap: 2 },
  empty: { textAlign: 'center', marginTop: Spacing.six },
  fab: {
    position: 'absolute',
    bottom: Spacing.four,
    left: Spacing.three,
    right: Spacing.three,
    height: 50,
    borderRadius: 12,
    backgroundColor: '#208AEF',
    alignItems: 'center',
    justifyContent: 'center',
  },
  fabText: { color: '#fff', fontWeight: '700' },
});
