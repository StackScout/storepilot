"use client";

import { useEffect, useRef } from "react";
import { toApiUrl } from "@/lib/api-client";

/**
 * Subscribes to a GET .../events SSE stream (see OrderController/
 * BookingController.subscribeToEvents) and calls onUpdate with each
 * "status" event's parsed payload. Pass null for [path] while the
 * order/booking hasn't loaded yet — the hook no-ops until it's a string.
 * [normalize]/[onUpdate] are read via refs, so callers can pass a fresh
 * inline closure every render (e.g. one that captures a TanStack Query
 * queryClient) without tearing down and reopening the connection — only a
 * change to [path] itself does that.
 */
export function useLiveStatus<T>(path: string | null, normalize: (raw: T) => T, onUpdate: (data: T) => void) {
  const normalizeRef = useRef(normalize);
  const onUpdateRef = useRef(onUpdate);
  useEffect(() => {
    normalizeRef.current = normalize;
    onUpdateRef.current = onUpdate;
  }, [normalize, onUpdate]);

  useEffect(() => {
    if (!path) return;
    const source = new EventSource(toApiUrl(path));
    function handleStatus(event: MessageEvent) {
      onUpdateRef.current(normalizeRef.current(JSON.parse(event.data) as T));
    }
    source.addEventListener("status", handleStatus);
    return () => source.close();
  }, [path]);
}
