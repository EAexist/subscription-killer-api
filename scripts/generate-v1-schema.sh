#!/bin/bash

cleanup() {
    echo "Cleaning up containers..."
    docker compose down
}
trap cleanup EXIT

docker compose up -d && \
{
    # Load SSL environment variables safely
    set -a
    # shellcheck source=.env.dev
    source .env.dev
    set +a
    
    ./gradlew bootRun -x test --args='--spring.profiles.active=dev,oauth,local-ssl,schema-generation'
}