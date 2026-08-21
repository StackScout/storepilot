"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Download, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
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
import { authService, buyersService } from "@/services";

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
 * Buyer-facing "Danger zone" — export + delete, both self-service and
 * instant (no admin review). Delete requires typing the buyer's own email
 * to confirm, same friction level as GitHub's repo-delete pattern, since
 * this is genuinely irreversible: order/booking history is anonymized in
 * place, everything else (addresses, saved searches, wishlist, follows,
 * the Cognito identity itself) is permanently gone.
 */
export function DeleteAccountDialog({ email }: { email: string }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [confirmText, setConfirmText] = useState("");
  const [isExporting, setIsExporting] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);

  async function handleExport() {
    setIsExporting(true);
    try {
      const data = await buyersService.exportBuyerData();
      downloadJson(data, "storepilot-account-data.json");
    } catch {
      toast.error("Couldn't export your data. Please try again.");
    } finally {
      setIsExporting(false);
    }
  }

  async function handleDelete() {
    setIsDeleting(true);
    try {
      await buyersService.deleteBuyerAccount();
      // Best-effort — the Cognito session is already globally signed out
      // server-side as part of deletion, so this call may itself 401; the
      // cookies get cleared client-side regardless via the redirect below.
      await authService.logout().catch(() => undefined);
      queryClient.clear();
      toast.success("Your account has been deleted.");
      router.push("/");
    } catch {
      toast.error("Couldn't delete your account. Please try again.");
      setIsDeleting(false);
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-2">
      <Button type="button" variant="outline" size="sm" disabled={isExporting} onClick={handleExport}>
        {isExporting ? <Loader2 className="size-3.5 animate-spin" /> : <Download className="size-3.5" />}
        Export my data
      </Button>
      <Dialog
        onOpenChange={(open) => {
          if (!open) setConfirmText("");
        }}
      >
        <DialogTrigger render={<Button type="button" variant="outline" size="sm" className="text-destructive" />}>
          Delete account
        </DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete your account?</DialogTitle>
            <DialogDescription>
              This can&apos;t be undone. Your addresses, saved searches, wishlist, and follows will be
              permanently deleted. Your order and booking history will be kept for tax/accounting
              purposes, with your personal details removed from it.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-1.5">
            <Label htmlFor="delete-confirm-email">
              Type <span className="font-medium">{email}</span> to confirm
            </Label>
            <Input
              id="delete-confirm-email"
              value={confirmText}
              onChange={(e) => setConfirmText(e.target.value)}
              autoComplete="off"
            />
          </div>
          <DialogFooter>
            <DialogClose render={<Button variant="outline" />}>Keep account</DialogClose>
            <Button
              variant="destructive"
              disabled={confirmText !== email || isDeleting}
              onClick={handleDelete}
            >
              {isDeleting ? <Loader2 className="size-4 animate-spin" /> : null}
              Delete my account
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
