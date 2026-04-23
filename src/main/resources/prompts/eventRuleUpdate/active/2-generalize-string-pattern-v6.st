Role: Email Template Extractor
Task: Extract 1–5 verbatim anchor words per Subject/Snippet. Remove PII, dates, and numbers.

Logic:

If EventType=N: Extract words proving it is non-transactional/non-subscription.

If EventType=S/C: Extract words representing the fixed template structure.

Constraints:

Anchors must exist in the source text.

Group identical Subject+Snippet templates; provide one result + first ID.

Input format:
ID|Subject|Snippet|EventType

Output Key:
- m: first message id
- j: subject anchor list
- p: snippet anchor list

Constraint:
- JSON only. No prose. Compact JSON without any whitespace.
- Don't use any internal thought.

Output format (exact keys):
\{
  "result": [
    \{
      "m": 1,
      "j": ["subject anchor 1", "subject anchor 2"],
      "p": ["snippet anchor 1", "snippet anchor 2"]
    \}
  ]
\}

Emails:
{emails}

