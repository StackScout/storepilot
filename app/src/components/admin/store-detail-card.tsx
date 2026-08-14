import { Check, MapPin, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { StatusBadge, type StatusTone } from "@/components/shared/status-badge";
import { AbnVerificationBadge } from "@/components/shared/abn-verification-badge";
import { formatDate } from "@/lib/format";
import { getCategoryLabel } from "@/mock/categories";
import type { Store, StoreSettings, StoreVerificationStatus } from "@/types";

const STATUS_TONES: Record<StoreVerificationStatus, StatusTone> = {
  pending: "warning",
  active: "success",
  rejected: "danger",
};

const STATUS_LABEL: Record<StoreVerificationStatus, string> = {
  pending: "Pending",
  active: "Active",
  rejected: "Rejected",
};

/** Full store details for admin review — seller/business identity, bank account, verification documents. Used by both the pending-approval queue and the all-stores directory on /admin/stores. */
export function StoreDetailCard({
  store,
  settings,
  isSriLanka,
  showActions = false,
  onApprove,
  onReject,
  isApproving = false,
}: {
  store: Store;
  settings: StoreSettings | null;
  isSriLanka: boolean;
  showActions?: boolean;
  onApprove?: (storeId: string) => void;
  onReject?: (store: Store) => void;
  isApproving?: boolean;
}) {
  return (
    <div className="rounded-lg border p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <p className="font-medium">{store.name}</p>
            <Badge variant="secondary">{getCategoryLabel(store.category)}</Badge>
            <StatusBadge tone={STATUS_TONES[store.verificationStatus]}>
              {STATUS_LABEL[store.verificationStatus]}
            </StatusBadge>
          </div>
          <p className="text-muted-foreground flex items-center gap-1 text-xs">
            <MapPin className="size-3" /> {store.address.city}, {store.address.state}
            <span aria-hidden="true">·</span>
            Joined {formatDate(store.joinedAt)}
          </p>
        </div>
        {showActions ? (
          <div className="flex gap-2">
            <Button size="sm" variant="outline" className="text-destructive" onClick={() => onReject?.(store)}>
              <X className="size-3.5" /> Reject
            </Button>
            <Button size="sm" disabled={isApproving} onClick={() => onApprove?.(store.id)}>
              <Check className="size-3.5" /> Approve
            </Button>
          </div>
        ) : null}
      </div>
      <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-1 border-t pt-3 text-xs sm:grid-cols-4">
        <div>
          <dt className="text-muted-foreground">Seller type</dt>
          <dd className="font-medium capitalize">{settings?.sellerType ?? "—"}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">{isSriLanka ? "NIC no." : "Driver's licence no."}</dt>
          <dd className="font-medium">
            {(isSriLanka ? settings?.nicNumber : settings?.driverLicenceNumber) ?? "—"}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">{isSriLanka ? "Business reg. no." : "ABN"}</dt>
          <dd className="font-medium">
            {(isSriLanka ? settings?.businessRegistrationNumber : settings?.abn) ?? "—"}
          </dd>
          {!isSriLanka && settings?.abn ? <AbnVerificationBadge abn={settings.abn} /> : null}
        </div>
        <div>
          <dt className="text-muted-foreground">Bank account</dt>
          <dd className="font-medium">
            {settings ? `${settings.bankName} · ${settings.bankAccountNumber}` : "—"}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Contact</dt>
          <dd className="font-medium">{settings?.contactEmail ?? "—"}</dd>
          <dd className="text-muted-foreground">{settings?.contactPhone ?? ""}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">{isSriLanka ? "NIC document" : "Driver's licence document"}</dt>
          <dd className="font-medium">
            {(isSriLanka ? settings?.nicDocumentUrl : settings?.driverLicenceDocumentUrl) ? (
              <a
                href={(isSriLanka ? settings?.nicDocumentUrl : settings?.driverLicenceDocumentUrl)!}
                target="_blank"
                rel="noopener noreferrer"
                className="text-primary underline-offset-4 hover:underline"
              >
                View file
              </a>
            ) : (
              "—"
            )}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">{isSriLanka ? "Business reg. document" : "ABN document"}</dt>
          <dd className="font-medium">
            {(isSriLanka ? settings?.businessRegDocumentUrl : settings?.abnDocumentUrl) ? (
              <a
                href={(isSriLanka ? settings?.businessRegDocumentUrl : settings?.abnDocumentUrl)!}
                target="_blank"
                rel="noopener noreferrer"
                className="text-primary underline-offset-4 hover:underline"
              >
                View file
              </a>
            ) : (
              "—"
            )}
          </dd>
        </div>
        {store.verificationStatus === "rejected" && settings?.rejectionReason ? (
          <div className="col-span-2 sm:col-span-4">
            <dt className="text-muted-foreground">Rejection reason</dt>
            <dd className="text-destructive font-medium">{settings.rejectionReason}</dd>
          </div>
        ) : null}
      </dl>
    </div>
  );
}
