import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Alert, StyleSheet, TextInput, TouchableOpacity, View } from 'react-native';

import type { Review, ReviewInput } from '@storepilot/shared-api';

import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-client';
import { useAuthStore } from '@/store/auth-store';
import { formatDate } from '@/lib/format';

export function ReviewsSection({
  queryKey,
  listReviews,
  createReview,
}: {
  queryKey: unknown[];
  listReviews: () => Promise<Review[]>;
  createReview: (input: ReviewInput) => Promise<Review>;
}) {
  const theme = useTheme();
  const queryClient = useQueryClient();
  const isSignedIn = useAuthStore((s) => s.isSignedIn);
  const role = useAuthStore((s) => s.role);
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState('');

  const reviewsQuery = useQuery({ queryKey, queryFn: listReviews });

  const submitMutation = useMutation({
    mutationFn: () => createReview({ rating, comment: comment.trim() || undefined }),
    onSuccess: () => {
      setComment('');
      queryClient.invalidateQueries({ queryKey });
    },
    onError: (e) => Alert.alert('Could not submit review', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  const canReview = isSignedIn && role === 'buyer';

  return (
    <View style={styles.container}>
      <ThemedText type="smallBold" themeColor="textSecondary">
        REVIEWS
      </ThemedText>

      {canReview ? (
        <View style={styles.form}>
          <View style={styles.stars}>
            {[1, 2, 3, 4, 5].map((n) => (
              <TouchableOpacity key={n} onPress={() => setRating(n)}>
                <ThemedText style={{ fontSize: 22, color: n <= rating ? '#B98900' : theme.textSecondary }}>★</ThemedText>
              </TouchableOpacity>
            ))}
          </View>
          <TextInput
            style={[styles.input, { color: theme.text, backgroundColor: theme.backgroundElement }]}
            placeholder="Share your experience (optional)"
            placeholderTextColor={theme.textSecondary}
            value={comment}
            onChangeText={setComment}
            multiline
          />
          <TouchableOpacity style={styles.submitButton} disabled={submitMutation.isPending} onPress={() => submitMutation.mutate()}>
            <ThemedText style={styles.submitButtonText}>{submitMutation.isPending ? 'Submitting...' : 'Submit review'}</ThemedText>
          </TouchableOpacity>
        </View>
      ) : (
        <ThemedText type="small" themeColor="textSecondary">
          Sign in as a buyer to leave a review — only buyers with a completed order or booking can review.
        </ThemedText>
      )}

      {(reviewsQuery.data ?? []).length === 0 ? (
        <ThemedText type="small" themeColor="textSecondary">
          No reviews yet.
        </ThemedText>
      ) : (
        <View style={styles.reviewList}>
          {(reviewsQuery.data ?? []).map((review) => (
            <View key={review.id} style={[styles.review, { backgroundColor: theme.backgroundElement }]}>
              <View style={styles.reviewHeader}>
                <ThemedText type="smallBold">{review.buyerName}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {formatDate(review.createdAt)}
                </ThemedText>
              </View>
              <ThemedText style={{ color: '#B98900' }}>
                {'★'.repeat(review.rating)}
                {'☆'.repeat(5 - review.rating)}
              </ThemedText>
              {review.comment ? (
                <ThemedText type="small" style={styles.reviewComment}>
                  {review.comment}
                </ThemedText>
              ) : null}
            </View>
          ))}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: Spacing.two, paddingHorizontal: Spacing.three },
  form: { gap: Spacing.two },
  stars: { flexDirection: 'row', gap: Spacing.half },
  input: { minHeight: 60, borderRadius: 10, padding: Spacing.two, fontSize: 14, textAlignVertical: 'top' },
  submitButton: { height: 40, borderRadius: 10, backgroundColor: '#208AEF', alignItems: 'center', justifyContent: 'center' },
  submitButtonText: { color: '#fff', fontWeight: '600' },
  reviewList: { gap: Spacing.two },
  review: { borderRadius: 12, padding: Spacing.three, gap: 4 },
  reviewHeader: { flexDirection: 'row', justifyContent: 'space-between' },
  reviewComment: { marginTop: 2, lineHeight: 20 },
});
