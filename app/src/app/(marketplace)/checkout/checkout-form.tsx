"use client";

import { useEffect, useRef } from "react";
import { useRouter } from "next/navigation";
import Image from "next/image";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { Banknote, CreditCard, Landmark, MapPin, Truck, Loader2, TriangleAlert, UserCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { PriceDisplay } from "@/components/shared/price-display";
import { useCart } from "@/hooks/use-cart";
import { useCartReconciliation } from "@/hooks/use-cart-reconciliation";
import { useAuthSession } from "@/hooks/use-auth-session";
import { cn } from "@/lib/utils";
import { formatCurrency } from "@/lib/currency";
import { usePlatformConfig, useStates } from "@/hooks/use-platform-config";
import { submitPayHereCheckout } from "@/lib/payhere";
import { PENDING_GATEWAY_ORDER_KEY } from "@/lib/constants";
import { ordersService, buyersService, storesService } from "@/services";
import type { DeliveryMethod, Order, PaymentMethod } from "@/types";

const checkoutSchema = z
  .object({
    fullName: z.string().min(2, "Enter the recipient's full name"),
    email: z.string().email("Enter a valid email"),
    phone: z
      .string()
      .min(9, "Enter a valid phone number")
      .regex(/^[0-9+\s]+$/, "Digits only"),
    // Only required for deliveryMethod === "shipping" — see the
    // superRefine below. A pickup order has no address at all.
    addressLine1: z.string().optional(),
    city: z.string().optional(),
    state: z.string().optional(),
    postalCode: z.string().optional(),
    paymentMethod: z.enum(["payhere", "cod", "bank-transfer", "stripe"]),
    deliveryMethod: z.enum(["shipping", "pickup"]),
  })
  .superRefine((data, ctx) => {
    if (data.deliveryMethod !== "shipping") return;
    if (!data.addressLine1 || data.addressLine1.length < 5) {
      ctx.addIssue({ code: "custom", path: ["addressLine1"], message: "Enter the delivery address" });
    }
    if (!data.city || data.city.length < 2) {
      ctx.addIssue({ code: "custom", path: ["city"], message: "Enter a city/town" });
    }
    if (!data.state) {
      ctx.addIssue({ code: "custom", path: ["state"], message: "Select a state/province" });
    }
    if (!data.postalCode || data.postalCode.length < 4) {
      ctx.addIssue({ code: "custom", path: ["postalCode"], message: "Enter a postal code" });
    }
  });

type CheckoutFormValues = z.infer<typeof checkoutSchema>;

export function CheckoutForm() {
  const router = useRouter();
  const { cart, subtotal, isHydrated, clearCart } = useCart();
  useCartReconciliation();
  const availableItems = cart.items.filter((i) => !i.isUnavailable);
  const hasUnavailable = cart.items.some((i) => i.isUnavailable);

  const { session } = useAuthSession();
  const isSignedInBuyer = session.signedIn && session.role === "buyer";
  const { name, countryCode, currencyCode, currencySymbol, currencyLocale, flatShippingFee } = usePlatformConfig();
  const currency = { code: currencyCode, symbol: currencySymbol, locale: currencyLocale };
  // PayHere and Stripe are each temporarily restricted to their home
  // market — PayHere (Sri Lanka) and Stripe (Australia) — see checkout's
  // paymentMethodEnabled below.
  const isSriLanka = countryCode === "LK";
  const isAustralia = countryCode === "AU";
  const { data: states } = useStates();

  const { data: buyer } = useQuery({
    queryKey: ["buyer", "me"],
    queryFn: () => buyersService.getCurrentBuyer(),
    enabled: isSignedInBuyer,
  });

  // Which payment methods this store accepts — set in the seller's Store
  // Settings. Defaults to both enabled while loading so the form doesn't
  // flash empty; the backend guarantees at least one is always true.
  const { data: storeSettings } = useQuery({
    queryKey: ["store-public-settings", cart.storeId],
    queryFn: () => storesService.getPublicStoreSettings(cart.storeId!),
    enabled: !!cart.storeId,
  });
  // Store contact info shown on the pickup card (WhatsApp + city/state) —
  // there's no separate pickup-location entity, buyers coordinate the
  // actual meeting point/time with the seller directly, matching this
  // marketplace's existing WhatsApp-first contact model.
  const { data: store } = useQuery({
    queryKey: ["store", cart.storeId],
    queryFn: () => storesService.getStoreById(cart.storeId!),
    enabled: !!cart.storeId,
  });
  const pickupEnabled = storeSettings?.pickupEnabled ?? false;
  const codEnabled = storeSettings?.codEnabled ?? true;
  // PayHere is temporarily Sri Lanka-only, regardless of the store's own
  // toggle — see the isSriLanka/isAustralia comment above.
  const onlinePaymentEnabled = isSriLanka && (storeSettings?.onlinePaymentEnabled ?? true);
  // Off by default while settings load, matching the backend's own default —
  // unlike COD/PayHere this is opt-in, so it shouldn't flash on then off.
  const bankTransferEnabled = storeSettings?.bankTransferEnabled ?? false;
  // Stripe needs both the seller's own toggle AND a fully-connected account
  // (stripeChargesEnabled, synced from Stripe via webhook) — never offer it
  // just because the seller flipped the switch before finishing onboarding.
  // Also temporarily Australia-only, same as PayHere/Sri Lanka above.
  const stripeEnabled = isAustralia && ((storeSettings?.stripeEnabled && storeSettings?.stripeChargesEnabled) ?? false);
  const paymentMethodEnabled: Record<PaymentMethod, boolean> = {
    cod: codEnabled,
    payhere: onlinePaymentEnabled,
    "bank-transfer": bankTransferEnabled,
    stripe: stripeEnabled,
  };

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    reset,
    formState: { errors },
  } = useForm<CheckoutFormValues>({
    resolver: zodResolver(checkoutSchema),
    defaultValues: { state: "", paymentMethod: "cod", deliveryMethod: "shipping", email: session.email ?? "" },
  });

  // Prefill from the signed-in buyer's saved address once it loads — a
  // guest, or a buyer with no saved address yet, just sees the empty form.
  useEffect(() => {
    if (!buyer?.defaultShipping) return;
    reset({
      fullName: buyer.defaultShipping.fullName,
      email: buyer.email,
      phone: buyer.defaultShipping.phone,
      addressLine1: buyer.defaultShipping.addressLine1,
      city: buyer.defaultShipping.city,
      state: buyer.defaultShipping.state,
      postalCode: buyer.defaultShipping.postalCode,
      paymentMethod: "cod",
      deliveryMethod: "shipping",
    });
  }, [buyer, reset]);

  // useAuthSession()'s data arrives asynchronously (a client fetch, unlike
  // the old server-rendered session), so the email default above is only
  // correct once this resolves — cover the case of a signed-in buyer with
  // no saved address yet (the effect above never fires for them).
  useEffect(() => {
    if (buyer?.defaultShipping || !session.email) return;
    setValue("email", session.email);
  }, [session.email, buyer, setValue]);

  const state = watch("state");
  const paymentMethod = watch("paymentMethod");
  const deliveryMethod = watch("deliveryMethod");

  // Pickup always defaults to "shipping" (see defaultValues/reset above) —
  // this only matters if the store's pickupEnabled flips off after the
  // buyer already had it selected (e.g. settings still loading).
  useEffect(() => {
    if (!storeSettings) return;
    if (deliveryMethod === "pickup" && !pickupEnabled) setValue("deliveryMethod", "shipping");
  }, [storeSettings, pickupEnabled, deliveryMethod, setValue]);

  // If the selected method isn't actually offered by this store (including
  // right after the buyer-prefill reset above, which always defaults to
  // "cod"), fall back to whichever one is enabled.
  useEffect(() => {
    if (!storeSettings) return;
    if (paymentMethodEnabled[paymentMethod]) return;
    const fallback = (["cod", "payhere", "bank-transfer", "stripe"] as const).find((m) => paymentMethodEnabled[m]);
    if (fallback) setValue("paymentMethod", fallback);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- depend on the primitive flags, not the object literal recreated each render
  }, [storeSettings, codEnabled, onlinePaymentEnabled, bankTransferEnabled, stripeEnabled, paymentMethod, setValue]);

  // Set synchronously (before clearCart) so the empty-cart redirect effect
  // below can't race the post-order navigation to /orders/[id].
  const orderPlacedRef = useRef(false);

  const mutation = useMutation({
    mutationFn: async (values: CheckoutFormValues) => {
      const isPickup = values.deliveryMethod === "pickup";
      const shipping = {
        fullName: values.fullName,
        phone: values.phone,
        // Omitted for pickup — there's no address to send.
        addressLine1: isPickup ? undefined : values.addressLine1,
        city: isPickup ? undefined : values.city,
        state: isPickup ? undefined : values.state,
        postalCode: isPickup ? undefined : values.postalCode,
      };
      const order = await ordersService.createOrder({
        storeId: cart.storeId!,
        items: availableItems.map((i) => ({ productId: i.productId, quantity: i.quantity })),
        shipping,
        paymentMethod: values.paymentMethod as PaymentMethod,
        deliveryMethod: values.deliveryMethod,
        email: values.email,
      });
      // Auto-save this address as the buyer's default, but only the first
      // time (no saved address yet) — once they have one, only an explicit
      // edit on the account page should change it, not whatever they
      // happened to type for one particular order (e.g. shipping a gift
      // elsewhere). Skipped entirely for pickup — there's no real address
      // to save. Awaited — not fire-and-forget — because the PayHere
      // path below navigates the browser away immediately afterwards
      // (submitPayHereCheckout does a real form submit, not client routing),
      // which would otherwise abort this request mid-flight. Still
      // best-effort: a failure here must never block order placement, and
      // the order itself is linked to the signed-in buyer server-side, from
      // the auth cookie — never a client-supplied id.
      if (!isPickup && isSignedInBuyer && !buyer?.defaultShipping) {
        try {
          await buyersService.updateDefaultShipping(shipping);
        } catch {
          // ignore — see comment above
        }
      }
      return order;
    },
    onSuccess: async (order) => {
      orderPlacedRef.current = true;

      // COD: the order is the whole flow — done. Bank transfer: the buyer
      // still needs to upload a receipt, from the order page. PayHere/
      // Stripe: the order now exists (unpaid, stock already reserved) but
      // payment itself still needs to happen at the gateway before it's
      // confirmed.
      const needsGatewayRedirect =
        (order.paymentMethod === "payhere" || order.paymentMethod === "stripe") &&
        order.paymentStatus === "unpaid";
      if (!needsGatewayRedirect) {
        // No further payment step — safe to clear now.
        clearCart();
        toast.success(
          order.paymentMethod === "bank-transfer"
            ? "Order placed! Upload your payment receipt to confirm it."
            : "Order placed!",
        );
        router.push(`/orders/${order.id}`);
        return;
      }
      // Payment isn't confirmed yet — deliberately don't clear the cart
      // here. If the gateway declines/cancels the payment (e.g. amount
      // over PayHere's limit), the buyer comes back to a stuck unpaid
      // order and needs their cart intact to retry. The order page clears
      // it once this specific order comes back paid.
      if (order.paymentMethod === "stripe") {
        await startStripePayment(order);
      } else {
        await startPayHerePayment(order);
      }
    },
    onError: () => toast.error("Something went wrong placing your order. Please try again."),
  });

  async function startPayHerePayment(order: Order) {
    try {
      const payload = await ordersService.getPayHereCheckoutPayload(order.id);
      // Navigates the browser away to PayHere's gateway page immediately —
      // there's no popup/callback to wait on. PayHere redirects back to
      // payload.returnUrl (== /orders/[order.id]) once the buyer finishes or
      // cancels; that page shows live status fetched from the backend,
      // updated by the server-to-server notify webhook.
      sessionStorage.setItem(PENDING_GATEWAY_ORDER_KEY, order.id);
      submitPayHereCheckout(payload);
    } catch (err) {
      console.error("PayHere checkout failed:", err);
      toast.error("Couldn't start PayHere checkout. Your order is saved — you can pay from the order page.");
      router.push(`/orders/${order.id}`);
    }
  }

  async function startStripePayment(order: Order) {
    try {
      const { checkoutUrl } = await ordersService.getStripeCheckoutUrl(order.id);
      // Navigates the browser away to Stripe's hosted Checkout page
      // immediately — no client-side Stripe.js needed, the backend already
      // returns a ready-to-redirect URL. Stripe redirects back to
      // /orders/[order.id] once the buyer finishes or cancels; that page
      // shows live status fetched from the backend, updated by the
      // server-to-server webhook.
      sessionStorage.setItem(PENDING_GATEWAY_ORDER_KEY, order.id);
      window.location.href = checkoutUrl;
    } catch (err) {
      console.error("Stripe checkout failed:", err);
      toast.error("Couldn't start Stripe checkout. Your order is saved — you can pay from the order page.");
      router.push(`/orders/${order.id}`);
    }
  }

  useEffect(() => {
    if (isHydrated && cart.items.length === 0 && !orderPlacedRef.current) {
      router.replace("/cart");
    }
  }, [isHydrated, cart.items.length, router]);

  if (!isHydrated || cart.items.length === 0) {
    return null;
  }

  return (
    <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6 lg:px-8">
      <h1 className="text-2xl font-bold">Checkout</h1>

      <form
        onSubmit={handleSubmit((values) => mutation.mutate(values))}
        className="mt-6 grid gap-8 lg:grid-cols-3"
      >
        <div className="space-y-6 lg:col-span-2">
          <Card>
            <CardContent className="space-y-4">
              <h2 className="font-semibold">Delivery details</h2>
              {buyer?.defaultShipping && deliveryMethod === "shipping" ? (
                <div className="bg-primary/5 text-primary flex items-center gap-2 rounded-md p-2.5 text-xs">
                  <UserCheck className="size-3.5 shrink-0" />
                  Prefilled from your saved address — feel free to edit it below.
                </div>
              ) : null}

              {pickupEnabled ? (
                <RadioGroup
                  value={deliveryMethod}
                  onValueChange={(v) => setValue("deliveryMethod", v as DeliveryMethod, { shouldValidate: true })}
                  className="grid gap-3 sm:grid-cols-2"
                >
                  <Label
                    htmlFor="delivery-shipping"
                    className="hover:bg-accent/50 flex cursor-pointer items-start gap-3 rounded-lg border p-3.5 has-[[data-state=checked]]:border-primary"
                  >
                    <RadioGroupItem value="shipping" id="delivery-shipping" className="mt-0.5" />
                    <span className="flex flex-1 items-start gap-2.5">
                      <Truck className="mt-0.5 size-4 shrink-0" />
                      <span>
                        <span className="block text-sm font-medium">Ship to my address</span>
                        <span className="text-muted-foreground block text-xs">
                          {formatCurrency(flatShippingFee, currency)} delivery fee
                        </span>
                      </span>
                    </span>
                  </Label>
                  <Label
                    htmlFor="delivery-pickup"
                    className="hover:bg-accent/50 flex cursor-pointer items-start gap-3 rounded-lg border p-3.5 has-[[data-state=checked]]:border-primary"
                  >
                    <RadioGroupItem value="pickup" id="delivery-pickup" className="mt-0.5" />
                    <span className="flex flex-1 items-start gap-2.5">
                      <MapPin className="mt-0.5 size-4 shrink-0" />
                      <span>
                        <span className="block text-sm font-medium">Pickup in store</span>
                        <span className="text-muted-foreground block text-xs">Free — collect it yourself</span>
                      </span>
                    </span>
                  </Label>
                </RadioGroup>
              ) : null}

              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-1.5 sm:col-span-2">
                  <Label htmlFor="fullName">Full name</Label>
                  <Input id="fullName" placeholder="e.g. Jack Thompson" {...register("fullName")} />
                  {errors.fullName ? (
                    <p className="text-destructive text-xs">{errors.fullName.message}</p>
                  ) : null}
                </div>
                <div className="space-y-1.5 sm:col-span-2">
                  <Label htmlFor="email">Email</Label>
                  <Input
                    id="email"
                    type="email"
                    placeholder="you@example.com"
                    {...register("email")}
                  />
                  {errors.email ? (
                    <p className="text-destructive text-xs">{errors.email.message}</p>
                  ) : (
                    <p className="text-muted-foreground text-xs">We&apos;ll send your order receipt here.</p>
                  )}
                </div>
                <div className="space-y-1.5 sm:col-span-2">
                  <Label htmlFor="phone">Phone number</Label>
                  <Input id="phone" placeholder="04XX XXX XXX" {...register("phone")} />
                  {errors.phone ? (
                    <p className="text-destructive text-xs">{errors.phone.message}</p>
                  ) : null}
                </div>

                {deliveryMethod === "pickup" ? (
                  <div className="bg-muted/50 space-y-1 rounded-lg border p-3.5 text-sm sm:col-span-2">
                    <p className="font-medium">Collect from {store?.name ?? "the seller"}</p>
                    {store ? (
                      <p className="text-muted-foreground">
                        {store.address.city}, {store.address.state}
                      </p>
                    ) : null}
                    <p className="text-muted-foreground pt-1 text-xs">
                      After placing your order, message the seller on WhatsApp
                      {store?.whatsappNumber ? ` (${store.whatsappNumber})` : ""} to arrange a pickup time.
                    </p>
                  </div>
                ) : (
                  <>
                    <div className="space-y-1.5 sm:col-span-2">
                      <Label htmlFor="addressLine1">Address</Label>
                      <Input
                        id="addressLine1"
                        placeholder="House no, street, area"
                        {...register("addressLine1")}
                      />
                      {errors.addressLine1 ? (
                        <p className="text-destructive text-xs">{errors.addressLine1.message}</p>
                      ) : null}
                    </div>
                    <div className="space-y-1.5">
                      <Label htmlFor="city">City</Label>
                      <Input id="city" placeholder="e.g. Parramatta" {...register("city")} />
                      {errors.city ? <p className="text-destructive text-xs">{errors.city.message}</p> : null}
                    </div>
                    <div className="space-y-1.5">
                      <Label htmlFor="postalCode">Postal code</Label>
                      <Input id="postalCode" placeholder="e.g. 2150" {...register("postalCode")} />
                      {errors.postalCode ? (
                        <p className="text-destructive text-xs">{errors.postalCode.message}</p>
                      ) : null}
                    </div>
                    <div className="space-y-1.5 sm:col-span-2">
                      <Label htmlFor="state">State/Province</Label>
                      <Select
                        value={state}
                        onValueChange={(v) => setValue("state", v as string, { shouldValidate: true })}
                      >
                        <SelectTrigger id="state" className="w-full">
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
                      {errors.state ? (
                        <p className="text-destructive text-xs">{errors.state.message}</p>
                      ) : null}
                    </div>
                  </>
                )}
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardContent className="space-y-4">
              <h2 className="font-semibold">Payment method</h2>
              {!codEnabled && !onlinePaymentEnabled && !bankTransferEnabled && !stripeEnabled ? (
                <p className="text-destructive text-sm">
                  This store isn&apos;t accepting payments right now. Please check back later.
                </p>
              ) : (
              <RadioGroup
                value={paymentMethod}
                onValueChange={(v) => setValue("paymentMethod", v as PaymentMethod)}
                className="gap-3"
              >
                {codEnabled ? (
                <Label
                  htmlFor="cod"
                  className="hover:bg-accent/50 flex cursor-pointer items-start gap-3 rounded-lg border p-3.5 has-[[data-state=checked]]:border-primary"
                >
                  <RadioGroupItem value="cod" id="cod" className="mt-0.5" />
                  <span className="flex flex-1 items-start gap-2.5">
                    <Truck className="mt-0.5 size-4 shrink-0" />
                    <span>
                      <span className="block text-sm font-medium">Cash on Delivery</span>
                      <span className="text-muted-foreground block text-xs">
                        Pay in cash when your order arrives
                      </span>
                    </span>
                  </span>
                </Label>
                ) : null}
                {onlinePaymentEnabled ? (
                <Label
                  htmlFor="payhere"
                  className="hover:bg-accent/50 flex cursor-pointer items-start gap-3 rounded-lg border p-3.5 has-[[data-state=checked]]:border-primary"
                >
                  <RadioGroupItem value="payhere" id="payhere" className="mt-0.5" />
                  <span className="flex flex-1 items-start gap-2.5">
                    <Landmark className="mt-0.5 size-4 shrink-0" />
                    <span>
                      <span className="block text-sm font-medium">Pay online with PayHere</span>
                      <span className="text-muted-foreground block text-xs">
                        Card, LankaQR, eZ Cash or mCash
                      </span>
                    </span>
                  </span>
                </Label>
                ) : null}
                {stripeEnabled ? (
                <Label
                  htmlFor="stripe"
                  className="hover:bg-accent/50 flex cursor-pointer items-start gap-3 rounded-lg border p-3.5 has-[[data-state=checked]]:border-primary"
                >
                  <RadioGroupItem value="stripe" id="stripe" className="mt-0.5" />
                  <span className="flex flex-1 items-start gap-2.5">
                    <CreditCard className="mt-0.5 size-4 shrink-0" />
                    <span>
                      <span className="block text-sm font-medium">Pay online with Stripe</span>
                      <span className="text-muted-foreground block text-xs">
                        Credit or debit card
                      </span>
                    </span>
                  </span>
                </Label>
                ) : null}
                {bankTransferEnabled ? (
                <Label
                  htmlFor="bank-transfer"
                  className="hover:bg-accent/50 flex cursor-pointer items-start gap-3 rounded-lg border p-3.5 has-[[data-state=checked]]:border-primary"
                >
                  <RadioGroupItem value="bank-transfer" id="bank-transfer" className="mt-0.5" />
                  <span className="flex flex-1 items-start gap-2.5">
                    <Banknote className="mt-0.5 size-4 shrink-0" />
                    <span>
                      <span className="block text-sm font-medium">Bank transfer</span>
                      <span className="text-muted-foreground block text-xs">
                        Transfer directly, then upload your receipt
                      </span>
                    </span>
                  </span>
                </Label>
                ) : null}
              </RadioGroup>
              )}
              {paymentMethod === "bank-transfer" && storeSettings ? (
                <div className="bg-muted/50 space-y-1 rounded-lg border p-3.5 text-sm">
                  <p className="font-medium">Transfer the total to:</p>
                  <p>{storeSettings.bankName}</p>
                  <p>{storeSettings.bankAccountName}</p>
                  <p className="font-mono">{storeSettings.bankAccountNumber}</p>
                  <p className="text-muted-foreground pt-1 text-xs">
                    After placing your order, upload a photo or PDF of your receipt from the order
                    confirmation page — the seller confirms your order once they&apos;ve verified it.
                  </p>
                </div>
              ) : null}
            </CardContent>
          </Card>
        </div>

        <Card className="h-fit lg:sticky lg:top-20">
          <CardContent className="space-y-4">
            <h2 className="font-semibold">Order summary</h2>
            <div className="space-y-3">
              {cart.items.map((item) => (
                <div
                  key={item.productId}
                  className={cn("flex items-center gap-3", item.isUnavailable && "opacity-50")}
                >
                  <div className="bg-muted relative size-12 shrink-0 overflow-hidden rounded-md">
                    <Image
                      src={item.productImageUrl}
                      alt={item.productName}
                      fill
                      sizes="48px"
                      className={cn("object-cover", item.isUnavailable && "grayscale")}
                    />
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="line-clamp-1 text-sm">{item.productName}</p>
                    <p className="text-muted-foreground text-xs">
                      {item.isUnavailable ? "No longer available" : `Qty ${item.quantity}`}
                    </p>
                  </div>
                  {item.isUnavailable ? null : (
                    <PriceDisplay price={item.unitPrice * item.quantity} size="sm" />
                  )}
                </div>
              ))}
            </div>
            {hasUnavailable ? (
              <div className="text-muted-foreground flex items-start gap-2 rounded-md border border-amber-200 bg-amber-50 p-3 text-xs dark:border-amber-900 dark:bg-amber-950/40">
                <TriangleAlert className="mt-0.5 size-3.5 shrink-0 text-amber-600" />
                <span>
                  Some items in your cart are no longer available.{" "}
                  <Link href="/cart" className="underline">
                    Go back to your cart
                  </Link>{" "}
                  to remove them before placing your order.
                </span>
              </div>
            ) : null}
            <Separator />
            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-muted-foreground">Subtotal</span>
                <span>{formatCurrency(subtotal, currency)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">{deliveryMethod === "pickup" ? "Pickup" : "Shipping"}</span>
                <span>
                  {deliveryMethod === "pickup" ? "Free" : formatCurrency(flatShippingFee, currency)}
                </span>
              </div>
              <Separator />
              <div className="flex justify-between text-base font-semibold">
                <span>Total</span>
                <span>
                  {formatCurrency(subtotal + (deliveryMethod === "pickup" ? 0 : flatShippingFee), currency)}
                </span>
              </div>
            </div>
            <Button
              type="submit"
              size="lg"
              className="w-full"
              disabled={
                mutation.isPending ||
                hasUnavailable ||
                availableItems.length === 0 ||
                (!codEnabled && !onlinePaymentEnabled && !bankTransferEnabled && !stripeEnabled)
              }
            >
              {mutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
              Place order
            </Button>
            <p className="text-muted-foreground text-center text-xs">
              By placing this order you agree to {name}&apos;s{" "}
              <Link href="#" className="underline">
                terms
              </Link>
              .
            </p>
          </CardContent>
        </Card>
      </form>
    </div>
  );
}
