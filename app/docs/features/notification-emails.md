# Feature: Order Notification Emails

> Index: [`feature-index.md`](../feature-index.md) · Architecture:
> [`frontend-architecture.md`](../frontend-architecture.md) · API:
> [`api-contracts.md`](../../../docs/api-contracts.md)

## Purpose

Email buyers and sellers at the points in the order lifecycle where they
need to act or be reassured, without coupling that logic to any specific
email provider. Entirely backend-side
(`backend/src/main/kotlin/com/islandcart/backend/notification/`) — there is
no frontend involvement beyond the order confirmation page's static "A
receipt has been sent to `<email>`" copy.

## Design

Two layers, deliberately kept separate:

- **`EmailService`** — transport only: `send(to, subject, body)`. No
  knowledge of orders, templates, or copy. `LoggingEmailService` is the
  only implementation today — it logs instead of sending
  (`[mock email] to=... subject="..."`). Adding a real provider (AWS SES,
  Resend, etc.) later is a second `@Service` implementing the same
  interface; nothing else in the backend changes.
- **`OrderNotifier`** — owns the copy and picks the recipient for each
  event, then calls `EmailService.send`. `OrderService` and
  `ReceiptReminderJob` call `OrderNotifier`, never `EmailService` directly.

## Touchpoints

| Event | Recipient | Method | Called from |
|---|---|---|---|
| Order placed | Buyer (`order.buyerEmail`) | `orderConfirmed` | `OrderService.createOrder` |
| Bank-transfer receipt uploaded | Seller (`StoreSettings.contactEmail`) | `receiptUploaded` | `OrderService.uploadReceipt` |
| Bank-transfer verified or rejected | Buyer | `bankTransferVerified` | `OrderService.verifyBankTransfer` |
| Bank-transfer receipt still missing | Buyer | `receiptReminder` | `ReceiptReminderJob` (scheduled) |

`receiptUploaded` looks up the seller's contact email via
`StoreSettingsRepository`; if a store somehow has no `StoreSettings` row
(sparse 1:1 — see [`store-settings.md`](store-settings.md)) the send is
skipped and a warning is logged rather than failing the buyer's upload
request.

## Receipt reminders

`ReceiptReminderJob` (`@Scheduled`, `@EnableScheduling` on
`BackendApplication`) periodically emails buyers who chose bank transfer
but haven't uploaded a receipt yet, with a link back to
`/orders/{orderId}` where they can either upload a receipt or cancel the
order (see [`checkout.md`](checkout.md#business-rules) for the cancel
endpoint and the page's "Payment pending" UI state).

- Query: `OrderRepository.findDueForReceiptReminder` — bank-transfer,
  unpaid, no `receiptUrl`, `status <> cancelled`, and either never
  reminded (older than `notifications.first-reminder-after-hours`) or last
  reminded more than `notifications.reminder-interval-hours` ago.
- Defaults (`application.yml`, all overridable via env var): first
  reminder 6h after the order is placed, then every 24h; the job itself
  ticks every hour (`notifications.reminder-check-interval-ms`).
- **Uncapped** — keeps sending on that interval indefinitely. It stops
  naturally the moment the order drops out of the query (receipt uploaded
  or order cancelled) — there's no separate "stop reminding" branch.
- `Order.lastReminderSentAt` tracks the throttle; it's a backend-only
  column, never exposed in `OrderResponse`.

## Config (`application.yml`, prefix `notifications`)

```yaml
notifications:
  frontend-base-url: ${NOTIFICATIONS_FRONTEND_BASE_URL:http://localhost:3000}
  first-reminder-after-hours: ${NOTIFICATIONS_FIRST_REMINDER_AFTER_HOURS:6}
  reminder-interval-hours: ${NOTIFICATIONS_REMINDER_INTERVAL_HOURS:24}
  reminder-check-interval-ms: ${NOTIFICATIONS_REMINDER_CHECK_INTERVAL_MS:3600000}
```

`frontend-base-url` is used to build order links in email bodies, as
`{frontendBaseUrl}/orders/{orderId}` — the same shape as
`PayHereProperties.returnUrlBase` (kept as a separate property rather than
shared, since PayHere and notifications are otherwise unrelated concerns).

## Not implemented

- No real email provider — everything is logged only, nothing is actually
  delivered. See the design section above for how a provider gets added.
- No reminder cap — see "Receipt reminders" above; if this becomes a
  product decision (e.g. "stop after 5 emails"), it needs a
  `reminder_count` column alongside `last_reminder_sent_at`.
