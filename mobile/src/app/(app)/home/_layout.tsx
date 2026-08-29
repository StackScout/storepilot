import { Stack } from 'expo-router';

export default function HomeLayout() {
  return (
    <Stack>
      <Stack.Screen name="index" options={{ title: 'StorePilot' }} />
      <Stack.Screen name="stores/[slug]/index" options={{ title: '' }} />
      <Stack.Screen name="stores/[slug]/products/[productSlug]" options={{ title: '' }} />
      <Stack.Screen name="stores/[slug]/services/[serviceSlug]" options={{ title: '' }} />
    </Stack>
  );
}
