import { Stack } from 'expo-router';

export default function AccountLayout() {
  return (
    <Stack>
      <Stack.Screen name="index" options={{ title: 'Account' }} />
      <Stack.Screen name="login" options={{ title: 'Sign in', presentation: 'modal' }} />
      <Stack.Screen name="register" options={{ title: 'Create account', presentation: 'modal' }} />
      <Stack.Screen name="track" options={{ title: 'Track order/booking' }} />
      <Stack.Screen name="addresses" options={{ title: 'Addresses' }} />
      <Stack.Screen name="wishlist" options={{ title: 'Wishlist' }} />
      <Stack.Screen name="saved-searches" options={{ title: 'Saved searches' }} />
      <Stack.Screen name="orders/index" options={{ title: 'Order history' }} />
      <Stack.Screen name="orders/[id]" options={{ title: 'Order' }} />
      <Stack.Screen name="bookings/index" options={{ title: 'Booking history' }} />
      <Stack.Screen name="bookings/[id]" options={{ title: 'Booking' }} />
      <Stack.Screen name="messages/index" options={{ title: 'Messages' }} />
      <Stack.Screen name="messages/[id]" options={{ title: 'Conversation' }} />
      <Stack.Screen name="stores/[slug]/index" options={{ title: '' }} />
      <Stack.Screen name="stores/[slug]/products/[productSlug]" options={{ title: '' }} />
      <Stack.Screen name="stores/[slug]/services/[serviceSlug]" options={{ title: '' }} />
    </Stack>
  );
}
