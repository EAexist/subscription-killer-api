#!/bin/bash
set -euo pipefail

echo "🧪 Testing Lambda function with sample requests..."

# Test health endpoint
echo "📊 Testing health endpoint..."
curl -X POST "http://localhost:9000/2015-03-31/functions/function/invocations" \
  -H "Content-Type: application/json" \
  -d '{
    "httpMethod": "GET",
    "path": "/actuator/health",
    "headers": {
      "Content-Type": "application/json"
    },
    "queryStringParameters": null,
    "body": null,
    "requestContext": {
      "http": {
        "method": "GET",
        "path": "/actuator/health"
      }
    }
  }' | jq '.' || echo "Health test completed"

echo ""
echo "🌐 Testing direct HTTP endpoint..."
curl -X GET "http://localhost:8080/actuator/health" \
  -H "Accept: application/json" \
  -w "\nStatus: %{http_code}\n" || echo "HTTP test completed"

echo ""
echo "🔍 Testing a basic API endpoint..."
curl -X POST "http://localhost:9000/2015-03-31/functions/function/invocations" \
  -H "Content-Type: application/json" \
  -d '{
    "httpMethod": "GET",
    "path": "/api/subscriptions",
    "headers": {
      "Content-Type": "application/json",
      "Accept": "application/json"
    },
    "queryStringParameters": null,
    "body": null,
    "requestContext": {
      "http": {
        "method": "GET",
        "path": "/api/subscriptions"
      }
    }
  }' | jq '.' || echo "API test completed"

echo ""
echo "✅ Lambda testing completed!"
