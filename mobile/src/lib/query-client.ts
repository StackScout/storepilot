import { QueryClient } from '@tanstack/react-query';

import { ApiError } from '@/lib/api-client';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: (failureCount, error) => {
        // Auth failures won't succeed on retry (api-client already tried
        // the refresh-and-retry once internally) — don't compound them.
        if (error instanceof ApiError && (error.status === 401 || error.status === 403)) return false;
        return failureCount < 2;
      },
    },
  },
});
