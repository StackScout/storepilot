import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { ActivityIndicator, Alert } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { createProduct } from '@/api/products';
import { getMyStore } from '@/api/stores';
import { ProductForm, type ProductFormValue } from '@/components/product-form';
import { ApiError } from '@/lib/api-client';
import { useTheme } from '@/hooks/use-theme';

function blankFor(category: string): ProductFormValue {
  return {
    name: '',
    description: '',
    category,
    price: 0,
    compareAtPrice: undefined,
    stockQuantity: 0,
    trackStock: true,
    sku: undefined,
    status: 'active',
    newImageUris: [],
  };
}

export default function NewProductScreen() {
  const theme = useTheme();
  const router = useRouter();
  const queryClient = useQueryClient();
  const storeQuery = useQuery({ queryKey: ['me', 'store'], queryFn: getMyStore });
  const storeId = storeQuery.data?.id;

  const createMutation = useMutation({
    mutationFn: (value: ProductFormValue) => {
      if (value.newImageUris.length === 0) {
        throw new Error('Add at least one photo before saving.');
      }
      const { newImageUris, ...input } = value;
      return createProduct(storeId!, input, newImageUris);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['store', storeId, 'products'] });
      router.back();
    },
    onError: (e) => Alert.alert('Could not create product', e instanceof ApiError ? e.message : e.message),
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
      <ProductForm
        initial={blankFor(storeQuery.data.category)}
        submitLabel="Save product"
        submitting={createMutation.isPending}
        onSubmit={(v) => createMutation.mutate(v)}
      />
    </SafeAreaView>
  );
}
