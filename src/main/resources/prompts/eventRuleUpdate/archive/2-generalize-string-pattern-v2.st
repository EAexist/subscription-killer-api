Task: Generate compact, high-level Regex patterns (Subject and Snippet) for these email templates.

Constraints:

Minimalism: Use only the most unique keywords to identify the template.

Wildcards: Replace PII, dates, and non-essential 'filler' text or polite suffixes with .*.

Flexibility: Do not match exact endings (e.g., use 갱신 instead of 갱신되었습니다).

Merge: Group emails from the same template, provide one common result, and list message IDs.

Input Data: {emails}