import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { ActivityIndicator, Alert } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { getService, serviceToFormInput, updateService } from '@/api/bookable-services';
import { ApiError } from '@/lib/api-client';
import { ServiceForm, type ServiceFormValue } from '@/components/service-form';
import { useTheme } from '@/hooks/use-theme';

export default function EditServiceScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const theme = useTheme();
  const router = useRouter();
  const queryClient = useQueryClient();

  const serviceQuery = useQuery({ queryKey: ['service', id], queryFn: () => getService(id!), enabled: !!id });

  const updateMutation = useMutation({
    mutationFn: (value: ServiceFormValue) => {
      const { newImageUris, ...input } = value;
      return updateService(id!, input, newImageUris.length > 0 ? newImageUris : undefined);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['service', id] });
      queryClient.invalidateQueries({ queryKey: ['store'] });
      router.back();
    },
    onError: (e) => Alert.alert('Could not save service', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  if (serviceQuery.isLoading || !serviceQuery.data) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: theme.background, alignItems: 'center', justifyContent: 'center' }}>
        <ActivityIndicator />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }}>
      <ServiceForm
        initial={serviceToFormInput(serviceQuery.data)}
        existingImages={serviceQuery.data.images}
        submitLabel="Save changes"
        submitting={updateMutation.isPending}
        onSubmit={(v) => updateMutation.mutate(v)}
      />
    </SafeAreaView>
  );
}
