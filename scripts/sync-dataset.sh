#!/bin/bash

# Read environment variables from .env.dev.dataset file
ENV_FILE=".env.dataset"

if [ ! -f "$ENV_FILE" ]; then
    echo "Error: $ENV_FILE file not found"
    exit 1
fi

# Export variables from .env.dev.dataset
set -a
source "$ENV_FILE"
set +a

# Check required variables
if [ -z "$HF_TOKEN" ] || [ -z "$HF_OWNER" ] || [ -z "$HF_REPO" ]; then
    echo "Error: Required environment variables (HF_TOKEN, HF_OWNER, HF_REPO) not found in $ENV_FILE"
    exit 1clea
fi

echo "Starting dataset download..."
echo "HF_OWNER: $HF_OWNER"
echo "HF_REPO: $HF_REPO"

# Run the downloadTestData task
./gradlew downloadTestData -PsyncDataset=true

echo "Dataset download completed."