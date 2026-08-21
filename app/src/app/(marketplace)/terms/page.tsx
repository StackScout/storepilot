import { getPlatformConfig } from "@/lib/platform-config";

/**
 * Placeholder content, not reviewed by counsel — see privacy/page.tsx's
 * doc comment and docs/gaps-and-assumptions.md's "No Privacy Policy or
 * Terms of Service pages" entry. Replace with real legal-reviewed copy
 * before this feeds real user data at any meaningful scale.
 */
export default async function TermsOfServicePage() {
  const config = await getPlatformConfig();

  return (
    <div className="mx-auto max-w-3xl px-4 py-12 sm:px-6 lg:px-8">
      <h1 className="text-3xl font-bold">Terms of Service</h1>
      <p className="text-muted-foreground mt-2 text-sm">Last updated: August 2026</p>

      <div className="mt-8 space-y-8 text-sm leading-relaxed">
        <p>
          These Terms of Service (&quot;Terms&quot;) govern your use of {config.name}, a marketplace connecting
          buyers with independent sellers. By creating an account, placing an order, or booking a service through{" "}
          {config.name}, you agree to these Terms.
        </p>

        <section className="space-y-2">
          <h2 className="text-lg font-semibold">The marketplace</h2>
          <p>
            {config.name} is a platform that connects buyers with independent sellers. Sellers are responsible for
            the products and services they list, including their description, pricing, and fulfillment.{" "}
            {config.name} is not the seller of record for items purchased through the platform.
          </p>
        </section>

        <section className="space-y-2">
          <h2 className="text-lg font-semibold">Accounts</h2>
          <p>
            You must provide accurate information when creating an account and keep your login credentials secure.
            Sellers must complete the store verification process before listing products or services.
          </p>
        </section>

        <section className="space-y-2">
          <h2 className="text-lg font-semibold">Orders and payment</h2>
          <p>
            Orders are placed directly with the seller through {config.name}. Payment is processed through the
            payment method selected at checkout. Pricing, delivery fees, and applicable platform fees are shown
            before you complete an order.
          </p>
        </section>

        <section className="space-y-2">
          <h2 className="text-lg font-semibold">Returns and refunds</h2>
          <p>
            Return and refund eligibility is described on your order page once an order has been delivered.
            Requests are reviewed by the seller, and refunds are processed through the original payment method
            where applicable.
          </p>
        </section>

        <section className="space-y-2">
          <h2 className="text-lg font-semibold">Seller obligations</h2>
          <p>
            Sellers agree to list products and services accurately, fulfill orders in a timely manner, and comply
            with applicable consumer protection laws in the markets they sell into.
          </p>
        </section>

        <section className="space-y-2">
          <h2 className="text-lg font-semibold">Prohibited use</h2>
          <p>
            You may not use {config.name} for unlawful purposes, to list prohibited or counterfeit goods, or to
            interfere with the operation or security of the platform.
          </p>
        </section>

        <section className="space-y-2">
          <h2 className="text-lg font-semibold">Changes to these Terms</h2>
          <p>
            We may update these Terms from time to time. Continued use of {config.name} after a change means you
            accept the updated Terms.
          </p>
        </section>

        <section className="space-y-2">
          <h2 className="text-lg font-semibold">Contact us</h2>
          <p>
            Questions about these Terms can be sent to{" "}
            <a href={`mailto:${config.supportEmail}`} className="text-primary underline-offset-4 hover:underline">
              {config.supportEmail}
            </a>
            .
          </p>
        </section>
      </div>
    </div>
  );
}
