"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2, Pencil, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { usePlatformConfig } from "@/hooks/use-platform-config";
import { couponsService } from "@/services";
import type { Coupon, CouponInput } from "@/types";

const couponSchema = z
  .object({
    code: z.string().min(2, "Enter a coupon code"),
    discountType: z.enum(["percent", "fixed"]),
    discountValue: z.number().positive("Enter a discount amount"),
    appliesToOrders: z.boolean(),
    appliesToBookings: z.boolean(),
    maxUses: z.number().int().positive().optional(),
    minSubtotal: z.number().min(0),
    expiresAt: z.string().optional(),
    active: z.boolean(),
  })
  .refine((v) => v.appliesToOrders || v.appliesToBookings, {
    message: "Must apply to at least one of orders or bookings",
    path: ["appliesToBookings"],
  })
  .refine((v) => v.discountType !== "percent" || v.discountValue <= 100, {
    message: "A percent discount can't exceed 100",
    path: ["discountValue"],
  });

type CouponFormValues = z.infer<typeof couponSchema>;

const EMPTY_VALUES: CouponFormValues = {
  code: "",
  discountType: "percent",
  discountValue: 10,
  appliesToOrders: true,
  appliesToBookings: true,
  maxUses: undefined,
  minSubtotal: 0,
  expiresAt: undefined,
  active: true,
};

function toFormValues(coupon: Coupon): CouponFormValues {
  return {
    code: coupon.code,
    discountType: coupon.discountType,
    discountValue: coupon.discountType === "fixed" ? coupon.discountValue / 100 : coupon.discountValue,
    appliesToOrders: coupon.appliesToOrders,
    appliesToBookings: coupon.appliesToBookings,
    maxUses: coupon.maxUses,
    minSubtotal: coupon.minSubtotal / 100,
    expiresAt: coupon.expiresAt ? coupon.expiresAt.slice(0, 10) : undefined,
    active: coupon.active,
  };
}

function toInput(values: CouponFormValues): CouponInput {
  return {
    code: values.code.trim(),
    discountType: values.discountType,
    discountValue: values.discountType === "fixed" ? Math.round(values.discountValue * 100) : Math.round(values.discountValue),
    appliesToOrders: values.appliesToOrders,
    appliesToBookings: values.appliesToBookings,
    maxUses: values.maxUses,
    minSubtotal: Math.round(values.minSubtotal * 100),
    expiresAt: values.expiresAt ? new Date(`${values.expiresAt}T23:59:59`).toISOString() : undefined,
    active: values.active,
  };
}

/**
 * Create or edit a coupon — [coupon] present means edit, absent means
 * create. [scope]/[storeId] pick which backend surface to call:
 * store-specific coupons (seller dashboard) vs platform-wide ones (admin),
 * see CouponService's dual-scoped CRUD.
 */
export function CouponFormDialog({
  coupon,
  scope,
  storeId,
}: {
  coupon?: Coupon;
  scope: "store" | "platform";
  storeId?: string;
}) {
  const [open, setOpen] = useState(false);
  const queryClient = useQueryClient();
  const { currencyCode } = usePlatformConfig();
  const initialValues = coupon ? toFormValues(coupon) : EMPTY_VALUES;

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    reset,
    formState: { errors },
  } = useForm<CouponFormValues>({
    resolver: zodResolver(couponSchema),
    defaultValues: initialValues,
  });

  const discountType = watch("discountType");
  const appliesToOrders = watch("appliesToOrders");
  const appliesToBookings = watch("appliesToBookings");
  const active = watch("active");

  const mutation = useMutation({
    mutationFn: (values: CouponFormValues) => {
      const input = toInput(values);
      if (coupon) {
        return scope === "store" ? couponsService.updateStoreCoupon(coupon.id, input) : couponsService.updatePlatformCoupon(coupon.id, input);
      }
      return scope === "store"
        ? couponsService.createStoreCoupon(requireNotNullStoreId(storeId), input)
        : couponsService.createPlatformCoupon(input);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["coupons"] });
      toast.success(coupon ? "Coupon updated" : "Coupon created");
      setOpen(false);
    },
    onError: () => toast.error("Couldn't save this coupon. Please try again."),
  });

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (next) reset(initialValues);
      }}
    >
      <DialogTrigger render={<Button type="button" variant={coupon ? "ghost" : "default"} size={coupon ? "icon" : "default"} className={coupon ? "size-8" : undefined} />}>
        {coupon ? (
          <Pencil className="size-3.5" />
        ) : (
          <>
            <Plus className="size-4" /> New coupon
          </>
        )}
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{coupon ? "Edit coupon" : "New coupon"}</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit((values) => mutation.mutate(values))} className="space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="couponCode">Code</Label>
            <Input id="couponCode" placeholder="e.g. WELCOME10" className="uppercase" {...register("code")} />
            {errors.code ? <p className="text-destructive text-xs">{errors.code.message}</p> : null}
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="discountType">Discount type</Label>
              <Select value={discountType} onValueChange={(v) => setValue("discountType", v as "percent" | "fixed")}>
                <SelectTrigger id="discountType">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="percent">Percent off</SelectItem>
                  <SelectItem value="fixed">Fixed amount off</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="discountValue">{discountType === "percent" ? "Percent" : `Amount (${currencyCode})`}</Label>
              <Input id="discountValue" type="number" step={discountType === "percent" ? "1" : "0.01"} {...register("discountValue", { valueAsNumber: true })} />
              {errors.discountValue ? <p className="text-destructive text-xs">{errors.discountValue.message}</p> : null}
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="maxUses">Max uses (optional)</Label>
              <Input id="maxUses" type="number" step="1" placeholder="Unlimited" {...register("maxUses", { valueAsNumber: true })} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="minSubtotal">Min spend ({currencyCode})</Label>
              <Input id="minSubtotal" type="number" step="0.01" {...register("minSubtotal", { valueAsNumber: true })} />
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="expiresAt">Expires (optional)</Label>
            <Input id="expiresAt" type="date" {...register("expiresAt")} />
          </div>

          <div className="space-y-2 pt-1">
            <Label htmlFor="appliesToOrders" className="flex cursor-pointer items-center gap-2">
              <Checkbox id="appliesToOrders" checked={appliesToOrders} onCheckedChange={(c) => setValue("appliesToOrders", c === true)} />
              Applies to product orders
            </Label>
            <Label htmlFor="appliesToBookings" className="flex cursor-pointer items-center gap-2">
              <Checkbox id="appliesToBookings" checked={appliesToBookings} onCheckedChange={(c) => setValue("appliesToBookings", c === true)} />
              Applies to bookings
            </Label>
            {errors.appliesToBookings ? <p className="text-destructive text-xs">{errors.appliesToBookings.message}</p> : null}
            <Label htmlFor="active" className="flex cursor-pointer items-center gap-2">
              <Checkbox id="active" checked={active} onCheckedChange={(c) => setValue("active", c === true)} />
              Active
            </Label>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
              {coupon ? "Save" : "Create"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function requireNotNullStoreId(storeId?: string): string {
  if (!storeId) throw new Error("storeId is required for scope=\"store\"");
  return storeId;
}
