import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { ActivityIndicator, Alert } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { getProduct, productToFormInput, updateProduct } from '@/api/products';
import { ApiError } from '@/lib/api-client';
import { ProductForm, type ProductFormValue } from '@/components/product-form';
import { useTheme } from '@/hooks/use-theme';

export default function EditProductScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const theme = useTheme();
  const router = useRouter();
  const queryClient = useQueryClient();

  const productQuery = useQuery({ queryKey: ['product', id], queryFn: () => getProduct(id!), enabled: !!id });

  const updateMutation = useMutation({
    mutationFn: (value: ProductFormValue) => {
      const { newImageUris, ...input } = value;
      return updateProduct(id!, input, newImageUris.length > 0 ? newImageUris : undefined);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['product', id] });
      queryClient.invalidateQueries({ queryKey: ['store'] });
      router.back();
    },
    onError: (e) => Alert.alert('Could not save product', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  if (productQuery.isLoading || !productQuery.data) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: theme.background, alignItems: 'center', justifyContent: 'center' }}>
        <ActivityIndicator />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }}>
      <ProductForm
        initial={productToFormInput(productQuery.data)}
        existingImages={productQuery.data.images}
        submitLabel="Save changes"
        submitting={updateMutation.isPending}
        onSubmit={(v) => updateMutation.mutate(v)}
      />
    </SafeAreaView>
  );
}
