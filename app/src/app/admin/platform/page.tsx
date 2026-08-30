"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { getPlatformConfig, type PlatformConfig } from "@/lib/platform-config";
import { platformConfigService } from "@/services";

const ADMIN_PLATFORM_CONFIG_QUERY_KEY = ["admin", "platform-config"];

/**
 * This deployment's own settings (see backend PlatformSettings' doc
 * comment) — separate from usePlatformConfig()'s context-based hook,
 * which only exists inside the marketplace layout and is populated once
 * server-side, not refetchable from here.
 */
export default function AdminPlatformSettingsPage() {
  const { data: config, isLoading } = useQuery({
    queryKey: ADMIN_PLATFORM_CONFIG_QUERY_KEY,
    queryFn: getPlatformConfig,
  });

  return (
    <div className="max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Platform settings</h1>
        <p className="text-muted-foreground text-sm">
          Which payment methods this deployment offers at all — checkout only ever shows a method
          that&apos;s both enabled here and enabled on the individual seller&apos;s own store. Turning off
          every method but one here removes the payment-method choice from checkout entirely; buyers just
          use that one.
        </p>
      </div>

      <Card>
        <CardContent className="space-y-4">
          <h2 className="font-semibold">Payment methods</h2>
          {isLoading || !config ? <Loader2 className="text-muted-foreground size-5 animate-spin" /> : <PaymentMethodsForm config={config} />}
        </CardContent>
      </Card>

      <Card>
        <CardContent className="space-y-4">
          <h2 className="font-semibold">Seller plans</h2>
          <p className="text-muted-foreground text-xs">
            Turn off for a deployment that doesn&apos;t use tiered seller plans at all — every Pro-only
            feature (booking analytics, and Cash on Delivery/Bank transfer if this deployment offers
            them) becomes free for every seller, and every Pro-plan UI (onboarding&apos;s plan picker,
            the billing page, sidebar badges) disappears everywhere.
          </p>
          {isLoading || !config ? <Loader2 className="text-muted-foreground size-5 animate-spin" /> : <ProPlanForm config={config} />}
        </CardContent>
      </Card>
    </div>
  );
}

/** Mounted only once `config` has loaded, so its local state can be seeded directly from props — no effect needed to sync it in after the fact. */
function PaymentMethodsForm({ config }: { config: PlatformConfig }) {
  const queryClient = useQueryClient();
  const [codEnabled, setCodEnabled] = useState(config.defaultCodEnabled);
  const [onlinePaymentEnabled, setOnlinePaymentEnabled] = useState(config.defaultOnlinePaymentEnabled);
  const [bankTransferEnabled, setBankTransferEnabled] = useState(config.defaultBankTransferEnabled);

  const mutation = useMutation({
    mutationFn: () => platformConfigService.updatePaymentMethods({ codEnabled, onlinePaymentEnabled, bankTransferEnabled }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ADMIN_PLATFORM_CONFIG_QUERY_KEY });
      toast.success("Payment methods updated — other open tabs pick this up on their next reload.");
    },
    onError: () => toast.error("Couldn't save payment methods. Try again."),
  });

  const noMethodsSelected = !codEnabled && !onlinePaymentEnabled && !bankTransferEnabled;
  const unchanged =
    codEnabled === config.defaultCodEnabled &&
    onlinePaymentEnabled === config.defaultOnlinePaymentEnabled &&
    bankTransferEnabled === config.defaultBankTransferEnabled;

  return (
    <>
      <label className="flex items-start gap-2.5 text-sm">
        <Checkbox checked={onlinePaymentEnabled} onCheckedChange={(checked) => setOnlinePaymentEnabled(checked === true)} className="mt-0.5" />
        <span>
          <span className="block font-medium">Online payment</span>
          <span className="text-muted-foreground block text-xs">
            Stripe or PayHere, whichever this deployment is configured for.
          </span>
        </span>
      </label>
      <label className="flex items-start gap-2.5 text-sm">
        <Checkbox checked={codEnabled} onCheckedChange={(checked) => setCodEnabled(checked === true)} className="mt-0.5" />
        <span className="block font-medium">Cash on delivery</span>
      </label>
      <label className="flex items-start gap-2.5 text-sm">
        <Checkbox checked={bankTransferEnabled} onCheckedChange={(checked) => setBankTransferEnabled(checked === true)} className="mt-0.5" />
        <span className="block font-medium">Bank transfer</span>
      </label>
      {noMethodsSelected ? (
        <p className="text-destructive text-xs">
          At least one payment method must stay enabled, or no store on this deployment could ever check
          out a buyer.
        </p>
      ) : null}
      <Button onClick={() => mutation.mutate()} disabled={mutation.isPending || noMethodsSelected || unchanged}>
        {mutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
        Save
      </Button>
    </>
  );
}

/** Mounted only once `config` has loaded — same reasoning as PaymentMethodsForm above. */
function ProPlanForm({ config }: { config: PlatformConfig }) {
  const queryClient = useQueryClient();
  const [proPlanEnabled, setProPlanEnabled] = useState(config.proPlanEnabled);

  const mutation = useMutation({
    mutationFn: () => platformConfigService.updateProPlanEnabled(proPlanEnabled),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ADMIN_PLATFORM_CONFIG_QUERY_KEY });
      toast.success("Seller plans updated — other open tabs pick this up on their next reload.");
    },
    onError: () => toast.error("Couldn't save. Try again."),
  });

  return (
    <>
      <label className="flex items-start gap-2.5 text-sm">
        <Checkbox checked={proPlanEnabled} onCheckedChange={(checked) => setProPlanEnabled(checked === true)} className="mt-0.5" />
        <span className="block font-medium">This deployment uses Free/Pro seller plans</span>
      </label>
      <Button onClick={() => mutation.mutate()} disabled={mutation.isPending || proPlanEnabled === config.proPlanEnabled}>
        {mutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
        Save
      </Button>
    </>
  );
}
