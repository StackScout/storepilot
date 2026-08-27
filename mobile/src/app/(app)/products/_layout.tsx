import { Stack } from 'expo-router';

export default function ProductsLayout() {
  return (
    <Stack>
      <Stack.Screen name="index" options={{ title: 'Products' }} />
      <Stack.Screen name="new" options={{ title: 'New product', presentation: 'modal' }} />
      <Stack.Screen name="[id]" options={{ title: 'Edit product' }} />
    </Stack>
  );
}
