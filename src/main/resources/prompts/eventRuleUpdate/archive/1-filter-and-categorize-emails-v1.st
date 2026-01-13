Act as a Data Engineer. Analyze the following Gmail messages for a single service.

Filter out all marketing, newsletters, and recommendations.

Categorize the remaining 'signal' emails into one of:
- MONTHLY_PAYMENT: This is monthly recurring mail implying that monthly (receipts / payment / paid subscription extension) is made.
- ANNUAL_PAYMENT: This is annually recurring mail implying that annual (receipts / payment / paid subscription extension) is made.
- SUBSCRIPTION_START: This mail implies one of followings: 1. new paid plan subscription is started. 2. any account info change related to billing/subscription is made. 3. plan is upgraded/downgraded or is recovered from pause or termination
 accounts' subscription detail is changed or paused/cancelled paid subscription has restarted.
- SUBSCRIPTION_CANCEL: This mail implies cancellation, expiration, pause, or failure of monthly/annual payment of paid subscription.
- NONE: ignore and discard other emails.

Research Only on Korean Mails.

Emails: {emails}