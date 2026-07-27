import { CheckoutForm } from "./checkout-form";

/** CheckoutForm resolves its own signed-in-buyer state client-side (there's no server-decodable session anymore, only a JWT the backend validates) — guest checkout works exactly the same either way. */
export default function CheckoutPage() {
  return <CheckoutForm />;
}
