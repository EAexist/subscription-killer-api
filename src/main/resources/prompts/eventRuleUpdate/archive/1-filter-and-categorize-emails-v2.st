Role: Data Engineer.

Task: Filter out marketing/newsletters and categorize "signal" emails.

Categories:

MONTHLY_PAYMENT: Monthly recurring receipts, payments, or subscription extensions.

ANNUAL_PAYMENT: Annual recurring receipts, payments, or subscription extensions.

SUBSCRIPTION_START: New membership subscriptions, billing/account info changes, plan upgrades/downgrades, or restarts from pause/termination.

SUBSCRIPTION_CANCEL: Cancellations, expirations, reminder of scheduled expiration, pauses, or payment failures of subscription.

Input Format: "ID|Subject|Snippet".

Emails: {emails}