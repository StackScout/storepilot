import { useQuery } from '@tanstack/react-query';
import { router, type Href } from 'expo-router';
import { FlatList, RefreshControl, StyleSheet, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { listMyConversations } from '@/api/buyer-messaging';
import type { ConversationResponse } from '@/api/types';
import { EmptyState } from '@/components/empty-state';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { formatDateTime } from '@/lib/format';

function ConversationRow({ conversation, onPress }: { conversation: ConversationResponse; onPress: () => void }) {
  const theme = useTheme();
  return (
    <TouchableOpacity onPress={onPress} style={[styles.row, { backgroundColor: theme.backgroundElement }]}>
      <View style={styles.rowTop}>
        <ThemedText type="smallBold">{conversation.storeName}</ThemedText>
        {conversation.unreadCount > 0 ? (
          <View style={styles.badge}>
            <ThemedText style={styles.badgeText}>{conversation.unreadCount}</ThemedText>
          </View>
        ) : null}
      </View>
      <ThemedText type="small" themeColor="textSecondary">
        {conversation.lastMessageAt ? formatDateTime(conversation.lastMessageAt) : 'No messages yet'}
      </ThemedText>
    </TouchableOpacity>
  );
}

export default function BuyerConversationsScreen() {
  const theme = useTheme();
  const conversationsQuery = useQuery({ queryKey: ['me', 'conversations'], queryFn: listMyConversations, refetchInterval: 15000 });

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <FlatList
        data={conversationsQuery.data ?? []}
        keyExtractor={(c) => c.id}
        contentContainerStyle={styles.list}
        refreshControl={<RefreshControl refreshing={conversationsQuery.isFetching} onRefresh={() => conversationsQuery.refetch()} />}
        renderItem={({ item }) => <ConversationRow conversation={item} onPress={() => router.push(`/account/messages/${item.id}` as Href)} />}
        ListEmptyComponent={!conversationsQuery.isLoading ? <EmptyState title="No conversations yet" /> : null}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  list: { padding: Spacing.three, gap: Spacing.two },
  row: { borderRadius: 14, padding: Spacing.three, gap: Spacing.half },
  rowTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  badge: { backgroundColor: '#208AEF', minWidth: 22, height: 22, borderRadius: 11, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 6 },
  badgeText: { color: '#fff', fontSize: 12, fontWeight: '700' },
});
