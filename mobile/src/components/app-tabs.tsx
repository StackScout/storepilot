import { useQuery } from '@tanstack/react-query';
import { NativeTabs } from 'expo-router/unstable-native-tabs';
import { useColorScheme } from 'react-native';

import { getStoreSettings } from '@/api/store-settings';
import { getMyStore } from '@/api/stores';
import { Colors } from '@/constants/theme';

export default function AppTabs() {
  const scheme = useColorScheme();
  const colors = Colors[scheme === 'unspecified' ? 'light' : scheme];

  const storeQuery = useQuery({ queryKey: ['me', 'store'], queryFn: getMyStore });
  const storeId = storeQuery.data?.id;
  // Hidden until settings actually load (rather than shown-then-hidden) — mirrors the web
  // sidebar's equivalent gate, see dashboard-sidebar.tsx's doc comment on BOOKING_NAV_ITEMS.
  const settingsQuery = useQuery({
    queryKey: ['store', storeId, 'settings'],
    queryFn: () => getStoreSettings(storeId!),
    enabled: !!storeId,
  });
  const bookingsEnabled = settingsQuery.data?.bookingsEnabled ?? false;

  return (
    <NativeTabs
      backgroundColor={colors.background}
      indicatorColor={colors.backgroundElement}
      labelStyle={{ selected: { color: colors.text } }}>
      <NativeTabs.Trigger name="dashboard">
        <NativeTabs.Trigger.Label>Dashboard</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon sf="house.fill" md="home" />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="orders">
        <NativeTabs.Trigger.Label>Orders</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon sf="shippingbox.fill" md="local_shipping" />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="products">
        <NativeTabs.Trigger.Label>Products</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon sf="tag.fill" md="sell" />
      </NativeTabs.Trigger>

      {bookingsEnabled ? (
        <NativeTabs.Trigger name="bookings">
          <NativeTabs.Trigger.Label>Bookings</NativeTabs.Trigger.Label>
          <NativeTabs.Trigger.Icon sf="calendar" md="calendar_month" />
        </NativeTabs.Trigger>
      ) : null}

      <NativeTabs.Trigger name="messages">
        <NativeTabs.Trigger.Label>Messages</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon sf="message.fill" md="chat" />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="returns">
        <NativeTabs.Trigger.Label>Returns</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon sf="arrow.uturn.left.circle.fill" md="assignment_return" />
      </NativeTabs.Trigger>
    </NativeTabs>
  );
}
