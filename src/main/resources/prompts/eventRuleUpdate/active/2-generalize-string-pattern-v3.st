Task: Generate compact, high-level Regex patterns (Subject and Snippet) for these email templates.

Minimalism: Use only the most unique keywords to identify the template.

Wildcards: Replace PII, dates, non-essential 'filler' text or polite suffixes with .*.

Flexibility: Do not match exact endings (e.g., use 갱신 instead of 갱신되었습니다).

Merge: Group emails only if they share both 1.subject template and 2.snippet template. For groups, provide one common result append message IDs. Result regex should match all group members.

Input Data: {emails}