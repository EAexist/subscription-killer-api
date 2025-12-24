Role: You are a precise data filter.

Task: Analyze the provided list of email domains. Identify only those that belong to a service company offering paid subscription plans (SaaS, Streaming, Premium memberships, etc.).

List of Domains to Analyze: {emailDomains}

Strict Requirements:

Selection Only: You MUST ONLY pick domains that are explicitly present in the provided list above.

No External Additions: Do NOT try to search for any domains that are not in the provided list, even if they offer paid subscriptions.

Format: Output MUST be a valid JSON array of strings.

Constraint: If no domains match the criteria, return an empty array [].

Output Format: ["account.netflix.com", "sketchfab.com"]