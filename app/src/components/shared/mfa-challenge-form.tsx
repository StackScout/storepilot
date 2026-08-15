"use client";

import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2, ShieldCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { authService } from "@/services";
import type { AuthSession } from "@/services/auth.service";

interface MfaChallengeFormProps {
  email: string;
  /** Cognito's opaque session token from login()'s mfaRequired response — not a browser session, single-use for this one challenge. */
  session: string;
  onVerified: (session: AuthSession) => void;
}

/** Shared "enter your authenticator app code" step, shown when login() returns mfaRequired: true — used by both the seller and admin sign-in pages. */
export function MfaChallengeForm({ email, session, onVerified }: MfaChallengeFormProps) {
  const [code, setCode] = useState("");

  const mutation = useMutation({
    mutationFn: () => authService.verifyMfaChallenge(email, session, code),
    onSuccess: onVerified,
    onError: (error: Error) => toast.error(error.message || "Invalid code. Please try again."),
  });

  return (
    <div className="space-y-4">
      <div className="space-y-1 text-center">
        <span className="bg-primary/10 text-primary mx-auto flex size-12 items-center justify-center rounded-full">
          <ShieldCheck className="size-6" />
        </span>
        <p className="mt-2 text-sm">Enter the 6-digit code from your authenticator app.</p>
      </div>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          mutation.mutate();
        }}
        className="space-y-4"
      >
        <div className="space-y-1.5">
          <Label htmlFor="mfa-code">Authentication code</Label>
          <Input
            id="mfa-code"
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            placeholder="123456"
            className="text-center text-lg tracking-[0.4em]"
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
            autoFocus
          />
        </div>
        <Button type="submit" size="lg" className="w-full" disabled={code.length !== 6 || mutation.isPending}>
          {mutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
          Verify
        </Button>
      </form>
    </div>
  );
}
