import { Stack } from 'expo-router';

export default function CartLayout() {
  return (
    <Stack screenOptions={{ headerBackButtonDisplayMode: 'minimal' }}>
      <Stack.Screen name="index" options={{ title: 'Cart' }} />
      <Stack.Screen name="checkout" options={{ title: 'Checkout' }} />
      <Stack.Screen name="stores/[slug]/index" options={{ title: '' }} />
      <Stack.Screen name="stores/[slug]/products/[productSlug]" options={{ title: '' }} />
      <Stack.Screen name="stores/[slug]/services/[serviceSlug]" options={{ title: '' }} />
    </Stack>
  );
}
