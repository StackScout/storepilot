"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { Banknote, CreditCard, Landmark, Loader2, Store as StoreIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { PriceDisplay } from "@/components/shared/price-display";
import { useAuthSession } from "@/hooks/use-auth-session";
import { formatCurrency } from "@/lib/currency";
import { usePlatformConfig } from "@/hooks/use-platform-config";
import { submitPayHereCheckout } from "@/lib/payhere";
import { availabilityService, bookingsService, buyersService, storesService } from "@/services";
import type { Booking, BookableService, PaymentMethod, SlotResponse, Store } from "@/types";

const bookingSchema = z.object({
  buyerName: z.string().min(2, "Enter your name"),
  buyerEmail: z.string().email("Enter a valid email"),
  buyerPhone: z
    .string()
    .min(9, "Enter a valid phone number")
    .regex(/^[0-9+\s]+$/, "Digits only"),
  paymentMethod: z.enum(["payhere", "cod", "bank-transfer", "stripe"]),
});

type BookingFormValues = z.infer<typeof bookingSchema>;

function formatSlotTime(iso: string): string {
  return new Date(iso).toLocaleTimeString("en-LK", { hour: "numeric", minute: "2-digit" });
}

function formatSlotDate(iso: string): { weekday: string; day: string } {
  const date = new Date(iso);
  return {
    weekday: date.toLocaleDateString("en-LK", { weekday: "short" }),
    day: date.toLocaleDateString("en-LK", { day: "numeric", month: "short" }),
  };
}

export function ServiceBookingForm({ service, store }: { service: BookableService; store: Store }) {
  const router = useRouter();
  const { session } = useAuthSession();
  const isSignedInBuyer = session.signedIn && session.role === "buyer";
  const { name, currencyCode, currencySymbol, currencyLocale, countryCode } = usePlatformConfig();
  const currency = { code: currencyCode, symbol: currencySymbol, locale: currencyLocale };
  const isSriLanka = countryCode === "LK";
  const isAustralia = countryCode === "AU";

  const { data: buyer } = useQuery({
    queryKey: ["buyer", "me"],
    queryFn: () => buyersService.getCurrentBuyer(),
    enabled: isSignedInBuyer,
  });

  const { data: storeSettings } = useQuery({
    queryKey: ["store-public-settings", store.id],
    queryFn: () => storesService.getPublicStoreSettings(store.id),
  });

  const { data: availability } = useQuery({
    queryKey: ["availability", "slots", store.id, service.id],
    queryFn: () => availabilityService.getSlots(store.id, service.id),
  });

  // "Pay at venue" (cod wire value) and bank-transfer are Pro-only; PayHere
  // is Sri Lanka-only, Stripe is Australia-only — identical gating to
  // checkout-form.tsx's paymentMethodEnabled.
  const codEnabled = storeSettings?.codEnabled ?? false;
  const onlinePaymentEnabled = isSriLanka && (storeSettings?.onlinePaymentEnabled ?? true);
  const bankTransferEnabled = storeSettings?.bankTransferEnabled ?? false;
  const stripeEnabled = isAustralia && ((storeSettings?.stripeEnabled && storeSettings?.stripeChargesEnabled) ?? false);
  const paymentMethodEnabled: Record<PaymentMethod, boolean> = {
    cod: codEnabled,
    payhere: onlinePaymentEnabled,
    "bank-transfer": bankTransferEnabled,
    stripe: stripeEnabled,
  };

  const daysWithSlots = (availability ?? []).filter((d) => d.slots.length > 0);
  const [selectedDate, setSelectedDate] = useState<string | null>(null);
  const [selectedSlot, setSelectedSlot] = useState<SlotResponse | null>(null);

  useEffect(() => {
    if (!selectedDate && daysWithSlots.length > 0) setSelectedDate(daysWithSlots[0].date);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only seed once availability first arrives
  }, [daysWithSlots.length]);

  const slotsForSelectedDate = daysWithSlots.find((d) => d.date === selectedDate)?.slots ?? [];

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    reset,
    formState: { errors },
  } = useForm<BookingFormValues>({
    resolver: zodResolver(bookingSchema),
    defaultValues: { paymentMethod: "cod", buyerEmail: session.email ?? "" },
  });

  useEffect(() => {
    if (!buyer) return;
    reset({
      buyerName: buyer.defaultShipping?.fullName ?? "",
      buyerEmail: buyer.email,
      buyerPhone: buyer.defaultShipping?.phone ?? "",
      paymentMethod: "cod",
    });
  }, [buyer, reset]);

  const paymentMethod = watch("paymentMethod");

  useEffect(() => {
    if (!storeSettings) return;
    if (paymentMethodEnabled[paymentMethod]) return;
    const fallback = (["cod", "payhere", "bank-transfer", "stripe"] as const).find((m) => paymentMethodEnabled[m]);
    if (fallback) setValue("paymentMethod", fallback);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- depend on the primitive flags, not the object literal recreated each render
  }, [storeSettings, codEnabled, onlinePaymentEnabled, bankTransferEnabled, stripeEnabled, paymentMethod, setValue]);

  const mutation = useMutation({
    mutationFn: (values: BookingFormValues) => {
      if (!selectedSlot) throw new Error("Select a time slot");
      return bookingsService.createBooking({
        storeId: store.id,
        serviceId: service.id,
        scheduledStart: selectedSlot.start,
        paymentMethod: values.paymentMethod,
        buyerName: values.buyerName,
        buyerPhone: values.buyerPhone,
        buyerEmail: values.buyerEmail,
      });
    },
    onSuccess: async (booking) => {
      const needsGatewayRedirect =
        (booking.paymentMethod === "payhere" || booking.paymentMethod === "stripe") &&
        booking.paymentStatus === "unpaid";
      if (!needsGatewayRedirect) {
        toast.success(
          booking.paymentMethod === "bank-transfer" ? "Booking requested! Upload your payment receipt to confirm it." : "Booking requested!",
        );
        router.push(`/bookings/${booking.id}`);
        return;
      }
      if (booking.paymentMethod === "stripe") {
        await startStripePayment(booking);
      } else {
        await startPayHerePayment(booking);
      }
    },
    onError: (error) =>
      toast.error(error instanceof Error && error.message === "Select a time slot" ? error.message : "Couldn't request this booking. The slot may no longer be available — please pick another."),
  });

  async function startPayHerePayment(booking: Booking) {
    try {
      const payload = await bookingsService.getPayHereCheckoutPayload(booking.id);
      submitPayHereCheckout(payload);
    } catch {
      toast.error("Couldn't start PayHere checkout. Your booking is saved — you can pay from the booking page.");
      router.push(`/bookings/${booking.id}`);
    }
  }

  async function startStripePayment(booking: Booking) {
    try {
      const { checkoutUrl } = await bookingsService.getStripeCheckoutUrl(booking.id);
      window.location.href = checkoutUrl;
    } catch {
      toast.error("Couldn't start Stripe checkout. Your booking is saved — you can pay from the booking page.");
      router.push(`/bookings/${booking.id}`);
    }
  }

  const noPaymentMethodsAvailable = !codEnabled && !onlinePaymentEnabled && !bankTransferEnabled && !stripeEnabled;

  return (
    <form onSubmit={handleSubmit((values) => mutation.mutate(values))} className="space-y-6">
      <Card>
        <CardContent className="space-y-4">
          <h2 className="font-semibold">Pick a time</h2>
          {!availability ? (
            <p className="text-muted-foreground text-sm">Loading availability…</p>
          ) : daysWithSlots.length === 0 ? (
            <p className="text-muted-foreground text-sm">
              No upcoming availability right now — message the store on WhatsApp to check for openings.
            </p>
          ) : (
            <>
              <div className="flex gap-2 overflow-x-auto pb-1">
                {daysWithSlots.map((day) => {
                  const { weekday, day: dayNum } = formatSlotDate(day.slots[0].start);
                  const isSelected = day.date === selectedDate;
                  return (
                    <button
                      key={day.date}
                      type="button"
                      onClick={() => {
                        setSelectedDate(day.date);
                        setSelectedSlot(null);
                      }}
                      className={
                        isSelected
                          ? "bg-primary text-primary-foreground shrink-0 rounded-lg px-3.5 py-2 text-center text-xs font-medium"
                          : "hover:bg-accent shrink-0 rounded-lg border px-3.5 py-2 text-center text-xs font-medium"
                      }
                    >
                      <span className="block">{weekday}</span>
                      <span className="block">{dayNum}</span>
                    </button>
                  );
                })}
              </div>

              <div className="grid grid-cols-3 gap-2 sm:grid-cols-4">
                {slotsForSelectedDate.map((slot) => {
                  const isSelected = selectedSlot?.start === slot.start;
                  return (
                    <button
                      key={slot.start}
                      type="button"
                      onClick={() => setSelectedSlot(slot)}
                      className={
                        isSelected
                          ? "bg-primary text-primary-foreground rounded-md px-2 py-2 text-sm font-medium"
                          : "hover:bg-accent rounded-md border px-2 py-2 text-sm font-medium"
                      }
                    >
                      {formatSlotTime(slot.start)}
                    </button>
                  );
                })}
              </div>
            </>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardContent className="space-y-4">
          <h2 className="font-semibold">Your details</h2>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5 sm:col-span-2">
              <Label htmlFor="buyerName">Full name</Label>
              <Input id="buyerName" placeholder="e.g. Jack Thompson" {...register("buyerName")} />
              {errors.buyerName ? <p className="text-destructive text-xs">{errors.buyerName.message}</p> : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="buyerEmail">Email</Label>
              <Input id="buyerEmail" type="email" placeholder="you@example.com" {...register("buyerEmail")} />
              {errors.buyerEmail ? <p className="text-destructive text-xs">{errors.buyerEmail.message}</p> : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="buyerPhone">Phone number</Label>
              <Input id="buyerPhone" placeholder="04XX XXX XXX" {...register("buyerPhone")} />
              {errors.buyerPhone ? <p className="text-destructive text-xs">{errors.buyerPhone.message}</p> : null}
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="space-y-4">
          <h2 className="font-semibold">Payment method</h2>
          {noPaymentMethodsAvailable ? (
            <p className="text-destructive text-sm">This store isn&apos;t accepting bookings right now.</p>
          ) : (
            <RadioGroup
              value={paymentMethod}
              onValueChange={(v) => setValue("paymentMethod", v as PaymentMethod)}
              className="gap-3"
            >
              {codEnabled ? (
                <Label
                  htmlFor="booking-cod"
                  className="hover:bg-accent/50 flex cursor-pointer items-start gap-3 rounded-lg border p-3.5 has-[[data-state=checked]]:border-primary"
                >
                  <RadioGroupItem value="cod" id="booking-cod" className="mt-0.5" />
                  <span className="flex flex-1 items-start gap-2.5">
                    <StoreIcon className="mt-0.5 size-4 shrink-0" />
                    <span>
                      <span className="block text-sm font-medium">Pay at venue</span>
                      <span className="text-muted-foreground block text-xs">Pay when you arrive for your appointment</span>
                    </span>
                  </span>
                </Label>
              ) : null}
              {onlinePaymentEnabled ? (
                <Label
                  htmlFor="booking-payhere"
                  className="hover:bg-accent/50 flex cursor-pointer items-start gap-3 rounded-lg border p-3.5 has-[[data-state=checked]]:border-primary"
                >
                  <RadioGroupItem value="payhere" id="booking-payhere" className="mt-0.5" />
                  <span className="flex flex-1 items-start gap-2.5">
                    <Landmark className="mt-0.5 size-4 shrink-0" />
                    <span>
                      <span className="block text-sm font-medium">Pay online with PayHere</span>
                      <span className="text-muted-foreground block text-xs">Card, LankaQR, eZ Cash or mCash</span>
                    </span>
                  </span>
                </Label>
              ) : null}
              {stripeEnabled ? (
                <Label
                  htmlFor="booking-stripe"
                  className="hover:bg-accent/50 flex cursor-pointer items-start gap-3 rounded-lg border p-3.5 has-[[data-state=checked]]:border-primary"
                >
                  <RadioGroupItem value="stripe" id="booking-stripe" className="mt-0.5" />
                  <span className="flex flex-1 items-start gap-2.5">
                    <CreditCard className="mt-0.5 size-4 shrink-0" />
                    <span>
                      <span className="block text-sm font-medium">Pay online with Stripe</span>
                      <span className="text-muted-foreground block text-xs">Credit or debit card</span>
                    </span>
                  </span>
                </Label>
              ) : null}
              {bankTransferEnabled ? (
                <Label
                  htmlFor="booking-bank-transfer"
                  className="hover:bg-accent/50 flex cursor-pointer items-start gap-3 rounded-lg border p-3.5 has-[[data-state=checked]]:border-primary"
                >
                  <RadioGroupItem value="bank-transfer" id="booking-bank-transfer" className="mt-0.5" />
                  <span className="flex flex-1 items-start gap-2.5">
                    <Banknote className="mt-0.5 size-4 shrink-0" />
                    <span>
                      <span className="block text-sm font-medium">Bank transfer</span>
                      <span className="text-muted-foreground block text-xs">Transfer directly, then upload your receipt</span>
                    </span>
                  </span>
                </Label>
              ) : null}
            </RadioGroup>
          )}
          {paymentMethod === "bank-transfer" && storeSettings ? (
            <div className="bg-muted/50 space-y-1 rounded-lg border p-3.5 text-sm">
              <p className="font-medium">Transfer {formatCurrency(service.price, currency)} to:</p>
              <p>{storeSettings.bankName}</p>
              <p>{storeSettings.bankAccountName}</p>
              <p className="font-mono">{storeSettings.bankAccountNumber}</p>
            </div>
          ) : null}
        </CardContent>
      </Card>

      <Card>
        <CardContent className="space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-muted-foreground text-sm">{service.name}</span>
            <PriceDisplay price={service.price} size="sm" />
          </div>
          <Button
            type="submit"
            size="lg"
            className="w-full"
            disabled={mutation.isPending || !selectedSlot || noPaymentMethodsAvailable}
          >
            {mutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            Request booking
          </Button>
          <p className="text-muted-foreground text-center text-xs">
            By requesting this booking you agree to {name}&apos;s terms.
          </p>
        </CardContent>
      </Card>
    </form>
  );
}
