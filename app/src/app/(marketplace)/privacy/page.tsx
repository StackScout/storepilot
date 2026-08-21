import { getPlatformConfig } from "@/lib/platform-config";

/**
 * Placeholder content, not reviewed by counsel — see docs/gaps-and-assumptions.md's
 * "No Privacy Policy or Terms of Service pages" entry. Written generically
 * enough to not misstate jurisdiction-specific compliance (e.g. doesn't
 * claim GDPR/specific-regulation compliance for either the AU or LK
 * deployment). Replace with real legal-reviewed copy before this feeds
 * real user data at any meaningful scale.
 */
export default async function PrivacyPolicyPage() {
  const config = await getPlatformConfig();

  return (
    <div className="mx-auto max-w-3xl px-4 py-12 sm:px-6 lg:px-8">
      <h1 className="text-3xl font-bold">Privacy Policy</h1>
      <p className="text-muted-foreground mt-2 text-sm">Last updated: August 2026</p>

      <div className="mt-8 space-y-8 text-sm leading-relaxed">
        <p>
          This Privacy Policy explains how {config.name} (&quot;we&quot;, &quot;us&quot;) collects, uses, and
          protects information when you use our marketplace as a buyer or seller.
        </p>

        <section className="space-y-2">
          <h2 className="text-lg font-semibold">Information we collect</h2>
          <p>Depending on how you use {config.name}, we may collect:</p>
          <ul className="list-disc space-y-1 pl-5">
            <li>Account details — name, email address, and phone number.</li>
            <li>Order and delivery details — shipping address, order history, and messages exchanged with sellers.</li>
            <li>Seller business details — store information and identity-verification documents required to sell on the platform.</li>
            <li>Payment information — processed directly by our payment providers; we do not store full card numbers or bank credentials ourselves.</li>
            <li>Technical information — basic device and usage data needed to operate and secure the platform.</li>
          </ul>
        </section>

        <section className="space-y-2">
          <h2 className="text-lg font-semibold">How we use this information</h2>
          <p>
            We use your information to operate the marketplace: processing orders and bookings, facilitating
            payments, verifying seller identity, sending order and account notifications, and providing customer
            support. We do not sell your personal information to third parties.
          </p>
        </section>

        <section className="space-y-2">
          <h2 className="text-lg font-semibold">Third-party services</h2>
          <p>
            Some information is shared with service providers who help us run {config.name}, including payment
            processors (such as Stripe and PayHere), our authentication provider, and our email delivery provider.
            Each of these providers processes information under their own privacy terms.
          </p>
        </section>

        <section className="space-y-2">
          <h2 className="text-lg font-semibold">Data retention</h2>
          <p>
            We retain account and order information for as long as your account is active and as needed to meet
            legal, accounting, or reporting obligations. You can request deletion of your account information by
            contacting us using the details below.
          </p>
        </section>

        <section className="space-y-2">
          <h2 className="text-lg font-semibold">Your choices</h2>
          <p>
            You can review and update your account details at any time from your account settings. Sellers can
            review and update their store details from their seller dashboard.
          </p>
        </section>

        <section className="space-y-2">
          <h2 className="text-lg font-semibold">Changes to this policy</h2>
          <p>
            We may update this policy from time to time. Continued use of {config.name} after a change means you
            accept the updated policy.
          </p>
        </section>

        <section className="space-y-2">
          <h2 className="text-lg font-semibold">Contact us</h2>
          <p>
            If you have questions about this policy or your information, contact us at{" "}
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
