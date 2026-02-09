#!/bin/bash

cleanup() {
    echo "Cleaning up containers..."
    docker compose down
}
trap cleanup EXIT

docker compose up -d && \
env $(cat .env.ssl | tr -d '\r' | xargs) ./gradlew bootRun -x test --args='--spring.profiles.active=dev,google-auth,local-ssl,schema-generation'