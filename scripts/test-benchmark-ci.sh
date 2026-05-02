#!/bin/bash

set -e

# Find the highest existing perf tag and increment it
echo "Finding existing performance tags..."
EXISTING_TAGS=$(git ls-remote --tags origin | grep "v0\.1\.0-perf\..*\+test" | sed 's|.*/||' | sort -V)

if [ -n "$EXISTING_TAGS" ]; then
    LATEST_TAG=$(echo "$EXISTING_TAGS" | tail -n1)
    # Extract the number after "v0.1.0-perf." and before "+test"
    CURRENT_NUM=$(echo "$LATEST_TAG" | sed 's/v0\.1\.0-perf\.\(.*\)+test/\1/')
    NEXT_NUM=$((CURRENT_NUM + 1))
    TAG_NAME="v0.1.0-perf.$NEXT_NUM+test"
    echo "Found latest tag: $LATEST_TAG, creating next tag: $TAG_NAME"
else
    TAG_NAME="v0.1.0-perf.1+test"
    echo "No existing performance tags found, starting with: $TAG_NAME"
fi

echo "Testing benchmark workflow with tag: $TAG_NAME"

# Check current branch
CURRENT_BRANCH=$(git branch --show-current)
if [ "$CURRENT_BRANCH" = "main" ]; then
    echo "❌ Error: Cannot run this script on main branch for safety."
    echo "Please switch to a different branch and try again."
    exit 1
fi

echo "Current branch: $CURRENT_BRANCH"

# 1. Add all local changes and commit if there are any
echo "Adding all changes and committing..."
git add .
if ! git diff-index --quiet HEAD; then
    git commit -m "wip: testing benchmark workflow trigger with perf test tag"
else
    echo "No changes to commit, working tree clean. Proceeding with existing commit..."
fi

# 2. Tag the current commit and push both code and tag
echo "Tagging current commit and pushing..."
git tag $TAG_NAME
git push origin $CURRENT_BRANCH
git push origin $TAG_NAME

echo "Done! The benchmark workflow should be triggered."