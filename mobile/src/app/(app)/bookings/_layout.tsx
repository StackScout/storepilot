import { Stack } from 'expo-router';

export default function BookingsLayout() {
  return (
    <Stack>
      <Stack.Screen name="index" options={{ title: 'Bookings' }} />
      <Stack.Screen name="[id]" options={{ title: 'Booking' }} />
      <Stack.Screen name="services/index" options={{ title: 'Services' }} />
      <Stack.Screen name="services/new" options={{ title: 'New service', presentation: 'modal' }} />
      <Stack.Screen name="services/[id]" options={{ title: 'Edit service' }} />
    </Stack>
  );
}
