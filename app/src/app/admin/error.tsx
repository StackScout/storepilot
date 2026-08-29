"use client";

import Link from "next/link";
import { useEffect } from "react";
import { Button } from "@/components/ui/button";
import { ErrorState } from "@/components/shared/error-state";

export default function AdminError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <div className="mx-auto flex min-h-[50vh] max-w-lg items-center justify-center px-4">
      <ErrorState
        description="Something went wrong loading this page. You can try again, or head back to the admin overview."
        onRetry={reset}
        action={
          <Button variant="ghost" render={<Link href="/admin" />}>
            Back to admin
          </Button>
        }
      />
    </div>
  );
}
