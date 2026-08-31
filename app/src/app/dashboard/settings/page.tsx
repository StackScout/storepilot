"use client";

import { Suspense, useEffect, useMemo, useRef, useState } from "react";
import Image from "next/image";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSearchParams } from "next/navigation";
import { toast } from "sonner";
import { FileText, Loader2, Lock, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import { StatusBadge } from "@/components/shared/status-badge";
import { Checkbox } from "@/components/ui/checkbox";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { StoreLogoFallback, StoreBannerFallback } from "@/components/shared/store-image-fallback";
import { MfaSettingsCard } from "@/components/shared/mfa-settings-card";
import { DangerZoneCard } from "@/components/dashboard/danger-zone-card";
import { StaffManagementCard } from "@/components/dashboard/staff-management-card";
import { EmptyState } from "@/components/shared/empty-state";
import { cn } from "@/lib/utils";
import { useSellerRole, useSellerStoreId } from "@/hooks/use-seller-store";
import { usePlatformConfig } from "@/hooks/use-platform-config";
import { formatCurrency } from "@/lib/currency";
import { formatDate, formatDateTime } from "@/lib/format";
import { storesService, billingService } from "@/services";
import type { SellerType } from "@/types";

const urlOrEmpty = z
  .string()
  .trim()
  .refine((v) => v === "" || z.string().url().safeParse(v).success, "Enter a valid URL");

/**
 * Bank details are only meaningful when this deployment offers cod or
 * bank-transfer at all (see PlatformSettings' default*Enabled doc comment
 * on the backend) — required when it does, dropped entirely otherwise, so
 * a seller on a Stripe-only deployment (who was never asked for them at
 * onboarding either — see onboarding/page.tsx) isn't blocked from saving
 * any other settings change by an empty required field for data nobody
 * reads.
 */
function buildSettingsSchema(needsBankDetails: boolean) {
  return z
    .object({
      contactEmail: z.string().email("Enter a valid email"),
      contactPhone: z.string().min(9, "Enter a valid phone number"),
      bankName: z.string().optional(),
      bankAccountName: z.string().optional(),
      bankAccountNumber: z.string().optional(),
      codEnabled: z.boolean(),
      onlinePaymentEnabled: z.boolean(),
      bankTransferEnabled: z.boolean(),
      stripeEnabled: z.boolean(),
      stockManagementEnabled: z.boolean(),
      pickupEnabled: z.boolean(),
      bookingsEnabled: z.boolean(),
      gstRegistered: z.boolean(),
      facebookUrl: urlOrEmpty,
      instagramUrl: urlOrEmpty,
      tiktokUrl: urlOrEmpty,
    })
    .refine(
      (data) => data.codEnabled || data.onlinePaymentEnabled || data.bankTransferEnabled || data.stripeEnabled,
      {
        message: "Enable at least one payment method so buyers can check out",
        path: ["bankTransferEnabled"],
      },
    )
    .superRefine((data, ctx) => {
      if (!needsBankDetails) return;
      if (!data.bankName || data.bankName.trim().length < 2) {
        ctx.addIssue({ code: "custom", path: ["bankName"], message: "Enter a bank name" });
      }
      if (!data.bankAccountName || data.bankAccountName.trim().length < 2) {
        ctx.addIssue({ code: "custom", path: ["bankAccountName"], message: "Enter the account holder name" });
      }
      if (!data.bankAccountNumber || data.bankAccountNumber.trim().length < 4) {
        ctx.addIssue({ code: "custom", path: ["bankAccountNumber"], message: "Enter the account number" });
      }
    });
}

type SettingsFormValues = z.infer<ReturnType<typeof buildSettingsSchema>>;

const changeRequestSchema = z.object({
  sellerType: z.enum(["individual", "business"]),
  driverLicenceNumber: z.string().optional(),
  abn: z.string().optional(),
  nicNumber: z.string().optional(),
  businessRegistrationNumber: z.string().optional(),
});

type ChangeRequestFormValues = z.infer<typeof changeRequestSchema>;

export default function DashboardSettingsPage() {
  return (
    <Suspense>
      <DashboardSettingsForm />
    </Suspense>
  );
}

function DashboardSettingsForm() {
  const role = useSellerRole();
  if (role === "staff") {
    return (
      <div className="mx-auto max-w-2xl px-4 py-16 sm:px-6 lg:px-8">
        <EmptyState
          icon={Lock}
          title="Store settings are managed by the store owner"
          description="Financial and account details here (bank info, payouts, billing) are only visible to the store owner."
        />
      </div>
    );
  }

  return <OwnerSettingsForm />;
}

/** Split out from DashboardSettingsForm so the staff early-return above never has to reason about hook-count consistency against this component's much larger hook list. */
function OwnerSettingsForm() {
  const queryClient = useQueryClient();
  const storeId = useSellerStoreId();
  const {
    countryCode,
    currencyCode,
    currencySymbol,
    currencyLocale,
    proPlanEnabled,
    defaultCodEnabled: platformCodEnabled,
    defaultBankTransferEnabled: platformBankTransferEnabled,
  } = usePlatformConfig();
  const currency = { code: currencyCode, symbol: currencySymbol, locale: currencyLocale };
  const isSriLanka = countryCode === "LK";
  // PayHere and Stripe are each temporarily restricted to their home
  // market — see the "Stripe Connect" card and "Payment methods" section
  // below, and checkout-form.tsx's matching gate on the buyer side.
  const isAustralia = countryCode === "AU";
  const searchParams = useSearchParams();
  const needsBankDetails = platformCodEnabled || platformBankTransferEnabled;
  const settingsSchema = useMemo(() => buildSettingsSchema(needsBankDetails), [needsBankDetails]);

  const { data: settings, isLoading: isSettingsLoading } = useQuery({
    queryKey: ["store-settings", storeId],
    queryFn: () => storesService.getStoreSettings(storeId),
    // Always refetch on mount — this page is where the seller lands back
    // after Stripe's hosted onboarding flow, and we need the just-updated
    // connection status, not a stale cached value.
    refetchOnMount: "always",
  });

  const { data: store, isLoading: isStoreLoading } = useQuery({
    queryKey: ["store", storeId],
    queryFn: () => storesService.getStoreById(storeId),
  });
  const isStoreActive = store?.verificationStatus === "active";

  // Once a store is approved, its verification-identity fields (seller
  // type, ABN/licence number, NIC/business-reg number, and their
  // documents) can no longer be edited directly — see StoreService
  // .upsertSettings' doc comment. This query drives the "Verification
  // details" card below, which is the only remaining way to touch those
  // fields on an active store.
  const { data: pendingChangeRequest } = useQuery({
    queryKey: ["verification-change-request", storeId],
    queryFn: () => storesService.getCurrentVerificationChangeRequest(storeId),
    enabled: isStoreActive,
  });

  const { data: planInfo, isLoading: isPlanLoading } = useQuery({
    queryKey: ["seller-plan"],
    queryFn: () => billingService.getMyPlan(),
    refetchOnMount: "always",
  });
  // On a deployment with no Pro tier concept at all (see
  // PlatformSettings.proPlanEnabled's doc comment), every seller is
  // treated as if they were Pro — the Plan card disappears entirely below,
  // and every other isPro check here (COD/bank-transfer checkboxes, "Pro"
  // badges) is naturally moot since there's no Free tier to distinguish
  // from.
  const isPro = !proPlanEnabled || planInfo?.plan === "pro";

  const isLoading = isSettingsLoading || isStoreLoading || isPlanLoading;

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    reset,
    formState: { errors },
  } = useForm<SettingsFormValues>({
    resolver: zodResolver(settingsSchema),
    defaultValues: {
      contactEmail: "",
      contactPhone: "",
      bankName: "",
      bankAccountName: "",
      bankAccountNumber: "",
      codEnabled: true,
      onlinePaymentEnabled: true,
      bankTransferEnabled: false,
      stripeEnabled: false,
      stockManagementEnabled: true,
      pickupEnabled: false,
      bookingsEnabled: false,
      facebookUrl: "",
      instagramUrl: "",
      tiktokUrl: "",
    },
  });

  useEffect(() => {
    if (settings && store && planInfo) {
      reset({
        contactEmail: settings.contactEmail,
        contactPhone: settings.contactPhone,
        bankName: settings.bankName,
        bankAccountName: settings.bankAccountName,
        bankAccountNumber: settings.bankAccountNumber,
        // Cash on Delivery and Bank transfer are Pro-only — the backend
        // already refuses to persist `true` for either on a Free plan
        // (see StoreService.upsertSettings), but a seller who downgraded
        // after enabling them could still have a stale `true` sitting in
        // the DB, so the form must not display/resubmit that as if it
        // were still in effect.
        codEnabled: isPro && settings.codEnabled,
        onlinePaymentEnabled: settings.onlinePaymentEnabled,
        bankTransferEnabled: isPro && settings.bankTransferEnabled,
        stripeEnabled: settings.stripeEnabled,
        stockManagementEnabled: settings.stockManagementEnabled,
        pickupEnabled: settings.pickupEnabled,
        bookingsEnabled: settings.bookingsEnabled,
        gstRegistered: settings.gstRegistered,
        facebookUrl: store.facebookUrl ?? "",
        instagramUrl: store.instagramUrl ?? "",
        tiktokUrl: store.tiktokUrl ?? "",
      });
    }
  }, [settings, store, planInfo, isPro, reset]);

  const codEnabled = watch("codEnabled");
  const onlinePaymentEnabled = watch("onlinePaymentEnabled");
  const bankTransferEnabled = watch("bankTransferEnabled");
  const stripeEnabled = watch("stripeEnabled");
  const stockManagementEnabled = watch("stockManagementEnabled");
  const pickupEnabled = watch("pickupEnabled");
  const bookingsEnabled = watch("bookingsEnabled");
  const gstRegistered = watch("gstRegistered");

  const stripeOnboardingMutation = useMutation({
    mutationFn: () => storesService.startStripeConnectOnboarding(storeId),
    onSuccess: ({ onboardingUrl }) => {
      window.location.href = onboardingUrl;
    },
    onError: () => toast.error("Couldn't start Stripe onboarding. Please try again."),
  });

  // Normally account.updated webhook keeps stripeChargesEnabled in sync —
  // this is a fallback for when that webhook is misconfigured or drops an
  // event (see backend StripeConnectService's doc comment), so a seller who
  // actually finished Stripe's hosted onboarding isn't stuck seeing "Finish
  // onboarding" forever with no way to unstick themselves. Runs once per
  // page load whenever we're in that stuck-looking state.
  const stripeRefreshedRef = useRef(false);
  const stripeRefreshMutation = useMutation({
    mutationFn: () => storesService.refreshStripeConnectStatus(storeId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["store-settings"] }),
  });
  useEffect(() => {
    if (stripeRefreshedRef.current) return;
    if (!settings?.stripeAccountId || settings.stripeChargesEnabled) return;
    stripeRefreshedRef.current = true;
    stripeRefreshMutation.mutate();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- run once per mount, not on every settings refetch
  }, [settings?.stripeAccountId, settings?.stripeChargesEnabled]);

  const checkoutMutation = useMutation({
    mutationFn: () => billingService.startProCheckout(),
    onSuccess: ({ checkoutUrl }) => {
      window.location.href = checkoutUrl;
    },
    onError: () => toast.error("Couldn't start checkout. Please try again."),
  });

  const cancelProMutation = useMutation({
    mutationFn: () => billingService.cancelPro(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["seller-plan"] });
      toast.success("Pro won't renew — you'll keep access until the end of the current billing period.");
    },
    onError: () => toast.error("Couldn't cancel. Please try again."),
  });

  // Same "webhook might be misconfigured or drop an event" fallback as
  // Stripe Connect's refresh above — this is where the seller lands right
  // after paying, so a delayed webhook shouldn't leave them looking stuck
  // on the Free plan.
  const planRefreshedRef = useRef(false);
  const planRefreshMutation = useMutation({
    mutationFn: () => billingService.refreshPlanStatus(),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["seller-plan"] }),
  });
  useEffect(() => {
    if (planRefreshedRef.current) return;
    if (searchParams.get("upgraded") !== "true") return;
    planRefreshedRef.current = true;
    planRefreshMutation.mutate();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- run once per mount
  }, [searchParams]);

  const mutation = useMutation({
    mutationFn: async (values: SettingsFormValues) => {
      const { facebookUrl, instagramUrl, tiktokUrl, ...settingsValues } = values;
      // The checkboxes above are hidden (not just disabled) when the
      // platform doesn't offer the method at all — clamp here too so a
      // stale true value from before the platform setting changed never
      // gets resubmitted just because its checkbox isn't rendered to
      // uncheck it.
      settingsValues.codEnabled = platformCodEnabled && settingsValues.codEnabled;
      settingsValues.bankTransferEnabled = platformBankTransferEnabled && settingsValues.bankTransferEnabled;
      await Promise.all([
        storesService.updateStoreSettings(storeId, settingsValues),
        storesService.updateStoreProfile(storeId, { facebookUrl, instagramUrl, tiktokUrl }),
      ]);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["store-settings"] });
      queryClient.invalidateQueries({ queryKey: ["store"] });
      toast.success("Settings saved");
    },
    onError: () => toast.error("Couldn't save settings. Please try again."),
  });

  const [isUploadingLogo, setIsUploadingLogo] = useState(false);
  const [isUploadingBanner, setIsUploadingBanner] = useState(false);
  const [isUploadingLicence, setIsUploadingLicence] = useState(false);
  const [isUploadingAbn, setIsUploadingAbn] = useState(false);
  const [isUploadingNic, setIsUploadingNic] = useState(false);
  const [isUploadingBusinessReg, setIsUploadingBusinessReg] = useState(false);

  async function handleLogoUpload(file: File | undefined) {
    if (!file) return;
    setIsUploadingLogo(true);
    try {
      await storesService.uploadStoreLogo(storeId, file);
      queryClient.invalidateQueries({ queryKey: ["store"] });
      toast.success("Store logo updated");
    } catch {
      toast.error("Couldn't upload the image. Please try again.");
    } finally {
      setIsUploadingLogo(false);
    }
  }

  async function handleBannerUpload(file: File | undefined) {
    if (!file) return;
    setIsUploadingBanner(true);
    try {
      await storesService.uploadStoreBanner(storeId, file);
      queryClient.invalidateQueries({ queryKey: ["store"] });
      toast.success("Store banner updated");
    } catch {
      toast.error("Couldn't upload the image. Please try again.");
    } finally {
      setIsUploadingBanner(false);
    }
  }

  async function handleLicenceUpload(file: File | undefined) {
    if (!file) return;
    setIsUploadingLicence(true);
    try {
      await storesService.uploadDriverLicenceDocument(storeId, file);
      queryClient.invalidateQueries({ queryKey: ["store-settings"] });
      toast.success("Driver's licence document updated");
    } catch {
      toast.error("Couldn't upload the file. Please try again.");
    } finally {
      setIsUploadingLicence(false);
    }
  }

  async function handleAbnUpload(file: File | undefined) {
    if (!file) return;
    setIsUploadingAbn(true);
    try {
      await storesService.uploadAbnDocument(storeId, file);
      queryClient.invalidateQueries({ queryKey: ["store-settings"] });
      toast.success("ABN document updated");
    } catch {
      toast.error("Couldn't upload the file. Please try again.");
    } finally {
      setIsUploadingAbn(false);
    }
  }

  async function handleNicUpload(file: File | undefined) {
    if (!file) return;
    setIsUploadingNic(true);
    try {
      await storesService.uploadNicDocument(storeId, file);
      queryClient.invalidateQueries({ queryKey: ["store-settings"] });
      toast.success("NIC document updated");
    } catch {
      toast.error("Couldn't upload the file. Please try again.");
    } finally {
      setIsUploadingNic(false);
    }
  }

  async function handleBusinessRegUpload(file: File | undefined) {
    if (!file) return;
    setIsUploadingBusinessReg(true);
    try {
      await storesService.uploadBusinessRegDocument(storeId, file);
      queryClient.invalidateQueries({ queryKey: ["store-settings"] });
      toast.success("Business registration document updated");
    } catch {
      toast.error("Couldn't upload the file. Please try again.");
    } finally {
      setIsUploadingBusinessReg(false);
    }
  }

  const [changeRequestOpen, setChangeRequestOpen] = useState(false);
  const [changeRequestLicenceFile, setChangeRequestLicenceFile] = useState<File | undefined>();
  const [changeRequestAbnFile, setChangeRequestAbnFile] = useState<File | undefined>();
  const [changeRequestNicFile, setChangeRequestNicFile] = useState<File | undefined>();
  const [changeRequestBusinessRegFile, setChangeRequestBusinessRegFile] = useState<File | undefined>();

  const {
    register: registerChangeRequest,
    handleSubmit: handleChangeRequestSubmit,
    watch: watchChangeRequest,
    setValue: setChangeRequestValue,
    reset: resetChangeRequestForm,
  } = useForm<ChangeRequestFormValues>({
    resolver: zodResolver(changeRequestSchema),
    defaultValues: { sellerType: "individual" },
  });
  const changeRequestSellerType = watchChangeRequest("sellerType");

  const changeRequestMutation = useMutation({
    mutationFn: (values: ChangeRequestFormValues) =>
      storesService.submitVerificationChangeRequest(storeId, values, {
        driverLicenceDocument: changeRequestLicenceFile,
        abnDocument: changeRequestAbnFile,
        nicDocument: changeRequestNicFile,
        businessRegDocument: changeRequestBusinessRegFile,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["verification-change-request", storeId] });
      toast.success("Change request submitted — an admin will review it shortly");
      setChangeRequestOpen(false);
      setChangeRequestLicenceFile(undefined);
      setChangeRequestAbnFile(undefined);
      setChangeRequestNicFile(undefined);
      setChangeRequestBusinessRegFile(undefined);
    },
    onError: () => toast.error("Couldn't submit the change request. Please try again."),
  });

  function openChangeRequestDialog() {
    resetChangeRequestForm({
      sellerType: settings?.sellerType ?? "individual",
      driverLicenceNumber: settings?.driverLicenceNumber ?? "",
      abn: settings?.abn ?? "",
      nicNumber: settings?.nicNumber ?? "",
      businessRegistrationNumber: settings?.businessRegistrationNumber ?? "",
    });
    setChangeRequestOpen(true);
  }

  if (isLoading) {
    return (
      <div className="flex justify-center py-24">
        <Loader2 className="text-muted-foreground size-6 animate-spin" />
      </div>
    );
  }

  return (
    <div className="max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Store settings</h1>
        <p className="text-muted-foreground text-sm">Contact info, payouts and payment methods.</p>
      </div>

      {proPlanEnabled ? (
      <Card>
        <CardContent className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <h2 className="font-semibold">Plan</h2>
              <StatusBadge tone={isPro ? "warning" : "neutral"}>
                {isPro ? (
                  <>
                    <Sparkles className="size-3" /> Pro
                  </>
                ) : (
                  "Free"
                )}
              </StatusBadge>
            </div>
            {!isPro ? (
              <Button type="button" disabled={checkoutMutation.isPending} onClick={() => checkoutMutation.mutate()}>
                {checkoutMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : <Sparkles className="size-3.5" />}
                Upgrade to Pro
              </Button>
            ) : null}
          </div>

          {isPro ? (
            <div className="space-y-2 text-sm">
              <p className="text-muted-foreground">
                {formatCurrency(planInfo?.monthlyPriceCents ?? 0, currency)}/month —{" "}
                {planInfo?.cancelAtPeriodEnd ? "won't renew, Pro until" : "renews"}{" "}
                {planInfo?.currentPeriodEnd ? formatDate(planInfo.currentPeriodEnd) : "—"}.
              </p>
              {!planInfo?.cancelAtPeriodEnd ? (
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="text-destructive -ml-2"
                  disabled={cancelProMutation.isPending}
                  onClick={() => cancelProMutation.mutate()}
                >
                  {cancelProMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
                  Cancel Pro
                </Button>
              ) : null}
            </div>
          ) : (
            <p className="text-muted-foreground text-sm">
              Unlock Cash on Delivery and Bank transfer as payment options for{" "}
              {formatCurrency(planInfo?.monthlyPriceCents ?? 0, currency)}/month.
            </p>
          )}
        </CardContent>
      </Card>
      ) : null}

      <Card>
        <CardContent className="space-y-4">
          <h2 className="font-semibold">Store branding</h2>
          <p className="text-muted-foreground text-xs">
            Uploads here save immediately, separate from the form below. Until you upload your
            own, buyers see a generated logo and banner based on your store name.
          </p>
          <div className="space-y-1.5">
            <Label htmlFor="logoUpload">Logo</Label>
            <div className="flex items-center gap-3">
              <div className="border-background bg-muted relative size-16 shrink-0 overflow-hidden rounded-full border-2">
                {store?.logoUrl ? (
                  <Image src={store.logoUrl} alt={store.name} fill sizes="64px" className="object-cover" />
                ) : store ? (
                  <StoreLogoFallback name={store.name} className="text-lg" />
                ) : null}
              </div>
              <Input
                id="logoUpload"
                type="file"
                accept="image/jpeg,image/png,image/webp"
                disabled={isUploadingLogo}
                onChange={(e) => handleLogoUpload(e.target.files?.[0])}
              />
            </div>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="bannerUpload">Banner</Label>
            <div className="bg-muted relative h-24 w-full overflow-hidden rounded-md">
              {store?.bannerUrl ? (
                <Image src={store.bannerUrl} alt="" fill sizes="100vw" className="object-cover" />
              ) : store ? (
                <StoreBannerFallback name={store.name} />
              ) : null}
            </div>
            <Input
              id="bannerUpload"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              disabled={isUploadingBanner}
              onChange={(e) => handleBannerUpload(e.target.files?.[0])}
            />
          </div>
        </CardContent>
      </Card>

      <MfaSettingsCard />

      <form onSubmit={handleSubmit((values) => mutation.mutate(values))} className="space-y-6">
        <Card>
          <CardContent className="space-y-4">
            <h2 className="font-semibold">Contact</h2>
            <div className="space-y-1.5">
              <Label htmlFor="contactEmail">Contact email</Label>
              <Input id="contactEmail" type="email" {...register("contactEmail")} />
              {errors.contactEmail ? (
                <p className="text-destructive text-xs">{errors.contactEmail.message}</p>
              ) : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="contactPhone">Contact phone</Label>
              <Input id="contactPhone" {...register("contactPhone")} />
              {errors.contactPhone ? (
                <p className="text-destructive text-xs">{errors.contactPhone.message}</p>
              ) : null}
            </div>
          </CardContent>
        </Card>

        {needsBankDetails ? (
        <Card>
          <CardContent className="space-y-4">
            <h2 className="font-semibold">Payout bank account</h2>
            <div className="space-y-1.5">
              <Label htmlFor="bankName">Bank name</Label>
              <Input id="bankName" {...register("bankName")} />
              {errors.bankName ? <p className="text-destructive text-xs">{errors.bankName.message}</p> : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="bankAccountName">Account holder name</Label>
              <Input id="bankAccountName" {...register("bankAccountName")} />
              {errors.bankAccountName ? (
                <p className="text-destructive text-xs">{errors.bankAccountName.message}</p>
              ) : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="bankAccountNumber">Account number</Label>
              <Input id="bankAccountNumber" {...register("bankAccountNumber")} />
              {errors.bankAccountNumber ? (
                <p className="text-destructive text-xs">{errors.bankAccountNumber.message}</p>
              ) : null}
            </div>
          </CardContent>
        </Card>
        ) : null}

        <Card>
          <CardContent className="space-y-4">
            <h2 className="font-semibold">Social media</h2>
            <p className="text-muted-foreground text-xs">
              Shown on your public store page — leave blank to hide.
            </p>
            <div className="space-y-1.5">
              <Label htmlFor="facebookUrl">Facebook</Label>
              <Input
                id="facebookUrl"
                placeholder="https://facebook.com/yourstore"
                {...register("facebookUrl")}
              />
              {errors.facebookUrl ? (
                <p className="text-destructive text-xs">{errors.facebookUrl.message}</p>
              ) : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="instagramUrl">Instagram</Label>
              <Input
                id="instagramUrl"
                placeholder="https://instagram.com/yourstore"
                {...register("instagramUrl")}
              />
              {errors.instagramUrl ? (
                <p className="text-destructive text-xs">{errors.instagramUrl.message}</p>
              ) : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="tiktokUrl">TikTok</Label>
              <Input
                id="tiktokUrl"
                placeholder="https://tiktok.com/@yourstore"
                {...register("tiktokUrl")}
              />
              {errors.tiktokUrl ? (
                <p className="text-destructive text-xs">{errors.tiktokUrl.message}</p>
              ) : null}
            </div>
          </CardContent>
        </Card>

        {isAustralia ? (
        <Card>
          <CardContent className="space-y-4">
            <h2 className="font-semibold">Stripe Connect</h2>
            {!settings?.stripeAccountId ? (
              <>
                <p className="text-muted-foreground text-sm">
                  Connect a Stripe account to accept card payments — buyers pay you directly and
                  automatically, we never hold your money.
                </p>
                <Button
                  type="button"
                  variant="outline"
                  disabled={stripeOnboardingMutation.isPending}
                  onClick={() => stripeOnboardingMutation.mutate()}
                >
                  {stripeOnboardingMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
                  Connect with Stripe
                </Button>
              </>
            ) : !settings.stripeChargesEnabled ? (
              <>
                <p className="text-muted-foreground text-sm">
                  {stripeRefreshMutation.isPending
                    ? "Checking with Stripe for your latest status…"
                    : "You've started connecting a Stripe account, but onboarding isn't finished yet — Stripe still needs a bit more information before you can accept payments. If you've already completed Stripe's form, try checking again below."}
                </p>
                <div className="flex flex-wrap gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    disabled={stripeOnboardingMutation.isPending}
                    onClick={() => stripeOnboardingMutation.mutate()}
                  >
                    {stripeOnboardingMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
                    Finish onboarding
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    disabled={stripeRefreshMutation.isPending}
                    onClick={() => stripeRefreshMutation.mutate()}
                  >
                    {stripeRefreshMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
                    Check again
                  </Button>
                </div>
              </>
            ) : (
              <div className="flex items-center gap-2 text-sm">
                <span className="bg-success-foreground inline-block size-2 rounded-full" />
                <span>Connected — Stripe account ready to accept payments.</span>
              </div>
            )}
          </CardContent>
        </Card>
        ) : null}

        <Card>
          <CardContent className="space-y-4">
            <h2 className="font-semibold">Payment methods</h2>
            {platformCodEnabled ? (
            <label className={cn("flex items-start gap-3", !isPro && "cursor-not-allowed opacity-60")}>
              <Checkbox
                checked={codEnabled}
                disabled={!isPro}
                onCheckedChange={(checked) => setValue("codEnabled", checked === true)}
              />
              <span>
                <span className="flex items-center gap-2">
                  <span className="text-sm font-medium">Cash on Delivery</span>
                  {!isPro ? (
                    <Badge variant="secondary" className="border-0 text-[10px]">
                      Included in Pro
                    </Badge>
                  ) : null}
                </span>
                <span className="text-muted-foreground block text-xs">
                  Let buyers pay in cash when their order arrives
                </span>
              </span>
            </label>
            ) : null}
            {isSriLanka ? (
            <label className="flex items-start gap-3">
              <Checkbox
                checked={onlinePaymentEnabled}
                onCheckedChange={(checked) => setValue("onlinePaymentEnabled", checked === true)}
              />
              <span>
                <span className="block text-sm font-medium">Online payment (PayHere)</span>
                <span className="text-muted-foreground block text-xs">
                  Accept cards, LankaQR, eZ Cash and mCash — a 3.5% transaction fee applies
                </span>
              </span>
            </label>
            ) : null}
            {platformBankTransferEnabled ? (
            <label className={cn("flex items-start gap-3", !isPro && "cursor-not-allowed opacity-60")}>
              <Checkbox
                checked={bankTransferEnabled}
                disabled={!isPro}
                onCheckedChange={(checked) => setValue("bankTransferEnabled", checked === true)}
              />
              <span>
                <span className="flex items-center gap-2">
                  <span className="text-sm font-medium">Bank transfer</span>
                  {!isPro ? (
                    <Badge variant="secondary" className="border-0 text-[10px]">
                      Included in Pro
                    </Badge>
                  ) : null}
                </span>
                <span className="text-muted-foreground block text-xs">
                  Buyers see your bank details above and upload a payment receipt for you to
                  verify manually — no transaction fee
                </span>
              </span>
            </label>
            ) : null}
            {isAustralia && settings?.stripeChargesEnabled ? (
              <label className="flex items-start gap-3">
                <Checkbox
                  checked={stripeEnabled}
                  onCheckedChange={(checked) => setValue("stripeEnabled", checked === true)}
                />
                <span>
                  <span className="block text-sm font-medium">Stripe (cards)</span>
                  <span className="text-muted-foreground block text-xs">
                    Buyers pay by card, paid to you directly and automatically — a transaction fee
                    is deducted at the same time
                  </span>
                </span>
              </label>
            ) : null}
            {errors.bankTransferEnabled ? (
              <p className="text-destructive text-xs">{errors.bankTransferEnabled.message}</p>
            ) : null}
          </CardContent>
        </Card>

        {isAustralia ? (
          <Card>
            <CardContent className="space-y-4">
              <h2 className="font-semibold">Tax</h2>
              <label className="flex items-start gap-3">
                <Checkbox
                  checked={gstRegistered}
                  onCheckedChange={(checked) => setValue("gstRegistered", checked === true)}
                />
                <span>
                  <span className="block text-sm font-medium">Registered for GST</span>
                  <span className="text-muted-foreground block text-xs">
                    GST registration is optional below A$75,000 annual turnover. When enabled, your
                    order confirmations include your ABN and a GST breakdown as a tax invoice. Don&apos;t
                    enable this unless you&apos;re actually registered with the ATO.
                  </span>
                </span>
              </label>
            </CardContent>
          </Card>
        ) : null}

        <Card>
          <CardContent className="space-y-4">
            <h2 className="font-semibold">Inventory</h2>
            <label className="flex items-start gap-3">
              <Checkbox
                checked={stockManagementEnabled}
                onCheckedChange={(checked) => setValue("stockManagementEnabled", checked === true)}
              />
              <span>
                <span className="block text-sm font-medium">Track stock quantities</span>
                <span className="text-muted-foreground block text-xs">
                  When off, the stock quantity field is hidden on the product form and every
                  product in your store is treated as always available.
                </span>
              </span>
            </label>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="space-y-4">
            <h2 className="font-semibold">Delivery</h2>
            <label className="flex items-start gap-3">
              <Checkbox
                checked={pickupEnabled}
                onCheckedChange={(checked) => setValue("pickupEnabled", checked === true)}
              />
              <span>
                <span className="block text-sm font-medium">Offer pickup in store</span>
                <span className="text-muted-foreground block text-xs">
                  Buyers can choose to collect their order themselves instead of paying for
                  shipping — you&apos;ll coordinate the pickup time over WhatsApp.
                </span>
              </span>
            </label>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="space-y-4">
            <h2 className="font-semibold">Bookings</h2>
            <label className="flex items-start gap-3">
              <Checkbox
                checked={bookingsEnabled}
                onCheckedChange={(checked) => setValue("bookingsEnabled", checked === true)}
              />
              <span>
                <span className="block text-sm font-medium">Offer bookable services</span>
                <span className="text-muted-foreground block text-xs">
                  Adds a Services section to your storefront so buyers can book an appointment
                  instead of (or alongside) buying products. Manage your services and weekly
                  availability from the Services and Availability pages once this is on.
                </span>
              </span>
            </label>
          </CardContent>
        </Card>

        <div className="flex justify-end">
          <Button type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            Save settings
          </Button>
        </div>
      </form>

      {isStoreActive ? (
        <Card>
          <CardContent className="space-y-4">
            <h2 className="font-semibold">Verification details</h2>
            <p className="text-muted-foreground text-xs">
              This store is already approved — changes to your seller type, ID number and
              documents now go through admin review instead of saving immediately.
            </p>
            <dl className="grid grid-cols-2 gap-3 text-sm">
              <div>
                <dt className="text-muted-foreground text-xs">Seller type</dt>
                <dd className="capitalize">{settings?.sellerType}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground text-xs">
                  {isSriLanka ? "NIC number" : "Driver's licence no."}
                </dt>
                <dd>{(isSriLanka ? settings?.nicNumber : settings?.driverLicenceNumber) || "—"}</dd>
              </div>
              {settings?.sellerType === "business" ? (
                <div>
                  <dt className="text-muted-foreground text-xs">
                    {isSriLanka ? "Business reg. no." : "ABN"}
                  </dt>
                  <dd>{(isSriLanka ? settings?.businessRegistrationNumber : settings?.abn) || "—"}</dd>
                </div>
              ) : null}
            </dl>
            {pendingChangeRequest ? (
              <div className="bg-muted space-y-1 rounded-md p-3 text-sm">
                <p className="font-medium">Change request pending review</p>
                <p className="text-muted-foreground text-xs">
                  Submitted {formatDateTime(pendingChangeRequest.submittedAt)} — an admin will review it
                  shortly. Your current details above stay in effect until then.
                </p>
              </div>
            ) : (
              <Button type="button" variant="outline" onClick={openChangeRequestDialog}>
                Request a change
              </Button>
            )}
          </CardContent>
        </Card>
      ) : (
      <Card>
        <CardContent className="space-y-4">
          <h2 className="font-semibold">Verification documents</h2>
          <p className="text-muted-foreground text-xs">
            Uploads here save immediately, separate from the form above.
          </p>
          {isSriLanka ? (
            <>
              <div className="space-y-1.5">
                <Label htmlFor="nicDocument">NIC copy</Label>
                {settings?.nicDocumentUrl ? (
                  <a
                    href={settings.nicDocumentUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-primary flex items-center gap-1.5 text-sm underline-offset-4 hover:underline"
                  >
                    <FileText className="size-3.5" /> View current file
                  </a>
                ) : null}
                <Input
                  id="nicDocument"
                  type="file"
                  accept="image/jpeg,image/png,image/webp,application/pdf"
                  disabled={isUploadingNic}
                  onChange={(e) => handleNicUpload(e.target.files?.[0])}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="businessRegDocument">Business registration document</Label>
                {settings?.businessRegDocumentUrl ? (
                  <a
                    href={settings.businessRegDocumentUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-primary flex items-center gap-1.5 text-sm underline-offset-4 hover:underline"
                  >
                    <FileText className="size-3.5" /> View current file
                  </a>
                ) : null}
                <Input
                  id="businessRegDocument"
                  type="file"
                  accept="image/jpeg,image/png,image/webp,application/pdf"
                  disabled={isUploadingBusinessReg}
                  onChange={(e) => handleBusinessRegUpload(e.target.files?.[0])}
                />
              </div>
            </>
          ) : (
            <>
              <div className="space-y-1.5">
                <Label htmlFor="licenceDocument">Driver&apos;s licence copy</Label>
                {settings?.driverLicenceDocumentUrl ? (
                  <a
                    href={settings.driverLicenceDocumentUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-primary flex items-center gap-1.5 text-sm underline-offset-4 hover:underline"
                  >
                    <FileText className="size-3.5" /> View current file
                  </a>
                ) : null}
                <Input
                  id="licenceDocument"
                  type="file"
                  accept="image/jpeg,image/png,image/webp,application/pdf"
                  disabled={isUploadingLicence}
                  onChange={(e) => handleLicenceUpload(e.target.files?.[0])}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="abnDocument">ABN registration document</Label>
                {settings?.abnDocumentUrl ? (
                  <a
                    href={settings.abnDocumentUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-primary flex items-center gap-1.5 text-sm underline-offset-4 hover:underline"
                  >
                    <FileText className="size-3.5" /> View current file
                  </a>
                ) : null}
                <Input
                  id="abnDocument"
                  type="file"
                  accept="image/jpeg,image/png,image/webp,application/pdf"
                  disabled={isUploadingAbn}
                  onChange={(e) => handleAbnUpload(e.target.files?.[0])}
                />
              </div>
            </>
          )}
        </CardContent>
      </Card>
      )}

      <StaffManagementCard storeId={storeId} />

      <DangerZoneCard
        storeId={store?.id ?? null}
        storeName={store?.name ?? null}
        verificationStatus={store?.verificationStatus ?? null}
      />

      <Dialog open={changeRequestOpen} onOpenChange={setChangeRequestOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Request a verification change</DialogTitle>
            <DialogDescription>
              An admin will review this before it takes effect on your store.
            </DialogDescription>
          </DialogHeader>
          <form
            id="change-request-form"
            className="space-y-4"
            onSubmit={handleChangeRequestSubmit((values) => changeRequestMutation.mutate(values))}
          >
            <div className="space-y-1.5">
              <Label htmlFor="crSellerType">Seller type</Label>
              <Select
                value={changeRequestSellerType}
                onValueChange={(v) => setChangeRequestValue("sellerType", v as SellerType)}
              >
                <SelectTrigger id="crSellerType">
                  <SelectValue>{(v: SellerType) => (v === "business" ? "Business" : "Individual")}</SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="individual">Individual</SelectItem>
                  <SelectItem value="business">Business</SelectItem>
                </SelectContent>
              </Select>
            </div>
            {isSriLanka ? (
              <>
                <div className="space-y-1.5">
                  <Label htmlFor="crNicNumber">NIC number</Label>
                  <Input id="crNicNumber" {...registerChangeRequest("nicNumber")} />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="crNicDocument">Replace NIC copy (optional)</Label>
                  <Input
                    id="crNicDocument"
                    type="file"
                    accept="image/jpeg,image/png,image/webp,application/pdf"
                    onChange={(e) => setChangeRequestNicFile(e.target.files?.[0])}
                  />
                </div>
                {changeRequestSellerType === "business" ? (
                  <>
                    <div className="space-y-1.5">
                      <Label htmlFor="crBusinessRegistrationNumber">Business registration number</Label>
                      <Input id="crBusinessRegistrationNumber" {...registerChangeRequest("businessRegistrationNumber")} />
                    </div>
                    <div className="space-y-1.5">
                      <Label htmlFor="crBusinessRegDocument">Replace business registration document (optional)</Label>
                      <Input
                        id="crBusinessRegDocument"
                        type="file"
                        accept="image/jpeg,image/png,image/webp,application/pdf"
                        onChange={(e) => setChangeRequestBusinessRegFile(e.target.files?.[0])}
                      />
                    </div>
                  </>
                ) : null}
              </>
            ) : (
              <>
                <div className="space-y-1.5">
                  <Label htmlFor="crDriverLicenceNumber">Driver&apos;s licence number</Label>
                  <Input id="crDriverLicenceNumber" {...registerChangeRequest("driverLicenceNumber")} />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="crLicenceDocument">Replace driver&apos;s licence copy (optional)</Label>
                  <Input
                    id="crLicenceDocument"
                    type="file"
                    accept="image/jpeg,image/png,image/webp,application/pdf"
                    onChange={(e) => setChangeRequestLicenceFile(e.target.files?.[0])}
                  />
                </div>
                {changeRequestSellerType === "business" ? (
                  <>
                    <div className="space-y-1.5">
                      <Label htmlFor="crAbn">ABN</Label>
                      <Input id="crAbn" {...registerChangeRequest("abn")} />
                    </div>
                    <div className="space-y-1.5">
                      <Label htmlFor="crAbnDocument">Replace ABN registration document (optional)</Label>
                      <Input
                        id="crAbnDocument"
                        type="file"
                        accept="image/jpeg,image/png,image/webp,application/pdf"
                        onChange={(e) => setChangeRequestAbnFile(e.target.files?.[0])}
                      />
                    </div>
                  </>
                ) : null}
              </>
            )}
          </form>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setChangeRequestOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" form="change-request-form" disabled={changeRequestMutation.isPending}>
              {changeRequestMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
              Submit for review
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
