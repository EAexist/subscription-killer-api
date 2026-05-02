#!/bin/bash

# Read git tag from .env.dev.benchmark file if it exists, otherwise use default
ENV_FILE="$(dirname "$0")/../.env.benchmark"
if [ -f "$ENV_FILE" ]; then
  source "$ENV_FILE"
  echo "✓ Loaded GIT_TAG from .env.benchmark: $GIT_TAG"
else
  echo "❌ Error: .env.benchmark not found. Cannot proceed without a defined tag."
  exit 1
fi

# Load environment variables from .env.dev.dataset file if it exists
PROJECT_ROOT="$(dirname "$(dirname "$0")")"
DATASET_ENV_FILE="$PROJECT_ROOT/.env.dataset"
BUILD_ARGS=""
if [ -f "$DATASET_ENV_FILE" ]; then
  # Create a temporary file with quotes stripped
  temp_env=$(mktemp)
  sed "s/^\([^=]*\)=[\"\']\([^\"\']*\)[\"\']$/\1=\2/" "$DATASET_ENV_FILE" > "$temp_env"
  
  # Source the cleaned file
  set -a
  source "$temp_env"
  set +a
  
  # Convert to build args for docker
  while IFS='=' read -r key value || [[ -n "$key" ]]; do
    # Skip empty lines and comments
    if [[ -n "$key" && ! "$key" =~ ^# ]]; then
      BUILD_ARGS="$BUILD_ARGS --build-arg $key=$value"
      echo "  ✓ Loaded: $key=$value"
    fi
  done < "$temp_env"
  
  # Clean up
  rm -f "$temp_env"
  echo "✓ Loaded environment variables from .env.dataset"
else
  echo "⚠️  .env.dataset file not found at: $DATASET_ENV_FILE"
  echo "Proceeding without dataset environment variables."
fi

if [ -z "$GIT_TAG" ]; then
  echo "❌ Error: GIT_TAG is empty in $ENV_FILE."
  echo "Please define a valid tag (e.g., GIT_TAG=v0.0.0-test.0) and try again."
  exit 1
fi

# 1. Delete all local tags
git tag -l | xargs -r git tag -d

# 2. Create the new tag
git tag "$GIT_TAG"
echo "✓ Added new git tag: $GIT_TAG"

# 1. Get the tag pointing at current HEAD
GIT_TAG=$(git tag --points-at HEAD | head -n 1)

# 2. Safety Check: If no tag exists, use the latest tag found in history
# or fall back to the short commit SHA
if [ -z "$GIT_TAG" ]; then
  GIT_TAG=$(git describe --tags --abbrev=0 2>/dev/null || git rev-parse --short HEAD)
  echo "⚠️  No tag at HEAD. Falling back to: $GIT_TAG"
fi

# Get commit hash for Docker tag (like GitHub workflow)
COMMIT_HASH=$(git rev-parse HEAD)
echo "✓ Using commit hash as Docker tag: $COMMIT_HASH"

# 1. Capture the exact ID from the build output
docker buildx build \
  --load . \
  --tag "subscription-killer-api:$COMMIT_HASH" \
  --build-arg IMAGE_REVISION=$COMMIT_HASH \
  --build-arg IMAGE_REF_NAME="${GIT_TAG}" \
  --build-arg IMAGE_CREATED=$(date -u +"%Y-%m-%dT%H:%M:%SZ") \
  --secret id=hf_token,env=HF_TOKEN \
  $BUILD_ARGS \
  --progress=plain

if [ $? -eq 0 ]; then
  NEW_IMAGE_ID=$(docker images -q "subscription-killer-api:$COMMIT_HASH")
  echo "Successfully built: $NEW_IMAGE_ID"
else
  echo "Build failed. Check the logs above."
  exit 1
fi

echo "Cleaning up old versions, keeping current image: $NEW_IMAGE_ID..."

# Get all IDs with that label, but exclude the one we just created
OLD_IMAGE_IDS=$(docker images -q --filter "label=org.opencontainers.image.title=subscription-killer-api" | grep -v "$NEW_IMAGE_ID" | sort | uniq)

if [ -n "$OLD_IMAGE_IDS" ]; then
    echo "Removing: $OLD_IMAGE_IDS"
    echo "$OLD_IMAGE_IDS" | xargs -r docker rmi -f
fi

# 5. Prune dangling build cache/layers
docker image prune -f --filter "label=org.opencontainers.image.title=subscription-killer-api"