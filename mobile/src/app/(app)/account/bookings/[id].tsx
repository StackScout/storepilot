import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as ImagePicker from 'expo-image-picker';
import { Stack, useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, Alert, ScrollView, StyleSheet, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { cancelBooking, getBookingById, uploadBookingReceipt } from '@/api/buyer-bookings';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-client';
import { bookingStatusColor, bookingStatusLabel, formatDateTime } from '@/lib/format';
import { formatCurrency, usePlatformConfig } from '@/lib/platform-config';

export default function BookingDetailScreen() {
  const theme = useTheme();
  const platformConfig = usePlatformConfig();
  const queryClient = useQueryClient();
  const { id } = useLocalSearchParams<{ id: string }>();

  const bookingQuery = useQuery({ queryKey: ['booking', id], queryFn: () => getBookingById(id!), enabled: !!id });
  const booking = bookingQuery.data;

  const uploadMutation = useMutation({
    mutationFn: (uri: string) => uploadBookingReceipt(id!, uri),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['booking', id] }),
    onError: (e) => Alert.alert('Could not upload receipt', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  const cancelMutation = useMutation({
    mutationFn: () => cancelBooking(id!),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['booking', id] }),
    onError: (e) => Alert.alert('Could not cancel booking', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  const pickAndUploadReceipt = async () => {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) return;
    const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.8 });
    if (result.canceled || !result.assets[0]) return;
    uploadMutation.mutate(result.assets[0].uri);
  };

  if (bookingQuery.isLoading || !booking) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: theme.background, alignItems: 'center', justifyContent: 'center' }}>
        <ActivityIndicator />
      </SafeAreaView>
    );
  }

  const canCancel = booking.status === 'pending' || booking.status === 'confirmed';
  const needsReceipt = booking.paymentMethod === 'bank-transfer' && !booking.receiptUrl && booking.status !== 'cancelled';

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <Stack.Screen options={{ title: booking.bookingNumber }} />
      <ScrollView contentContainerStyle={styles.container}>
        <View style={styles.headerRow}>
          <ThemedText type="title" style={styles.bookingNumber}>
            {booking.bookingNumber}
          </ThemedText>
          <ThemedText style={{ color: bookingStatusColor(booking.status) }}>{bookingStatusLabel(booking.status)}</ThemedText>
        </View>
        <ThemedText type="small" themeColor="textSecondary">
          {booking.storeName}
        </ThemedText>

        <ThemedText type="smallBold" style={styles.sectionLabel}>
          {booking.serviceName}
        </ThemedText>
        <ThemedText type="small" themeColor="textSecondary">
          {formatDateTime(booking.scheduledStart)}
        </ThemedText>
        <ThemedText type="smallBold">{formatCurrency(booking.total, platformConfig)}</ThemedText>

        {needsReceipt ? (
          <TouchableOpacity style={styles.uploadButton} disabled={uploadMutation.isPending} onPress={pickAndUploadReceipt}>
            <ThemedText style={styles.uploadButtonText}>{uploadMutation.isPending ? 'Uploading...' : 'Upload payment receipt'}</ThemedText>
          </TouchableOpacity>
        ) : null}

        {canCancel ? (
          <TouchableOpacity
            style={[styles.cancelButton, { borderColor: '#D64545' }]}
            disabled={cancelMutation.isPending}
            onPress={() => Alert.alert('Cancel booking?', 'This cannot be undone.', [{ text: 'No' }, { text: 'Yes, cancel', style: 'destructive', onPress: () => cancelMutation.mutate() }])}>
            <ThemedText style={{ color: '#D64545' }}>Cancel booking</ThemedText>
          </TouchableOpacity>
        ) : null}

        <ThemedText type="smallBold" themeColor="textSecondary" style={styles.sectionLabel}>
          TIMELINE
        </ThemedText>
        {booking.timeline.map((entry, i) => (
          <View key={i} style={styles.timelineEntry}>
            <ThemedText type="small">{entry.label}</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {formatDateTime(entry.timestamp)}
            </ThemedText>
          </View>
        ))}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { padding: Spacing.three, gap: Spacing.two, paddingBottom: Spacing.six },
  headerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  bookingNumber: { fontSize: 20, lineHeight: 26 },
  sectionLabel: { marginTop: Spacing.three },
  uploadButton: { height: 44, borderRadius: 10, backgroundColor: '#208AEF', alignItems: 'center', justifyContent: 'center', marginTop: Spacing.two },
  uploadButtonText: { color: '#fff', fontWeight: '600' },
  cancelButton: { height: 44, borderRadius: 10, borderWidth: 1, alignItems: 'center', justifyContent: 'center', marginTop: Spacing.two },
  timelineEntry: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 4 },
});
