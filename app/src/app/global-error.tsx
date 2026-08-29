"use client";

import { useEffect } from "react";

/**
 * Catches an error thrown inside the root layout itself (very rare — a
 * regular error.tsx can't catch that, since it renders inside the layout
 * it's meant to protect). Must render its own <html>/<body> per Next.js's
 * convention, so it can't reuse ErrorState's rendering assumptions about an
 * existing document shell.
 */
export default function GlobalError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <html>
      <body>
        <div style={{ display: "flex", minHeight: "100vh", alignItems: "center", justifyContent: "center", padding: "1rem" }}>
          <div style={{ textAlign: "center" }}>
            <p style={{ fontWeight: 500, marginBottom: "0.5rem" }}>Something went wrong</p>
            <p style={{ color: "#666", marginBottom: "1rem" }}>An unexpected error occurred. Please try again.</p>
            <button
              onClick={reset}
              style={{ padding: "0.5rem 1rem", border: "1px solid #ccc", borderRadius: "0.375rem", background: "white", cursor: "pointer" }}
            >
              Try again
            </button>
          </div>
        </div>
      </body>
    </html>
  );
}
