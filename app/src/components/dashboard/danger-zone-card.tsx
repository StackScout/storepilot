"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Download, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { ApiRequestError } from "@/lib/api-client";
import { authService, sellerAccountService, storesService } from "@/services";
import type { StoreVerificationStatus } from "@/types";

/** Triggers a browser download of [data] as a formatted JSON file — no backend round trip, the export endpoint already returned the full bundle. */
function downloadJson(data: unknown, filename: string) {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

/**
 * Seller-facing "Danger zone" — closing the store and deleting the account
 * are two separate, sequential, explicit actions, not one button. This is
 * the pause point + safety mechanism in place of an admin reviewer: the
 * backend refuses account deletion until the store is already `closed`,
 * and refuses to close a store with anything still in flight or owed
 * either direction (surfaced below as the specific blocking reason from
 * the 409 response, not a generic error).
 */
export function DangerZoneCard({
  storeId,
  storeName,
  verificationStatus,
}: {
  storeId: string | null;
  storeName: string | null;
  verificationStatus: StoreVerificationStatus | null;
}) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [isExporting, setIsExporting] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [confirmText, setConfirmText] = useState("");

  const { data: session } = useQuery({
    queryKey: ["auth-session"],
    queryFn: () => authService.getSession(),
  });
  const sellerEmail = session?.email ?? "";

  const isClosed = verificationStatus === "closed";
  const canDelete = !storeId || isClosed;

  const closeMutation = useMutation({
    mutationFn: () => storesService.closeStore(storeId as string),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["store", storeId] });
      toast.success("Store closed.");
    },
    onError: (error) => {
      const message =
        error instanceof ApiRequestError ? error.message : "Couldn't close the store. Please try again.";
      toast.error(message);
    },
  });

  async function handleExport() {
    setIsExporting(true);
    try {
      const data = await sellerAccountService.exportSellerData();
      downloadJson(data, "storepilot-seller-data.json");
    } catch {
      toast.error("Couldn't export your data. Please try again.");
    } finally {
      setIsExporting(false);
    }
  }

  async function handleDelete() {
    setIsDeleting(true);
    try {
      await sellerAccountService.deleteSellerAccount();
      // Best-effort — the Cognito session is already globally signed out
      // server-side as part of deletion, so this call may itself 401; the
      // cookies get cleared client-side regardless via the redirect below.
      await authService.logout().catch(() => undefined);
      queryClient.clear();
      toast.success("Your seller account has been deleted.");
      router.push("/");
    } catch {
      toast.error("Couldn't delete your account. Please try again.");
      setIsDeleting(false);
    }
  }

  return (
    <Card>
      <CardContent className="space-y-4">
        <div>
          <h2 className="font-semibold">Danger zone</h2>
          <p className="text-muted-foreground text-sm">
            Export your data, close your store, or permanently delete your seller account.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Button type="button" variant="outline" size="sm" disabled={isExporting} onClick={handleExport}>
            {isExporting ? <Loader2 className="size-3.5 animate-spin" /> : <Download className="size-3.5" />}
            Export my data
          </Button>

          {storeId && !isClosed ? (
            <Dialog>
              <DialogTrigger render={<Button type="button" variant="outline" size="sm" className="text-destructive" />}>
                Close store
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>Close {storeName ?? "your store"}?</DialogTitle>
                  <DialogDescription>
                    This is permanent — your store will drop out of the marketplace immediately. Past
                    orders and buyers will still see your store&apos;s name in their order history. You
                    can&apos;t reopen a closed store.
                  </DialogDescription>
                </DialogHeader>
                <DialogFooter>
                  <DialogClose render={<Button variant="outline" />}>Keep store open</DialogClose>
                  <Button variant="destructive" disabled={closeMutation.isPending} onClick={() => closeMutation.mutate()}>
                    {closeMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
                    Yes, close store
                  </Button>
                </DialogFooter>
              </DialogContent>
            </Dialog>
          ) : null}

          <Dialog
            onOpenChange={(open) => {
              if (!open) setConfirmText("");
            }}
          >
            <DialogTrigger
              render={<Button type="button" variant="outline" size="sm" className="text-destructive" disabled={!canDelete} />}
            >
              Delete account
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Delete your seller account?</DialogTitle>
                <DialogDescription>
                  This can&apos;t be undone. Your Pro subscription will be cancelled, your Stripe Connect
                  account disconnected, and your store settings permanently redacted. Your store and order
                  history will be kept for tax/accounting purposes, with your personal details removed.
                </DialogDescription>
              </DialogHeader>
              <div className="space-y-1.5">
                <Label htmlFor="delete-seller-confirm-email">
                  Type <span className="font-medium">{sellerEmail}</span> to confirm
                </Label>
                <Input
                  id="delete-seller-confirm-email"
                  value={confirmText}
                  onChange={(e) => setConfirmText(e.target.value)}
                  autoComplete="off"
                />
              </div>
              <DialogFooter>
                <DialogClose render={<Button variant="outline" />}>Keep account</DialogClose>
                <Button
                  variant="destructive"
                  disabled={confirmText !== sellerEmail || !sellerEmail || isDeleting}
                  onClick={handleDelete}
                >
                  {isDeleting ? <Loader2 className="size-4 animate-spin" /> : null}
                  Delete my account
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>

          {storeId && !isClosed ? (
            <p className="text-muted-foreground w-full text-xs">Close your store before deleting your account.</p>
          ) : null}
        </div>
      </CardContent>
    </Card>
  );
}
