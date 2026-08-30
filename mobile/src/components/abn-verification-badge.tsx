import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { ActivityIndicator, View } from 'react-native';

import { lookupAbn } from '@/api/abn';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';

const DEBOUNCE_MS = 500;

/** ABR's own check-digit algorithm expects exactly 11 digits — a shorter value is just a still-typing ABN, not an error. */
function digitCount(abn: string): number {
  return abn.replace(/\D/g, '').length;
}

/**
 * Live ABR "ABN Lookup" result for whatever ABN is currently typed —
 * debounced, and only fires once 11 digits are present. Mirrors the web
 * app's AbnVerificationBadge exactly (same debounce, same status states).
 */
export function AbnVerificationBadge({ abn }: { abn: string }) {
  const [debouncedAbn, setDebouncedAbn] = useState(abn);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedAbn(abn), DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [abn]);

  const ready = digitCount(debouncedAbn) === 11;

  const { data, isFetching } = useQuery({
    queryKey: ['abn-lookup', debouncedAbn],
    queryFn: () => lookupAbn(debouncedAbn),
    enabled: ready,
    staleTime: 5 * 60_000,
  });

  if (!ready) return null;

  if (isFetching) {
    return (
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.one }}>
        <ActivityIndicator size="small" />
        <ThemedText type="small" themeColor="textSecondary">
          Checking ABN...
        </ThemedText>
      </View>
    );
  }

  if (!data || data.status === 'not-configured') return null;

  if (data.status === 'found') {
    const isActive = !data.abnStatus || data.abnStatus.toLowerCase() === 'active';
    return (
      <ThemedText type="small" style={{ color: isActive ? '#1E9E5A' : '#B98900' }}>
        {isActive ? '✓' : '⚠'} Registered to {data.entityName}
        {data.abnStatus ? ` — ${data.abnStatus}` : ''}
      </ThemedText>
    );
  }

  if (data.status === 'invalid-format') {
    return (
      <ThemedText type="small" style={{ color: '#D64545' }}>
        ⚠ That doesn&apos;t look like a valid ABN — check the digits.
      </ThemedText>
    );
  }

  if (data.status === 'not-found') {
    return (
      <ThemedText type="small" style={{ color: '#B98900' }}>
        ⚠ No matching ABN found in the register — double-check the number.
      </ThemedText>
    );
  }

  // "error" — a transient ABR/network issue, not the seller's fault. Stay quiet rather than alarm them.
  return null;
}
