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
import { SRI_LANKA_DISTRICTS } from "@/lib/constants";
import { buyersService } from "@/services";
import type { ShippingDetails } from "@/types";

const addressSchema = z.object({
  fullName: z.string().min(2, "Enter the recipient's full name"),
  phone: z
    .string()
    .min(9, "Enter a valid Sri Lankan phone number")
    .regex(/^[0-9+\s]+$/, "Digits only"),
  addressLine1: z.string().min(5, "Enter the delivery address"),
  city: z.string().min(2, "Enter a city/town"),
  district: z.string().min(1, "Select a district"),
  postalCode: z.string().min(4, "Enter a postal code"),
});

type AddressFormValues = z.infer<typeof addressSchema>;

const EMPTY_VALUES: AddressFormValues = {
  fullName: "",
  phone: "",
  addressLine1: "",
  city: "",
  district: "",
  postalCode: "",
};

/** Lets a buyer explicitly set/edit their saved default shipping address from the account page — the only other way it's set is automatically, on their first checkout (see checkout-form.tsx). */
export function EditAddressDialog({ defaultShipping }: { defaultShipping?: ShippingDetails }) {
  const [open, setOpen] = useState(false);
  const queryClient = useQueryClient();

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    reset,
    formState: { errors },
  } = useForm<AddressFormValues>({
    resolver: zodResolver(addressSchema),
    defaultValues: defaultShipping ?? EMPTY_VALUES,
  });

  const district = watch("district");

  const mutation = useMutation({
    mutationFn: (values: AddressFormValues) => buyersService.updateDefaultShipping(values),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["buyer", "me"] });
      toast.success("Address updated");
      setOpen(false);
    },
    onError: () => toast.error("Couldn't update your address. Please try again."),
  });

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (next) reset(defaultShipping ?? EMPTY_VALUES);
      }}
    >
      <DialogTrigger render={<Button type="button" variant="outline" size="sm" />}>
        {defaultShipping ? (
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
          <DialogTitle>{defaultShipping ? "Edit saved address" : "Add a saved address"}</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit((values) => mutation.mutate(values))} className="space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="edit-fullName">Full name</Label>
            <Input id="edit-fullName" {...register("fullName")} />
            {errors.fullName ? <p className="text-destructive text-xs">{errors.fullName.message}</p> : null}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="edit-phone">Phone number</Label>
            <Input id="edit-phone" placeholder="07X XXX XXXX" {...register("phone")} />
            {errors.phone ? <p className="text-destructive text-xs">{errors.phone.message}</p> : null}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="edit-addressLine1">Address</Label>
            <Input id="edit-addressLine1" placeholder="House no, street, area" {...register("addressLine1")} />
            {errors.addressLine1 ? (
              <p className="text-destructive text-xs">{errors.addressLine1.message}</p>
            ) : null}
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="edit-city">City</Label>
              <Input id="edit-city" placeholder="e.g. Dehiwala" {...register("city")} />
              {errors.city ? <p className="text-destructive text-xs">{errors.city.message}</p> : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="edit-postalCode">Postal code</Label>
              <Input id="edit-postalCode" placeholder="e.g. 10350" {...register("postalCode")} />
              {errors.postalCode ? (
                <p className="text-destructive text-xs">{errors.postalCode.message}</p>
              ) : null}
            </div>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="edit-district">District</Label>
            <Select
              value={district}
              onValueChange={(v) => setValue("district", v as string, { shouldValidate: true })}
            >
              <SelectTrigger id="edit-district" className="w-full">
                <SelectValue placeholder="Select a district" />
              </SelectTrigger>
              <SelectContent>
                {SRI_LANKA_DISTRICTS.map((d) => (
                  <SelectItem key={d} value={d}>
                    {d}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {errors.district ? <p className="text-destructive text-xs">{errors.district.message}</p> : null}
          </div>
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
