import { useQuery, useQueryClient } from '@tanstack/react-query';
import type { Href } from 'expo-router';
import { Stack, useRouter } from 'expo-router';
import { FlatList, RefreshControl, StyleSheet, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { listBuyerNotifications, markAllBuyerNotificationsRead, markBuyerNotificationRead } from '@/api/buyer-notifications';
import type { BuyerNotificationResponse } from '@/api/types';
import { EmptyState } from '@/components/empty-state';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { formatDateTime } from '@/lib/format';

/** Buyer-side mirror of dashboard/notifications.tsx's routeForNotification — same (type, id) vocabulary, buyer-side routes. */
function routeForNotification(notification: BuyerNotificationResponse): Href | null {
  const id = notification.entityId;
  switch (notification.type) {
    case 'order':
      return `/account/orders/${id}` as Href;
    case 'booking':
      return `/account/bookings/${id}` as Href;
    case 'conversation':
      return `/account/messages/${id}` as Href;
    default:
      return null;
  }
}

function NotificationRow({ notification, onPress }: { notification: BuyerNotificationResponse; onPress: () => void }) {
  const theme = useTheme();
  return (
    <TouchableOpacity onPress={onPress} style={[styles.row, { backgroundColor: theme.backgroundElement }]}>
      {!notification.read ? <View style={styles.unreadDot} /> : <View style={styles.unreadDotSpacer} />}
      <View style={styles.rowContent}>
        <ThemedText type={notification.read ? 'default' : 'smallBold'}>{notification.title}</ThemedText>
        <ThemedText type="small" themeColor="textSecondary" numberOfLines={2}>
          {notification.body}
        </ThemedText>
        <ThemedText type="small" themeColor="textSecondary">
          {formatDateTime(notification.createdAt)}
        </ThemedText>
      </View>
    </TouchableOpacity>
  );
}

export default function BuyerNotificationsScreen() {
  const theme = useTheme();
  const router = useRouter();
  const queryClient = useQueryClient();

  const notificationsQuery = useQuery({
    queryKey: ['me', 'buyer', 'notifications'],
    queryFn: () => listBuyerNotifications(),
  });

  const notifications = notificationsQuery.data?.content ?? [];
  const hasUnread = notifications.some((n) => !n.read);

  const onPressNotification = async (notification: BuyerNotificationResponse) => {
    if (!notification.read) {
      queryClient.setQueryData(['me', 'buyer', 'notifications'], (old: typeof notificationsQuery.data) =>
        old ? { ...old, content: old.content.map((n) => (n.id === notification.id ? { ...n, read: true } : n)) } : old,
      );
      markBuyerNotificationRead(notification.id).finally(() => {
        queryClient.invalidateQueries({ queryKey: ['me', 'buyer', 'notifications', 'summary'] });
      });
    }
    const route = routeForNotification(notification);
    if (route) router.push(route);
  };

  const onMarkAllRead = async () => {
    queryClient.setQueryData(['me', 'buyer', 'notifications'], (old: typeof notificationsQuery.data) =>
      old ? { ...old, content: old.content.map((n) => ({ ...n, read: true })) } : old,
    );
    await markAllBuyerNotificationsRead();
    queryClient.invalidateQueries({ queryKey: ['me', 'buyer', 'notifications', 'summary'] });
  };

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <Stack.Screen
        options={{
          headerRight: hasUnread
            ? () => (
                <TouchableOpacity hitSlop={8} onPress={onMarkAllRead}>
                  <ThemedText type="small">Mark all read</ThemedText>
                </TouchableOpacity>
              )
            : undefined,
        }}
      />
      <FlatList
        data={notifications}
        keyExtractor={(n) => n.id}
        contentContainerStyle={styles.list}
        refreshControl={<RefreshControl refreshing={notificationsQuery.isFetching} onRefresh={() => notificationsQuery.refetch()} />}
        renderItem={({ item }) => <NotificationRow notification={item} onPress={() => onPressNotification(item)} />}
        ListEmptyComponent={
          !notificationsQuery.isLoading ? <EmptyState title="No notifications yet" description="You're all caught up." /> : null
        }
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  list: { padding: Spacing.three, gap: Spacing.two, flexGrow: 1 },
  row: { flexDirection: 'row', borderRadius: 14, padding: Spacing.three, gap: Spacing.two },
  rowContent: { flex: 1, gap: Spacing.half },
  unreadDot: { width: 8, height: 8, borderRadius: 4, backgroundColor: '#208AEF', marginTop: 6 },
  unreadDotSpacer: { width: 8 },
});
