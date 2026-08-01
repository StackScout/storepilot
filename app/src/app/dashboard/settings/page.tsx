"use client";

import { useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { FileText, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { useSellerStoreId } from "@/hooks/use-seller-store";
import { usePlatformConfig } from "@/hooks/use-platform-config";
import { storesService } from "@/services";

const urlOrEmpty = z
  .string()
  .trim()
  .refine((v) => v === "" || z.string().url().safeParse(v).success, "Enter a valid URL");

const settingsSchema = z
  .object({
    contactEmail: z.string().email("Enter a valid email"),
    contactPhone: z.string().min(9, "Enter a valid phone number"),
    bankName: z.string().min(2, "Enter a bank name"),
    bankAccountName: z.string().min(2, "Enter the account holder name"),
    bankAccountNumber: z.string().min(4, "Enter the account number"),
    codEnabled: z.boolean(),
    onlinePaymentEnabled: z.boolean(),
    bankTransferEnabled: z.boolean(),
    stripeEnabled: z.boolean(),
    stockManagementEnabled: z.boolean(),
    pickupEnabled: z.boolean(),
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
  );

type SettingsFormValues = z.infer<typeof settingsSchema>;

export default function DashboardSettingsPage() {
  const queryClient = useQueryClient();
  const storeId = useSellerStoreId();
  const { countryCode } = usePlatformConfig();
  const isSriLanka = countryCode === "LK";

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

  const isLoading = isSettingsLoading || isStoreLoading;

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
      facebookUrl: "",
      instagramUrl: "",
      tiktokUrl: "",
    },
  });

  useEffect(() => {
    if (settings && store) {
      reset({
        contactEmail: settings.contactEmail,
        contactPhone: settings.contactPhone,
        bankName: settings.bankName,
        bankAccountName: settings.bankAccountName,
        bankAccountNumber: settings.bankAccountNumber,
        codEnabled: settings.codEnabled,
        onlinePaymentEnabled: settings.onlinePaymentEnabled,
        bankTransferEnabled: settings.bankTransferEnabled,
        stripeEnabled: settings.stripeEnabled,
        stockManagementEnabled: settings.stockManagementEnabled,
        pickupEnabled: settings.pickupEnabled,
        facebookUrl: store.facebookUrl ?? "",
        instagramUrl: store.instagramUrl ?? "",
        tiktokUrl: store.tiktokUrl ?? "",
      });
    }
  }, [settings, store, reset]);

  const codEnabled = watch("codEnabled");
  const onlinePaymentEnabled = watch("onlinePaymentEnabled");
  const bankTransferEnabled = watch("bankTransferEnabled");
  const stripeEnabled = watch("stripeEnabled");
  const stockManagementEnabled = watch("stockManagementEnabled");
  const pickupEnabled = watch("pickupEnabled");

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

  const mutation = useMutation({
    mutationFn: async (values: SettingsFormValues) => {
      const { facebookUrl, instagramUrl, tiktokUrl, ...settingsValues } = values;
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

  const [isUploadingLicence, setIsUploadingLicence] = useState(false);
  const [isUploadingAbn, setIsUploadingAbn] = useState(false);
  const [isUploadingNic, setIsUploadingNic] = useState(false);
  const [isUploadingBusinessReg, setIsUploadingBusinessReg] = useState(false);

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
                <span className="inline-block size-2 rounded-full bg-emerald-500" />
                <span>Connected — Stripe account ready to accept payments.</span>
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardContent className="space-y-4">
            <h2 className="font-semibold">Payment methods</h2>
            <label className="flex items-start gap-3">
              <Checkbox
                checked={codEnabled}
                onCheckedChange={(checked) => setValue("codEnabled", checked === true)}
              />
              <span>
                <span className="block text-sm font-medium">Cash on Delivery</span>
                <span className="text-muted-foreground block text-xs">
                  Let buyers pay in cash when their order arrives
                </span>
              </span>
            </label>
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
            <label className="flex items-start gap-3">
              <Checkbox
                checked={bankTransferEnabled}
                onCheckedChange={(checked) => setValue("bankTransferEnabled", checked === true)}
              />
              <span>
                <span className="block text-sm font-medium">Bank transfer</span>
                <span className="text-muted-foreground block text-xs">
                  Buyers see your bank details above and upload a payment receipt for you to
                  verify manually — no transaction fee
                </span>
              </span>
            </label>
            {settings?.stripeChargesEnabled ? (
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

        <div className="flex justify-end">
          <Button type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            Save settings
          </Button>
        </div>
      </form>

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
    </div>
  );
}
