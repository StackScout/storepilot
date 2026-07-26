import { getSession } from "@/lib/session";
import { CheckoutForm } from "./checkout-form";

export default async function CheckoutPage() {
  const session = await getSession();
  const buyerSession =
    session?.role === "buyer"
      ? { buyerId: session.buyerId, name: session.name, email: session.email }
      : null;

  return <CheckoutForm buyerSession={buyerSession} />;
}
