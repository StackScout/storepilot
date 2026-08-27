import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { ActivityIndicator, Alert } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { createService } from '@/api/bookable-services';
import { getMyStore } from '@/api/stores';
import { ServiceForm, type ServiceFormValue } from '@/components/service-form';
import { ApiError } from '@/lib/api-client';
import { useTheme } from '@/hooks/use-theme';

function blankFor(category: string): ServiceFormValue {
  return {
    name: '',
    description: '',
    category,
    price: 0,
    durationMinutes: 30,
    bufferMinutes: 0,
    status: 'active',
    newImageUris: [],
  };
}

export default function NewServiceScreen() {
  const theme = useTheme();
  const router = useRouter();
  const queryClient = useQueryClient();
  const storeQuery = useQuery({ queryKey: ['me', 'store'], queryFn: getMyStore });
  const storeId = storeQuery.data?.id;

  const createMutation = useMutation({
    mutationFn: (value: ServiceFormValue) => {
      if (value.newImageUris.length === 0) throw new Error('Add at least one photo before saving.');
      const { newImageUris, ...input } = value;
      return createService(storeId!, input, newImageUris);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['store', storeId, 'services'] });
      router.back();
    },
    onError: (e) => Alert.alert('Could not create service', e instanceof ApiError ? e.message : e.message),
  });

  if (storeQuery.isLoading || !storeQuery.data) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: theme.background, alignItems: 'center', justifyContent: 'center' }}>
        <ActivityIndicator />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }}>
      <ServiceForm
        initial={blankFor(storeQuery.data.category)}
        submitLabel="Save service"
        submitting={createMutation.isPending}
        onSubmit={(v) => createMutation.mutate(v)}
      />
    </SafeAreaView>
  );
}
