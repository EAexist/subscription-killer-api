Based on the categorized emails, generate recognition patterns for each category.

Strict Rules:

No PII: Remove names, specific dates (e.g., 'Oct 5th'), specific amounts (e.g., '$14.99'), and card numbers.

Keywords: Extract high-signal phrases. I will use this to match and identify mails as this category from set of general mails. Because services frequently change their expressions in mails, make phrases to focus on nouns and verbs and try to avoid full sentence or general suffixes. When i'm using this, i will use logical-OR, so that email is matched for category if it contains any of the keywords.

Regex: Create Java-compatible regex (use \\d+ for numbers). Ensure the regex can distinguish 1. MONTHLY_PAYMENT or ANNUAL_PAYMENT mails from 2. SUBSCRIPTION_START mails. When I'm using this, i will use logical-AND between keyword match and regex match, so that email is matched for category only if both 1.one of keywords and 2.regex matches.

Based On Input: Do not make any assumption or predictions about unseen emails. Generate pattern based on the input data. Ensure they are specific enough to prevent false positives. But also, , so try to use key phrases and avoid using sentences or exact full phrases.

Input Data: {categorizedEmails}