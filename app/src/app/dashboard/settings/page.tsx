"use client";

import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { useSellerStoreId } from "@/hooks/use-seller-store";
import { storesService } from "@/services";

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
  })
  .refine((data) => data.codEnabled || data.onlinePaymentEnabled || data.bankTransferEnabled, {
    message: "Enable at least one payment method so buyers can check out",
    path: ["bankTransferEnabled"],
  });

type SettingsFormValues = z.infer<typeof settingsSchema>;

export default function DashboardSettingsPage() {
  const queryClient = useQueryClient();
  const storeId = useSellerStoreId();

  const { data: settings, isLoading } = useQuery({
    queryKey: ["store-settings", storeId],
    queryFn: () => storesService.getStoreSettings(storeId),
  });

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
    },
  });

  useEffect(() => {
    if (settings) {
      reset({
        contactEmail: settings.contactEmail,
        contactPhone: settings.contactPhone,
        bankName: settings.bankName,
        bankAccountName: settings.bankAccountName,
        bankAccountNumber: settings.bankAccountNumber,
        codEnabled: settings.codEnabled,
        onlinePaymentEnabled: settings.onlinePaymentEnabled,
        bankTransferEnabled: settings.bankTransferEnabled,
      });
    }
  }, [settings, reset]);

  const codEnabled = watch("codEnabled");
  const onlinePaymentEnabled = watch("onlinePaymentEnabled");
  const bankTransferEnabled = watch("bankTransferEnabled");

  const mutation = useMutation({
    mutationFn: (values: SettingsFormValues) =>
      storesService.updateStoreSettings(storeId, values),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["store-settings"] });
      toast.success("Settings saved");
    },
    onError: () => toast.error("Couldn't save settings. Please try again."),
  });

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
            {errors.bankTransferEnabled ? (
              <p className="text-destructive text-xs">{errors.bankTransferEnabled.message}</p>
            ) : null}
          </CardContent>
        </Card>

        <div className="flex justify-end">
          <Button type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            Save settings
          </Button>
        </div>
      </form>
    </div>
  );
}
