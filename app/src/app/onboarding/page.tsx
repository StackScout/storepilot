"use client";

import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2, ShieldCheck, Store } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Card, CardContent } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { CATEGORIES } from "@/mock/categories";
import { SRI_LANKA_DISTRICTS } from "@/lib/constants";
import { createSellerSession } from "@/lib/actions/auth";
import { storesService } from "@/services";
import type { SellerType, StoreCategory } from "@/types";

const onboardingSchema = z
  .object({
    storeName: z.string().min(3, "Enter your store name"),
    category: z.string().min(1, "Select a category"),
    tagline: z.string().min(5, "Add a short tagline"),
    description: z.string().min(20, "Describe your store in a bit more detail (min 20 characters)"),
    city: z.string().min(2, "Enter your city/town"),
    district: z.string().min(1, "Select a district"),
    whatsappNumber: z.string().min(9, "Enter a valid WhatsApp number"),
    contactEmail: z.string().email("Enter a valid email"),
    sellerType: z.enum(["individual", "business"]),
    nicNumber: z.string().min(10, "Enter a valid NIC number"),
    businessRegistrationNumber: z.string().optional(),
    bankName: z.string().min(2, "Enter your bank name"),
    bankAccountName: z.string().min(2, "Enter the account holder name"),
    bankAccountNumber: z.string().min(4, "Enter the account number"),
    agreeToTerms: z.boolean().refine((v) => v, "You must agree to continue"),
  })
  .superRefine((data, ctx) => {
    if (data.sellerType === "business" && !data.businessRegistrationNumber?.trim()) {
      ctx.addIssue({
        code: "custom",
        path: ["businessRegistrationNumber"],
        message: "Enter your Business Registration number",
      });
    }
  });

type OnboardingFormValues = z.infer<typeof onboardingSchema>;

export default function OnboardingPage() {
  const router = useRouter();
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
      district: "",
      sellerType: "individual",
      agreeToTerms: false,
    },
  });

  const category = watch("category");
  const district = watch("district");
  const sellerType = watch("sellerType");
  const agreeToTerms = watch("agreeToTerms");

  const mutation = useMutation({
    mutationFn: async (values: OnboardingFormValues) => {
      // Store/settings creation must happen client-side: Server Actions run
      // in Node.js, where the localStorage-backed mock DB can't write (see
      // src/lib/mock-db.ts) — only the session cookie is set server-side.
      const store = await storesService.createStore({
        name: values.storeName,
        category: values.category as StoreCategory,
        tagline: values.tagline,
        description: values.description,
        city: values.city,
        district: values.district,
        whatsappNumber: values.whatsappNumber,
      });
      await storesService.updateStoreSettings(store.id, {
        contactEmail: values.contactEmail,
        contactPhone: values.whatsappNumber,
        bankName: values.bankName,
        bankAccountName: values.bankAccountName,
        bankAccountNumber: values.bankAccountNumber,
        sellerType: values.sellerType,
        nicNumber: values.nicNumber,
        businessRegistrationNumber: values.businessRegistrationNumber,
        codEnabled: true,
        onlinePaymentEnabled: true,
      });
      await createSellerSession(store.id, values.contactEmail);
    },
    onSuccess: () => {
      toast.success("Application submitted! Your store is pending review.");
      router.push("/dashboard");
    },
    onError: () => toast.error("Something went wrong submitting your application. Please try again."),
  });

  return (
    <div className="mx-auto max-w-lg px-4 py-12 sm:px-6">
      <div className="mb-6 space-y-2 text-center">
        <span className="bg-primary/10 text-primary mx-auto flex size-12 items-center justify-center rounded-full">
          <Store className="size-6" />
        </span>
        <h1 className="text-2xl font-bold">Start selling on IslandCart</h1>
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
              <Input id="storeName" placeholder="e.g. Kandy Handloom Co." {...register("storeName")} />
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
                    <SelectValue placeholder="Select category" />
                  </SelectTrigger>
                  <SelectContent>
                    {CATEGORIES.map((c) => (
                      <SelectItem key={c.value} value={c.value}>
                        {c.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {errors.category ? (
                  <p className="text-destructive text-xs">{errors.category.message}</p>
                ) : null}
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="district">District</Label>
                <Select
                  value={district}
                  onValueChange={(v) => setValue("district", v as string, { shouldValidate: true })}
                >
                  <SelectTrigger id="district" className="w-full">
                    <SelectValue placeholder="Select district" />
                  </SelectTrigger>
                  <SelectContent>
                    {SRI_LANKA_DISTRICTS.map((d) => (
                      <SelectItem key={d} value={d}>
                        {d}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {errors.district ? (
                  <p className="text-destructive text-xs">{errors.district.message}</p>
                ) : null}
              </div>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="city">City / town</Label>
              <Input id="city" placeholder="e.g. Kandy" {...register("city")} />
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
                    Has a Business Registration certificate from the Registrar of Companies
                  </span>
                </span>
              </Label>
            </RadioGroup>

            <div className="space-y-1.5">
              <Label htmlFor="nicNumber">NIC number</Label>
              <Input id="nicNumber" placeholder="e.g. 200012345678 or 851234567V" {...register("nicNumber")} />
              {errors.nicNumber ? (
                <p className="text-destructive text-xs">{errors.nicNumber.message}</p>
              ) : null}
            </div>

            {sellerType === "business" ? (
              <div className="space-y-1.5">
                <Label htmlFor="businessRegistrationNumber">Business Registration number</Label>
                <Input
                  id="businessRegistrationNumber"
                  placeholder="e.g. PV 00219845"
                  {...register("businessRegistrationNumber")}
                />
                {errors.businessRegistrationNumber ? (
                  <p className="text-destructive text-xs">
                    {errors.businessRegistrationNumber.message}
                  </p>
                ) : null}
              </div>
            ) : null}
          </CardContent>
        </Card>

        <Card>
          <CardContent className="space-y-4">
            <h2 className="font-semibold">Contact & payout details</h2>
            <div className="space-y-1.5">
              <Label htmlFor="whatsappNumber">WhatsApp number</Label>
              <Input id="whatsappNumber" placeholder="+94 7X XXX XXXX" {...register("whatsappNumber")} />
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
              <Input id="bankName" placeholder="e.g. Commercial Bank of Ceylon" {...register("bankName")} />
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
              Your account holder name should match your NIC/business registration name — payouts
              are held until this can be confirmed.
            </p>
          </CardContent>
        </Card>

        <label className="flex items-start gap-3 text-sm">
          <Checkbox
            checked={agreeToTerms}
            onCheckedChange={(checked) => setValue("agreeToTerms", checked === true, { shouldValidate: true })}
          />
          <span className="text-muted-foreground">
            I confirm the information above is accurate and agree to IslandCart&apos;s seller terms.
          </span>
        </label>
        {errors.agreeToTerms ? (
          <p className="text-destructive -mt-4 text-xs">{errors.agreeToTerms.message}</p>
        ) : null}

        <Button type="submit" size="lg" className="w-full" disabled={mutation.isPending}>
          {mutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
          Submit application
        </Button>
        <p className="text-muted-foreground text-center text-xs">
          A 3.5% transaction fee applies only when you make a sale — no monthly costs.
        </p>
      </form>
    </div>
  );
}
