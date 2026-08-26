import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as WebBrowser from 'expo-web-browser';
import { ActivityIndicator, Alert, StyleSheet, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { cancelBillingAtPeriodEnd, getSellerPlan, refreshBillingFromStripe, startBillingCheckout } from '@/api/billing';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { ApiError } from '@/lib/api-client';
import { formatDate, formatMoney } from '@/lib/format';

export default function BillingScreen() {
  const theme = useTheme();
  const queryClient = useQueryClient();

  const planQuery = useQuery({ queryKey: ['me', 'seller', 'plan'], queryFn: getSellerPlan });

  const upgradeMutation = useMutation({
    mutationFn: startBillingCheckout,
    onSuccess: async (data) => {
      await WebBrowser.openBrowserAsync(data.checkoutUrl);
      // The Stripe redirect always lands on the web dashboard, never back into this app — force a
      // resync from Stripe once the browser closes in case the webhook hasn't landed yet.
      try {
        await refreshBillingFromStripe();
      } finally {
        queryClient.invalidateQueries({ queryKey: ['me', 'seller', 'plan'] });
      }
    },
    onError: (e) => Alert.alert('Could not start checkout', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  const cancelMutation = useMutation({
    mutationFn: cancelBillingAtPeriodEnd,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['me', 'seller', 'plan'] }),
    onError: (e) => Alert.alert('Could not cancel', e instanceof ApiError ? e.message : 'Please try again.'),
  });

  if (planQuery.isLoading || !planQuery.data) {
    return (
      <SafeAreaView style={{ flex: 1, backgroundColor: theme.background, alignItems: 'center', justifyContent: 'center' }}>
        <ActivityIndicator />
      </SafeAreaView>
    );
  }

  const plan = planQuery.data;

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.background }} edges={['bottom']}>
      <View style={styles.container}>
        <ThemedView type="backgroundElement" style={styles.card}>
          <ThemedText type="smallBold" themeColor="textSecondary">
            CURRENT PLAN
          </ThemedText>
          <ThemedText type="subtitle" style={styles.planName}>
            {plan.plan === 'pro' ? 'Pro' : 'Free'}
          </ThemedText>
          {plan.plan === 'pro' ? (
            <ThemedText type="small" themeColor="textSecondary">
              {plan.cancelAtPeriodEnd
                ? `Cancels on ${plan.currentPeriodEnd ? formatDate(plan.currentPeriodEnd) : 'period end'}`
                : plan.currentPeriodEnd
                  ? `Renews ${formatDate(plan.currentPeriodEnd)}`
                  : null}
            </ThemedText>
          ) : (
            <ThemedText type="small" themeColor="textSecondary">
              {formatMoney(plan.monthlyPriceCents)} {plan.currencyCode} / month for Pro
            </ThemedText>
          )}
        </ThemedView>

        {plan.plan === 'free' ? (
          <TouchableOpacity style={[styles.button, styles.upgradeButton]} onPress={() => upgradeMutation.mutate()} disabled={upgradeMutation.isPending}>
            {upgradeMutation.isPending ? <ActivityIndicator color="#fff" /> : <ThemedText style={styles.upgradeText}>Upgrade to Pro</ThemedText>}
          </TouchableOpacity>
        ) : plan.cancelAtPeriodEnd ? null : (
          <TouchableOpacity style={[styles.button, styles.cancelButton, { borderColor: theme.textSecondary }]} onPress={() => cancelMutation.mutate()} disabled={cancelMutation.isPending}>
            {cancelMutation.isPending ? <ActivityIndicator /> : <ThemedText themeColor="textSecondary">Cancel at period end</ThemedText>}
          </TouchableOpacity>
        )}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { padding: Spacing.three, gap: Spacing.three },
  card: { borderRadius: 16, padding: Spacing.three, gap: Spacing.half },
  planName: { fontSize: 28, lineHeight: 34 },
  button: { height: 50, borderRadius: 12, alignItems: 'center', justifyContent: 'center' },
  upgradeButton: { backgroundColor: '#208AEF' },
  upgradeText: { color: '#fff', fontWeight: '700' },
  cancelButton: { borderWidth: 1 },
});
