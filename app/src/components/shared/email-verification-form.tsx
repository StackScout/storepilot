"use client";

import { useEffect, useRef, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2, MailCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { authService } from "@/services";
import type { AuthSession } from "@/services/auth.service";

interface EmailVerificationFormProps {
  email: string;
  /**
   * Held only in this component's React state (and the parent's, which
   * passed it down) — never persisted to localStorage/URL/a server field.
   * Needed to call login() ourselves once the code is confirmed, since
   * verifyEmail() deliberately doesn't sign the caller in (see backend
   * AuthController.register()'s doc comment).
   */
  password: string;
  /** Fires a resend on mount — used when a login attempt bounced here because the account wasn't verified yet, so there's no code waiting already. */
  autoSend?: boolean;
  onVerified: (session: AuthSession) => void;
}

/** Shared "enter the 6-digit code we emailed you" step, used by both the seller and buyer registration/login pages. */
export function EmailVerificationForm({ email, password, autoSend, onVerified }: EmailVerificationFormProps) {
  const [code, setCode] = useState("");
  const autoSentRef = useRef(false);

  const resendMutation = useMutation({
    mutationFn: () => authService.resendVerificationCode(email),
    onSuccess: () => toast.success("A new code was sent to your email."),
    onError: (error: Error) => toast.error(error.message || "Couldn't send a new code. Please try again."),
  });

  useEffect(() => {
    if (autoSend && !autoSentRef.current) {
      autoSentRef.current = true;
      resendMutation.mutate();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoSend]);

  const verifyMutation = useMutation({
    mutationFn: async () => {
      await authService.verifyEmail(email, code);
      return authService.login(email, password);
    },
    onSuccess: onVerified,
    onError: (error: Error) => toast.error(error.message || "Couldn't verify that code. Please try again."),
  });

  return (
    <div className="space-y-4">
      <div className="space-y-1 text-center">
        <span className="bg-primary/10 text-primary mx-auto flex size-12 items-center justify-center rounded-full">
          <MailCheck className="size-6" />
        </span>
        <p className="mt-2 text-sm">
          We sent a 6-digit code to <span className="font-medium">{email}</span>.
        </p>
      </div>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          verifyMutation.mutate();
        }}
        className="space-y-4"
      >
        <div className="space-y-1.5">
          <Label htmlFor="verification-code">Verification code</Label>
          <Input
            id="verification-code"
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            placeholder="123456"
            className="text-center text-lg tracking-[0.4em]"
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
          />
        </div>
        <Button type="submit" size="lg" className="w-full" disabled={code.length !== 6 || verifyMutation.isPending}>
          {verifyMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
          Verify email
        </Button>
      </form>

      <Button
        type="button"
        variant="ghost"
        size="sm"
        className="w-full"
        disabled={resendMutation.isPending}
        onClick={() => resendMutation.mutate()}
      >
        {resendMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
        Resend code
      </Button>
    </div>
  );
}
