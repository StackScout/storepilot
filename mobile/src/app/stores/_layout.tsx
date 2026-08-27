import { Stack } from 'expo-router';

export default function StoresLayout() {
  return (
    <Stack>
      <Stack.Screen name="[slug]/index" options={{ title: '' }} />
      <Stack.Screen name="[slug]/products/[productSlug]" options={{ title: '' }} />
      <Stack.Screen name="[slug]/services/[serviceSlug]" options={{ title: '' }} />
    </Stack>
  );
}
