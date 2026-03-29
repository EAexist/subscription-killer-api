#!/bin/bash

set -e

TAG_NAME="v0.1.0-perf.1+test"

echo "Testing benchmark workflow with tag: $TAG_NAME"

# Check current branch
CURRENT_BRANCH=$(git branch --show-current)
if [ "$CURRENT_BRANCH" = "main" ]; then
    echo "❌ Error: Cannot run this script on main branch for safety."
    echo "Please switch to a different branch and try again."
    exit 1
fi

echo "Current branch: $CURRENT_BRANCH"

# 1. Add all local changes and commit
echo "Adding all changes and committing..."
git add .
git commit -m "wip: testing benchmark workflow trigger with perf test tag"

# 2. Remove tag from local and remote
echo "Removing existing tag from local and remote..."
git tag -d $TAG_NAME 2>/dev/null || echo "Tag doesn't exist locally"
git push origin :refs/tags/$TAG_NAME 2>/dev/null || echo "Tag doesn't exist remotely"

# 3. Re-tag the current commit and push both code and tag
echo "Tagging current commit and pushing..."
git tag $TAG_NAME
git push origin $CURRENT_BRANCH
git push origin $TAG_NAME

echo "Done! The benchmark workflow should be triggered."