import { Stack } from 'expo-router';

export default function DashboardLayout() {
  return (
    <Stack>
      <Stack.Screen name="index" options={{ title: 'Dashboard' }} />
      <Stack.Screen name="notifications" options={{ title: 'Notifications' }} />
      <Stack.Screen name="settings/index" options={{ title: 'Settings' }} />
      <Stack.Screen name="settings/store" options={{ title: 'Store settings' }} />
      <Stack.Screen name="settings/profile" options={{ title: 'Store profile' }} />
      <Stack.Screen name="settings/billing" options={{ title: 'Billing' }} />
      <Stack.Screen name="settings/payouts" options={{ title: 'Payouts' }} />
      <Stack.Screen name="settings/fee-collections" options={{ title: 'Fee collections' }} />
      <Stack.Screen name="settings/coupons/index" options={{ title: 'Coupons' }} />
      <Stack.Screen name="settings/coupons/new" options={{ title: 'New coupon', presentation: 'modal' }} />
      <Stack.Screen name="settings/mfa" options={{ title: 'Two-factor authentication' }} />
      <Stack.Screen name="settings/account" options={{ title: 'Account' }} />
    </Stack>
  );
}
