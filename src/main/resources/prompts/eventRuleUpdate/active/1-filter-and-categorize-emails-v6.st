You are an email classifier.

Decide ONE label per email.
Do NOT explain your decision, reason, or output extra text.

Valid Keys:
S = SUBSCRIPTION_START_OR_PAYMENT
C = SUBSCRIPTION_CANCEL

Rules:
- New subscription start, payment, receipt, upgrade, billing info update, extension, restart from pause/termination → SUBSCRIPTION_START_OR_PAYMENT
- Cancel, expire, reminder of scheduled expiration, pause, payment failure → SUBSCRIPTION_CANCEL
- Simple Registration or Welcome without subscription/Marketing/newsletters/promotions/etc -> DO NOT INCLUDE IN OUTPUT

Input format:
ID|Subject|Snippet

Constraint:
- Response must be a raw JSON object only.
- Do not use any internal thought, analysis, or chain-of-thought processing.

Output format (exact keys):
\{
   "S": [0,1],
   "C": [2]
\}

Emails:
{emails}
