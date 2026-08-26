import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams } from 'expo-router';
import { useRef, useState } from 'react';
import { ActivityIndicator, FlatList, KeyboardAvoidingView, Platform, StyleSheet, TextInput, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { getConversation, listMessages, sendMessage } from '@/api/messaging';
import type { MessageResponse } from '@/api/types';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { formatDateTime } from '@/lib/format';

function Bubble({ message }: { message: MessageResponse }) {
  const theme = useTheme();
  const isSeller = message.senderType === 'seller';
  return (
    <View style={[styles.bubbleRow, isSeller && styles.bubbleRowRight]}>
      <View style={[styles.bubble, { backgroundColor: isSeller ? '#208AEF' : theme.backgroundElement }]}>
        <ThemedText style={isSeller ? styles.bubbleTextSeller : undefined}>{message.body}</ThemedText>
      </View>
      <ThemedText type="small" themeColor="textSecondary" style={styles.bubbleTime}>
        {formatDateTime(message.createdAt)}
      </ThemedText>
    </View>
  );
}

export default function ConversationThreadScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const theme = useTheme();
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState('');
  const listRef = useRef<FlatList>(null);

  const conversationQuery = useQuery({ queryKey: ['conversation', id], queryFn: () => getConversation(id!), enabled: !!id });
  const messagesQuery = useQuery({
    queryKey: ['conversation', id, 'messages'],
    queryFn: () => listMessages(id!),
    enabled: !!id,
    refetchInterval: 5000,
  });

  const sendMutation = useMutation({
    mutationFn: (body: string) => sendMessage(id!, body),
    onSuccess: () => {
      setDraft('');
      queryClient.invalidateQueries({ queryKey: ['conversation', id, 'messages'] });
      queryClient.invalidateQueries({ queryKey: ['store'] });
    },
  });

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        {messagesQuery.isLoading ? (
          <View style={styles.loading}>
            <ActivityIndicator />
          </View>
        ) : (
          <FlatList
            ref={listRef}
            data={messagesQuery.data ?? []}
            keyExtractor={(m) => m.id}
            contentContainerStyle={styles.list}
            renderItem={({ item }) => <Bubble message={item} />}
            onContentSizeChange={() => listRef.current?.scrollToEnd({ animated: false })}
            ListHeaderComponent={
              conversationQuery.data ? (
                <ThemedText type="smallBold" style={styles.header}>
                  {conversationQuery.data.buyerName}
                </ThemedText>
              ) : null
            }
          />
        )}

        <View style={[styles.inputRow, { borderTopColor: theme.backgroundElement }]}>
          <TextInput
            style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
            placeholder="Type a message"
            placeholderTextColor={theme.textSecondary}
            value={draft}
            onChangeText={setDraft}
            multiline
          />
          <TouchableOpacity
            style={[styles.sendButton, (!draft.trim() || sendMutation.isPending) && styles.sendButtonDisabled]}
            disabled={!draft.trim() || sendMutation.isPending}
            onPress={() => sendMutation.mutate(draft.trim())}>
            <ThemedText style={styles.sendButtonText}>Send</ThemedText>
          </TouchableOpacity>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  loading: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  list: { padding: Spacing.three, gap: Spacing.two },
  header: { marginBottom: Spacing.two },
  bubbleRow: { maxWidth: '80%', alignSelf: 'flex-start' },
  bubbleRowRight: { alignSelf: 'flex-end', alignItems: 'flex-end' },
  bubble: { borderRadius: 14, paddingHorizontal: Spacing.three, paddingVertical: Spacing.two },
  bubbleTextSeller: { color: '#fff' },
  bubbleTime: { marginTop: 2 },
  inputRow: { flexDirection: 'row', gap: Spacing.two, padding: Spacing.three, borderTopWidth: 1, alignItems: 'flex-end' },
  input: { flex: 1, minHeight: 44, maxHeight: 120, borderRadius: 10, paddingHorizontal: Spacing.three, paddingVertical: Spacing.two, fontSize: 16 },
  sendButton: { height: 44, paddingHorizontal: Spacing.three, borderRadius: 10, backgroundColor: '#208AEF', alignItems: 'center', justifyContent: 'center' },
  sendButtonDisabled: { opacity: 0.5 },
  sendButtonText: { color: '#fff', fontWeight: '700' },
});
