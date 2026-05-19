#!/bin/bash
set -euo pipefail

echo "🚀 Testing Lambda deployment with Docker..."

ZIP_FILE="build/distributions/sublog-api-0.0.1-SNAPSHOT.zip"

if [ ! -f "$ZIP_FILE" ]; then
    echo "❌ Error: ZIP file not found at $ZIP_FILE"
    echo "Please run: ./gradlew buildLambdaWebAdapterZip"
    exit 1
fi

echo "✅ ZIP file found: $ZIP_FILE"

echo "🔨 Building Docker image..."
docker build -f Dockerfile.lambda-test -t lambda-test .

mkdir -p logs
LOG_FILE="logs/lambda-test-$(date +%Y%m%d-%H%M%S).log"

echo ""
echo "🏃 Running Lambda container (foreground mode)..."
echo "📡 Lambda: http://localhost:9000/2015-03-31/functions/function/invocations"
echo "🌐 HTTP:   http://localhost:8080"
echo "📝 Logs:   $LOG_FILE"
echo ""
echo "Press Ctrl+C to stop"
echo ""

# Cleanup on exit
cleanup() {
    echo ""
    echo "🧹 Cleaning up container..."
    docker rm -f lambda-test-container 2>/dev/null || true
    echo "🗂️  Logs saved to: $LOG_FILE"
}
trap cleanup EXIT

# Run container in foreground and stream ALL logs from the start
docker run --rm \
    -p 9000:8080 \
    --name lambda-test-container \
    lambda-test 2>&1 | tee "$LOG_FILE"