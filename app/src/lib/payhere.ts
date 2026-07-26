import type { PayHereCheckoutPayload } from "@/types";

/**
 * Submits a hidden form to PayHere's Checkout API, which redirects the
 * buyer's browser to PayHere's own gateway page. This is the plain HTML-form
 * redirect method, not the payhere.js onsite popup SDK — the popup's
 * `startPayment` readiness proved unreliable in practice (an async
 * domain-validation step with no hard timing guarantee); this redirect has
 * no equivalent step to wait on. See
 * https://support.payhere.lk/api-&-mobile-sdk/checkout-api
 *
 * Navigates the page away immediately — there's nothing to await.
 */
export function submitPayHereCheckout(payload: PayHereCheckoutPayload): void {
  const fields: Record<string, string> = {
    merchant_id: payload.merchantId,
    return_url: payload.returnUrl,
    cancel_url: payload.cancelUrl,
    notify_url: payload.notifyUrl,
    order_id: payload.orderId,
    items: payload.items,
    currency: payload.currency,
    amount: payload.amount,
    hash: payload.hash,
    first_name: payload.firstName,
    last_name: payload.lastName,
    email: payload.email,
    phone: payload.phone,
    address: payload.address,
    city: payload.city,
    country: payload.country,
  };

  const form = document.createElement("form");
  form.method = "POST";
  form.action = payload.actionUrl;

  for (const [name, value] of Object.entries(fields)) {
    const input = document.createElement("input");
    input.type = "hidden";
    input.name = name;
    input.value = value;
    form.appendChild(input);
  }

  document.body.appendChild(form);
  form.submit();
}
