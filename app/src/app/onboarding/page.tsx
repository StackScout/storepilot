"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2, ShieldCheck, Sparkles, Store } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Card, CardContent } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { AbnVerificationBadge } from "@/components/shared/abn-verification-badge";
import { useCategories } from "@/hooks/use-categories";
import { formatCurrency } from "@/lib/currency";
import { usePlatformConfig, useStates } from "@/hooks/use-platform-config";
import { storesService, authService, billingService } from "@/services";
import type { SellerType, StoreCategory } from "@/types";

/**
 * Which identity-verification fields are required depends on this
 * deployment's platform config country (`platform_settings.country_code`),
 * not on anything the seller chooses — see StoreSettings' doc comment on
 * the backend. Built per-render (via useMemo) since it closes over that.
 */
function buildOnboardingSchema(isSriLanka: boolean) {
  return z
    .object({
      storeName: z.string().min(3, "Enter your store name"),
      category: z.string().min(1, "Select a category"),
      tagline: z.string().min(5, "Add a short tagline"),
      description: z.string().min(20, "Describe your store in a bit more detail (min 20 characters)"),
      city: z.string().min(2, "Enter your city/town"),
      state: z.string().min(1, "Select a state/province"),
      whatsappNumber: z.string().min(9, "Enter a valid WhatsApp number"),
      contactEmail: z.string().email("Enter a valid email"),
      sellerType: z.enum(["individual", "business"]),
      driverLicenceNumber: z.string().optional(),
      abn: z.string().optional(),
      nicNumber: z.string().optional(),
      businessRegistrationNumber: z.string().optional(),
      bankName: z.string().min(2, "Enter your bank name"),
      bankAccountName: z.string().min(2, "Enter the account holder name"),
      bankAccountNumber: z.string().min(4, "Enter the account number"),
      agreeToTerms: z.boolean().refine((v) => v, "You must agree to continue"),
    })
    .superRefine((data, ctx) => {
      if (isSriLanka) {
        if (!data.nicNumber || data.nicNumber.trim().length < 5) {
          ctx.addIssue({ code: "custom", path: ["nicNumber"], message: "Enter a valid NIC number" });
        }
        if (data.sellerType === "business" && !data.businessRegistrationNumber?.trim()) {
          ctx.addIssue({
            code: "custom",
            path: ["businessRegistrationNumber"],
            message: "Enter your business registration number",
          });
        }
      } else {
        if (!data.driverLicenceNumber || data.driverLicenceNumber.trim().length < 6) {
          ctx.addIssue({
            code: "custom",
            path: ["driverLicenceNumber"],
            message: "Enter a valid driver's licence number",
          });
        }
        if (data.sellerType === "business" && !data.abn?.trim()) {
          ctx.addIssue({ code: "custom", path: ["abn"], message: "Enter your ABN" });
        }
      }
    });
}

type OnboardingFormValues = z.infer<ReturnType<typeof buildOnboardingSchema>>;

export default function OnboardingPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { name, platformFeePercent, countryCode, proMonthlyPriceCents, currencyCode, currencySymbol, currencyLocale } =
    usePlatformConfig();
  const currency = { code: currencyCode, symbol: currencySymbol, locale: currencyLocale };
  const { categories } = useCategories();
  const isSriLanka = countryCode === "LK";
  const [sellerPlan, setSellerPlan] = useState<"free" | "pro">("free");
  const onboardingSchema = useMemo(() => buildOnboardingSchema(isSriLanka), [isSriLanka]);
  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors },
  } = useForm<OnboardingFormValues>({
    resolver: zodResolver(onboardingSchema),
    defaultValues: {
      category: "",
      state: "",
      sellerType: "individual",
      agreeToTerms: false,
    },
  });

  const category = watch("category");
  const state = watch("state");
  const sellerType = watch("sellerType");
  const abn = watch("abn");
  const agreeToTerms = watch("agreeToTerms");
  const { data: states } = useStates();

  const [licenceFile, setLicenceFile] = useState<File | null>(null);
  const [abnFile, setAbnFile] = useState<File | null>(null);
  const [nicFile, setNicFile] = useState<File | null>(null);
  const [businessRegFile, setBusinessRegFile] = useState<File | null>(null);

  const mutation = useMutation({
    mutationFn: async (values: OnboardingFormValues) => {
      if (isSriLanka) {
        if (!nicFile) throw new Error("Upload a copy of your NIC to continue");
        if (values.sellerType === "business" && !businessRegFile) {
          throw new Error("Upload your business registration document to continue");
        }
      } else {
        if (!licenceFile) throw new Error("Upload a copy of your driver's licence to continue");
        if (values.sellerType === "business" && !abnFile) {
          throw new Error("Upload your ABN registration document to continue");
        }
      }

      const store = await storesService.createStore({
        name: values.storeName,
        category: values.category as StoreCategory,
        tagline: values.tagline,
        description: values.description,
        city: values.city,
        state: values.state,
        whatsappNumber: values.whatsappNumber,
      });
      // createStore just granted the caller's account the "seller" Cognito
      // group, but the access token already in the browser was issued
      // before that — cognito:groups only reflects current membership at
      // the moment a token is issued, so without this refresh both the
      // settings PATCH below (ROLE_SELLER-gated) and the dashboard (gated
      // by proxy.ts checking that same stale token) would reject the
      // still-buyer-only token.
      await authService.refresh();
      await storesService.updateStoreSettings(store.id, {
        contactEmail: values.contactEmail,
        contactPhone: values.whatsappNumber,
        bankName: values.bankName,
        bankAccountName: values.bankAccountName,
        bankAccountNumber: values.bankAccountNumber,
        sellerType: values.sellerType,
        ...(isSriLanka
          ? { nicNumber: values.nicNumber, businessRegistrationNumber: values.businessRegistrationNumber }
          : { driverLicenceNumber: values.driverLicenceNumber, abn: values.abn }),
        codEnabled: true,
        onlinePaymentEnabled: true,
      });
      if (isSriLanka) {
        await storesService.uploadNicDocument(store.id, nicFile!);
        if (businessRegFile) {
          await storesService.uploadBusinessRegDocument(store.id, businessRegFile);
        }
      } else {
        await storesService.uploadDriverLicenceDocument(store.id, licenceFile!);
        if (abnFile) {
          await storesService.uploadAbnDocument(store.id, abnFile);
        }
      }
    },
    onSuccess: async () => {
      toast.success("Application submitted! Your store is pending review.");
      // React Query cached the pre-onboarding auth-session (role: "buyer"
      // only) — clear it so the dashboard's checks see the fresh "seller"
      // role from the token authService.refresh() just reissued above.
      queryClient.clear();
      if (sellerPlan === "pro") {
        try {
          const { checkoutUrl } = await billingService.startProCheckout();
          window.location.href = checkoutUrl;
          return;
        } catch {
          toast.error("Your store was created, but starting the Pro checkout failed — you can upgrade anytime from Settings.");
        }
      }
      router.push("/dashboard");
      router.refresh();
    },
    onError: (error: Error) =>
      toast.error(error.message || "Something went wrong submitting your application. Please try again."),
  });

  return (
    <div className="mx-auto max-w-lg px-4 py-12 sm:px-6">
      <div className="mb-6 space-y-2 text-center">
        <span className="bg-primary/10 text-primary mx-auto flex size-12 items-center justify-center rounded-full">
          <Store className="size-6" />
        </span>
        <h1 className="text-2xl font-bold">Start selling on {name}</h1>
        <p className="text-muted-foreground text-sm">
          We review every new store before it goes live to buyers — it helps keep the marketplace
          safe. Approval typically takes 1–3 business days.
        </p>
      </div>

      <form onSubmit={handleSubmit((values) => mutation.mutate(values))} className="space-y-6">
        <Card>
          <CardContent className="space-y-4">
            <h2 className="font-semibold">Store details</h2>
            <div className="space-y-1.5">
              <Label htmlFor="storeName">Store name</Label>
              <Input id="storeName" placeholder="e.g. Blue Mountains Roasters" {...register("storeName")} />
              {errors.storeName ? (
                <p className="text-destructive text-xs">{errors.storeName.message}</p>
              ) : null}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="tagline">Short tagline</Label>
              <Textarea
                id="tagline"
                rows={2}
                placeholder="One line describing what you sell"
                {...register("tagline")}
              />
              {errors.tagline ? <p className="text-destructive text-xs">{errors.tagline.message}</p> : null}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="description">Store description</Label>
              <Textarea
                id="description"
                rows={3}
                placeholder="Tell buyers more about your products, materials, or story"
                {...register("description")}
              />
              {errors.description ? (
                <p className="text-destructive text-xs">{errors.description.message}</p>
              ) : null}
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="category">Category</Label>
                <Select
                  value={category}
                  onValueChange={(v) => setValue("category", v as string, { shouldValidate: true })}
                >
                  <SelectTrigger id="category" className="w-full">
                    <SelectValue placeholder="Select category">
                      {(v: string) => (v ? (categories.find((c) => c.wireValue === v)?.name ?? v) : "Select category")}
                    </SelectValue>
                  </SelectTrigger>
                  <SelectContent>
                    {categories.map((c) => (
                      <SelectItem key={c.wireValue} value={c.wireValue}>
                        {c.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {errors.category ? (
                  <p className="text-destructive text-xs">{errors.category.message}</p>
                ) : null}
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="state">State/Province</Label>
                <Select
                  value={state}
                  onValueChange={(v) => setValue("state", v as string, { shouldValidate: true })}
                >
                  <SelectTrigger id="state" className="w-full">
                    <SelectValue placeholder="Select state/province" />
                  </SelectTrigger>
                  <SelectContent>
                    {(states ?? []).map((s) => (
                      <SelectItem key={s.name} value={s.name}>
                        {s.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {errors.state ? (
                  <p className="text-destructive text-xs">{errors.state.message}</p>
                ) : null}
              </div>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="city">City / town</Label>
              <Input id="city" placeholder="e.g. Katoomba" {...register("city")} />
              {errors.city ? <p className="text-destructive text-xs">{errors.city.message}</p> : null}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="space-y-4">
            <div className="flex items-center gap-2">
              <ShieldCheck className="text-primary size-4.5" />
              <h2 className="font-semibold">Seller verification</h2>
            </div>
            <p className="text-muted-foreground text-xs">
              We ask for this to confirm you&apos;re a real seller before your store goes live —
              the same reason marketplaces like Amazon or Lazada verify sellers before onboarding.
            </p>

            <RadioGroup
              value={sellerType}
              onValueChange={(v) => setValue("sellerType", v as SellerType, { shouldValidate: true })}
              className="gap-3"
            >
              <Label
                htmlFor="individual"
                className="hover:bg-accent/50 flex cursor-pointer items-start gap-3 rounded-lg border p-3.5 has-[[data-state=checked]]:border-primary"
              >
                <RadioGroupItem value="individual" id="individual" className="mt-0.5" />
                <span>
                  <span className="block text-sm font-medium">Individual seller</span>
                  <span className="text-muted-foreground block text-xs">
                    Selling on your own, not as a registered company
                  </span>
                </span>
              </Label>
              <Label
                htmlFor="business"
                className="hover:bg-accent/50 flex cursor-pointer items-start gap-3 rounded-lg border p-3.5 has-[[data-state=checked]]:border-primary"
              >
                <RadioGroupItem value="business" id="business" className="mt-0.5" />
                <span>
                  <span className="block text-sm font-medium">Registered business</span>
                  <span className="text-muted-foreground block text-xs">
                    {isSriLanka
                      ? "Has a business registration number with the Registrar of Companies"
                      : "Has an ABN registered with the Australian Business Register"}
                  </span>
                </span>
              </Label>
            </RadioGroup>

            {isSriLanka ? (
              <div className="space-y-1.5">
                <Label htmlFor="nicNumber">NIC number</Label>
                <Input id="nicNumber" placeholder="e.g. 199512345678" {...register("nicNumber")} />
                {errors.nicNumber ? (
                  <p className="text-destructive text-xs">{errors.nicNumber.message}</p>
                ) : null}
              </div>
            ) : (
              <div className="space-y-1.5">
                <Label htmlFor="driverLicenceNumber">Driver&apos;s licence number</Label>
                <Input id="driverLicenceNumber" placeholder="e.g. 12345678" {...register("driverLicenceNumber")} />
                {errors.driverLicenceNumber ? (
                  <p className="text-destructive text-xs">{errors.driverLicenceNumber.message}</p>
                ) : null}
              </div>
            )}

            <div className="space-y-1.5">
              <Label htmlFor="licenceDocument">
                {isSriLanka ? "NIC copy (photo or PDF)" : "Driver's licence copy (photo or PDF)"}
              </Label>
              <Input
                id="licenceDocument"
                type="file"
                accept="image/jpeg,image/png,image/webp,application/pdf"
                onChange={(e) =>
                  isSriLanka
                    ? setNicFile(e.target.files?.[0] ?? null)
                    : setLicenceFile(e.target.files?.[0] ?? null)
                }
              />
            </div>

            {sellerType === "business" ? (
              isSriLanka ? (
                <>
                  <div className="space-y-1.5">
                    <Label htmlFor="businessRegistrationNumber">Business registration number</Label>
                    <Input
                      id="businessRegistrationNumber"
                      placeholder="e.g. PV 12345"
                      {...register("businessRegistrationNumber")}
                    />
                    {errors.businessRegistrationNumber ? (
                      <p className="text-destructive text-xs">{errors.businessRegistrationNumber.message}</p>
                    ) : null}
                  </div>
                  <div className="space-y-1.5">
                    <Label htmlFor="businessRegDocument">Business registration document (photo or PDF)</Label>
                    <Input
                      id="businessRegDocument"
                      type="file"
                      accept="image/jpeg,image/png,image/webp,application/pdf"
                      onChange={(e) => setBusinessRegFile(e.target.files?.[0] ?? null)}
                    />
                  </div>
                </>
              ) : (
                <>
                  <div className="space-y-1.5">
                    <Label htmlFor="abn">ABN</Label>
                    <Input
                      id="abn"
                      placeholder="e.g. 51 824 753 556"
                      {...register("abn")}
                    />
                    {errors.abn ? (
                      <p className="text-destructive text-xs">
                        {errors.abn.message}
                      </p>
                    ) : (
                      <AbnVerificationBadge abn={abn} />
                    )}
                  </div>
                  <div className="space-y-1.5">
                    <Label htmlFor="abnDocument">ABN registration document (photo or PDF)</Label>
                    <Input
                      id="abnDocument"
                      type="file"
                      accept="image/jpeg,image/png,image/webp,application/pdf"
                      onChange={(e) => setAbnFile(e.target.files?.[0] ?? null)}
                    />
                  </div>
                </>
              )
            ) : null}
          </CardContent>
        </Card>

        <Card>
          <CardContent className="space-y-4">
            <h2 className="font-semibold">Contact & payout details</h2>
            <div className="space-y-1.5">
              <Label htmlFor="whatsappNumber">WhatsApp number</Label>
              <Input id="whatsappNumber" placeholder="+61 4XX XXX XXX" {...register("whatsappNumber")} />
              {errors.whatsappNumber ? (
                <p className="text-destructive text-xs">{errors.whatsappNumber.message}</p>
              ) : null}
              <p className="text-muted-foreground text-xs">
                Buyers will use this to message you directly.
              </p>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="contactEmail">Email</Label>
              <Input id="contactEmail" type="email" placeholder="you@example.com" {...register("contactEmail")} />
              {errors.contactEmail ? (
                <p className="text-destructive text-xs">{errors.contactEmail.message}</p>
              ) : null}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="bankName">Bank name</Label>
              <Input id="bankName" placeholder="e.g. Commonwealth Bank of Australia" {...register("bankName")} />
              {errors.bankName ? <p className="text-destructive text-xs">{errors.bankName.message}</p> : null}
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
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
            </div>
            <p className="text-muted-foreground text-xs">
              Your account holder name should match your{" "}
              {isSriLanka ? "NIC/business registration" : "driver's licence/ABN registration"} name —
              payouts are held until this can be confirmed.
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="space-y-4">
            <div className="flex items-center gap-2">
              <Sparkles className="text-primary size-4.5" />
              <h2 className="font-semibold">Choose your plan</h2>
            </div>
            <p className="text-muted-foreground text-xs">
              You can switch plans anytime from your dashboard — this isn&apos;t locked in.
            </p>
            <RadioGroup value={sellerPlan} onValueChange={(v) => setSellerPlan(v as "free" | "pro")} className="gap-3">
              <Label
                htmlFor="plan-free"
                className="hover:bg-accent/50 flex cursor-pointer items-start gap-3 rounded-lg border p-3.5 has-[[data-state=checked]]:border-primary"
              >
                <RadioGroupItem value="free" id="plan-free" className="mt-0.5" />
                <span>
                  <span className="block text-sm font-medium">Free</span>
                  <span className="text-muted-foreground block text-xs">
                    Sell with online payment{isSriLanka ? " (PayHere)" : " (Stripe, once connected)"} — a{" "}
                    {platformFeePercent}% transaction fee applies only when you make a sale.
                  </span>
                </span>
              </Label>
              <Label
                htmlFor="plan-pro"
                className="hover:bg-accent/50 flex cursor-pointer items-start gap-3 rounded-lg border p-3.5 has-[[data-state=checked]]:border-primary"
              >
                <RadioGroupItem value="pro" id="plan-pro" className="mt-0.5" />
                <span>
                  <span className="flex items-center gap-2">
                    <span className="text-sm font-medium">Pro</span>
                    <span className="text-muted-foreground text-xs">
                      {formatCurrency(proMonthlyPriceCents, currency)}/month
                    </span>
                  </span>
                  <span className="text-muted-foreground block text-xs">
                    Everything in Free, plus Cash on Delivery and Bank transfer as payment options.
                  </span>
                </span>
              </Label>
            </RadioGroup>
          </CardContent>
        </Card>

        <label className="flex items-start gap-3 text-sm">
          <Checkbox
            checked={agreeToTerms}
            onCheckedChange={(checked) => setValue("agreeToTerms", checked === true, { shouldValidate: true })}
          />
          <span className="text-muted-foreground">
            I confirm the information above is accurate and agree to {name}&apos;s seller terms.
          </span>
        </label>
        {errors.agreeToTerms ? (
          <p className="text-destructive -mt-4 text-xs">{errors.agreeToTerms.message}</p>
        ) : null}

        <Button type="submit" size="lg" className="w-full" disabled={mutation.isPending}>
          {mutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
          {sellerPlan === "pro" ? "Submit application & continue to payment" : "Submit application"}
        </Button>
        <p className="text-muted-foreground text-center text-xs">
          {sellerPlan === "pro"
            ? `Pro is ${formatCurrency(proMonthlyPriceCents, currency)}/month, billed via Stripe — cancel anytime.`
            : `A ${platformFeePercent}% transaction fee applies only when you make a sale — no monthly costs.`}
        </p>
      </form>
    </div>
  );
}
