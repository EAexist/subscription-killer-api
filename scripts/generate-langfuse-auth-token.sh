#!/bin/bash

# 1. Load variables from .env file
if [ -f .env ]; then
    export $(grep -v '^#' .env | xargs)
else
    echo "Error: .env file not found."
    exit 1
fi

# 2. Check if required variables exist
if [ -z "$LANGFUSE_PUBLIC_KEY" ] || [ -z "$LANGFUSE_SECRET_KEY" ]; then
    echo "Error: LANGFUSE_PUBLIC_KEY or LANGFUSE_SECRET_KEY is missing."
    exit 1
fi

# 3. Generate the Base64 token (Public:Secret)
# Use 'printf' to avoid trailing newlines in the encoding
TOKEN=$(printf "%s:%s" "$LANGFUSE_PUBLIC_KEY" "$LANGFUSE_SECRET_KEY" | base64)

# 4. Output/Export the result
echo "Generated Token: $TOKEN"
export LANGFUSE_AUTH_TOKEN=$TOKEN

# Optional: Append/Update the token in your .env file
if grep -q "LANGFUSE_AUTH_TOKEN" .env; then
    sed -i "s/^LANGFUSE_AUTH_TOKEN=.*/LANGFUSE_AUTH_TOKEN=$TOKEN/" .env
else
    echo "LANGFUSE_AUTH_TOKEN=$TOKEN" >> .env
fi

echo "Success: LANGFUSE_AUTH_TOKEN added to .env"