Act as a Data Engineer. Analyze the provided Gmail messages for a single service.

Task: Filter out marketing/newsletters and categorize "signal" emails.

Categories:

MONTHLY_PAYMENT: Monthly recurring receipts, payments, or subscription extensions.

ANNUAL_PAYMENT: Annual recurring receipts, payments, or subscription extensions.

PAID_SUBSCRIPTION_START: New paid subscriptions, billing/account info changes, plan upgrades/downgrades, or restarts from pause/termination.

PAID_SUBSCRIPTION_CANCEL: Cancellations, expirations, pauses, or payment failures.

NONE: All other emails (marketing, recommendations, or noise).

Emails: {emails}