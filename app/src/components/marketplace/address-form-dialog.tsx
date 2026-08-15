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
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useStates } from "@/hooks/use-platform-config";
import { addressesService } from "@/services";
import type { Address } from "@/types";

const addressSchema = z.object({
  label: z.string().optional(),
  fullName: z.string().min(2, "Enter the recipient's full name"),
  phone: z
    .string()
    .min(9, "Enter a valid phone number")
    .regex(/^[0-9+\s]+$/, "Digits only"),
  addressLine1: z.string().min(5, "Enter the delivery address"),
  city: z.string().min(2, "Enter a city/town"),
  state: z.string().min(1, "Select a state/province"),
  postalCode: z.string().min(4, "Enter a postal code"),
  isDefault: z.boolean(),
});

type AddressFormValues = z.infer<typeof addressSchema>;

const EMPTY_VALUES: AddressFormValues = {
  label: "",
  fullName: "",
  phone: "",
  addressLine1: "",
  city: "",
  state: "",
  postalCode: "",
  isDefault: false,
};

function toFormValues(address: Address): AddressFormValues {
  return {
    label: address.label ?? "",
    fullName: address.shipping.fullName ?? "",
    phone: address.shipping.phone ?? "",
    addressLine1: address.shipping.addressLine1 ?? "",
    city: address.shipping.city ?? "",
    state: address.shipping.state ?? "",
    postalCode: address.shipping.postalCode ?? "",
    isDefault: address.isDefault,
  };
}

/** Create or edit one entry in a buyer's saved address book (see /account's "Saved addresses" card) — [address] present means edit, absent means create. */
export function AddressFormDialog({ address }: { address?: Address }) {
  const [open, setOpen] = useState(false);
  const queryClient = useQueryClient();
  const initialValues = address ? toFormValues(address) : EMPTY_VALUES;

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    reset,
    formState: { errors },
  } = useForm<AddressFormValues>({
    resolver: zodResolver(addressSchema),
    defaultValues: initialValues,
  });

  const state = watch("state");
  const isDefault = watch("isDefault");
  const { data: states } = useStates();

  const mutation = useMutation({
    mutationFn: (values: AddressFormValues) => {
      const { label, isDefault, ...shipping } = values;
      const input = { label: label || undefined, shipping, isDefault };
      return address ? addressesService.updateAddress(address.id, input) : addressesService.createAddress(input);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["addresses"] });
      toast.success(address ? "Address updated" : "Address added");
      setOpen(false);
    },
    onError: () => toast.error("Couldn't save this address. Please try again."),
  });

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (next) reset(initialValues);
      }}
    >
      <DialogTrigger render={<Button type="button" variant="outline" size="sm" />}>
        {address ? (
          <>
            <Pencil className="size-3.5" /> Edit
          </>
        ) : (
          <>
            <Plus className="size-3.5" /> Add address
          </>
        )}
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{address ? "Edit address" : "Add a saved address"}</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit((values) => mutation.mutate(values))} className="space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="addr-label">Label (optional)</Label>
            <Input id="addr-label" placeholder="e.g. Home, Work" {...register("label")} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="addr-fullName">Full name</Label>
            <Input id="addr-fullName" {...register("fullName")} />
            {errors.fullName ? <p className="text-destructive text-xs">{errors.fullName.message}</p> : null}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="addr-phone">Phone number</Label>
            <Input id="addr-phone" placeholder="04XX XXX XXX" {...register("phone")} />
            {errors.phone ? <p className="text-destructive text-xs">{errors.phone.message}</p> : null}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="addr-addressLine1">Address</Label>
            <Input id="addr-addressLine1" placeholder="House no, street, area" {...register("addressLine1")} />
            {errors.addressLine1 ? (
              <p className="text-destructive text-xs">{errors.addressLine1.message}</p>
            ) : null}
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="addr-city">City</Label>
              <Input id="addr-city" placeholder="e.g. Parramatta" {...register("city")} />
              {errors.city ? <p className="text-destructive text-xs">{errors.city.message}</p> : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="addr-postalCode">Postal code</Label>
              <Input id="addr-postalCode" placeholder="e.g. 2150" {...register("postalCode")} />
              {errors.postalCode ? (
                <p className="text-destructive text-xs">{errors.postalCode.message}</p>
              ) : null}
            </div>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="addr-state">State/Province</Label>
            <Select
              value={state}
              onValueChange={(v) => setValue("state", v as string, { shouldValidate: true })}
            >
              <SelectTrigger id="addr-state" className="w-full">
                <SelectValue placeholder="Select a state/province" />
              </SelectTrigger>
              <SelectContent>
                {(states ?? []).map((s) => (
                  <SelectItem key={s.name} value={s.name}>
                    {s.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {errors.state ? <p className="text-destructive text-xs">{errors.state.message}</p> : null}
          </div>
          {!address?.isDefault ? (
            <label className="flex items-center gap-2.5">
              <Checkbox
                checked={isDefault}
                onCheckedChange={(checked) => setValue("isDefault", checked === true)}
              />
              <span className="text-sm">Set as default address</span>
            </label>
          ) : null}
          <DialogFooter>
            <DialogClose render={<Button type="button" variant="outline" />}>Cancel</DialogClose>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
              Save address
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
