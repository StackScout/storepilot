"use client";

import { useState } from "react";
import QRCode from "qrcode";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2, ShieldCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import { authService } from "@/services";

/**
 * Self-service TOTP enroll/disable card — works for any signed-in role
 * (seller or admin), since the backend endpoints act on whichever access
 * token cookie is present, not a specific role. Opt-in only: nothing here
 * forces MFA on, matching the product decision to keep it voluntary for
 * both roles (see docs/feature-epics.md's Should-have backlog).
 */
export function MfaSettingsCard() {
  const queryClient = useQueryClient();
  const [enrolling, setEnrolling] = useState(false);
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null);
  const [secret, setSecret] = useState<string | null>(null);
  const [code, setCode] = useState("");

  const { data: status, isLoading } = useQuery({
    queryKey: ["mfa-status"],
    queryFn: () => authService.getMfaStatus(),
  });

  const setupMutation = useMutation({
    mutationFn: () => authService.setupMfa(),
    onSuccess: async ({ secret: newSecret, otpauthUri }) => {
      setSecret(newSecret);
      setQrDataUrl(await QRCode.toDataURL(otpauthUri));
      setEnrolling(true);
    },
    onError: () => toast.error("Couldn't start setup. Please try again."),
  });

  const verifyMutation = useMutation({
    mutationFn: () => authService.verifyMfaSetup(code),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["mfa-status"] });
      toast.success("Two-factor authentication is on");
      setEnrolling(false);
      setQrDataUrl(null);
      setSecret(null);
      setCode("");
    },
    onError: (error: Error) => toast.error(error.message || "Invalid code. Please try again."),
  });

  const disableMutation = useMutation({
    mutationFn: () => authService.disableMfa(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["mfa-status"] });
      toast.success("Two-factor authentication is off");
    },
    onError: () => toast.error("Couldn't turn this off. Please try again."),
  });

  // Cancelling mid-setup invalidates the unconfirmed secret server-side —
  // nothing to clean up here beyond resetting local UI state.
  function cancelEnrollment() {
    setEnrolling(false);
    setQrDataUrl(null);
    setSecret(null);
    setCode("");
  }

  return (
    <Card>
      <CardContent className="space-y-4">
        <div className="flex items-center gap-2">
          <ShieldCheck className="text-muted-foreground size-4" />
          <h2 className="font-semibold">Two-factor authentication</h2>
        </div>

        {isLoading ? (
          <Loader2 className="text-muted-foreground size-4 animate-spin" />
        ) : enrolling ? (
          <div className="space-y-4">
            <p className="text-muted-foreground text-sm">
              Scan this QR code with an authenticator app (Google Authenticator, Authy, 1Password,
              etc.), then enter the 6-digit code it shows to confirm.
            </p>
            {qrDataUrl ? (
              // eslint-disable-next-line @next/next/no-img-element -- a locally-generated data: URI, not a remote image next/image would optimize
              <img src={qrDataUrl} alt="Scan with your authenticator app" className="size-48 rounded-md border" />
            ) : null}
            {secret ? (
              <p className="text-muted-foreground text-xs">
                Can&apos;t scan it? Enter this code manually:{" "}
                <span className="font-mono font-medium">{secret}</span>
              </p>
            ) : null}
            <div className="space-y-1.5">
              <Label htmlFor="mfa-setup-code">6-digit code</Label>
              <Input
                id="mfa-setup-code"
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={6}
                placeholder="123456"
                className="w-40 text-center tracking-[0.3em]"
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
              />
            </div>
            <div className="flex gap-2">
              <Button
                type="button"
                disabled={code.length !== 6 || verifyMutation.isPending}
                onClick={() => verifyMutation.mutate()}
              >
                {verifyMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
                Confirm and enable
              </Button>
              <Button type="button" variant="ghost" onClick={cancelEnrollment}>
                Cancel
              </Button>
            </div>
          </div>
        ) : status?.enabled ? (
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm">
              <span className="font-medium">Enabled</span> — you&apos;ll be asked for a code from your
              authenticator app each time you sign in.
            </p>
            <Button
              type="button"
              variant="outline"
              className="text-destructive"
              disabled={disableMutation.isPending}
              onClick={() => disableMutation.mutate()}
            >
              {disableMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
              Turn off
            </Button>
          </div>
        ) : (
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-muted-foreground text-sm">
              Add an extra step at sign-in using an authenticator app — optional, but recommended.
            </p>
            <Button type="button" disabled={setupMutation.isPending} onClick={() => setupMutation.mutate()}>
              {setupMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
              Enable
            </Button>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
