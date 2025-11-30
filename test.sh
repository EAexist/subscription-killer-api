#!/bin/bash
TEST_CLASSES=("$@")

docker compose up -d 
export DB_ENDPOINT=localhost
export DB_NAME=subscription_killer_db
export DB_USER=guest
export DB_PASSWORD=hello_guest

if [ ${#TEST_CLASSES[@]} -eq 0 ]; then
    ./gradlew test -Dspring.profiles.active=local,development
else
    ./gradlew test -Dspring.profiles.active=local,development --tests "${TEST_CLASSES[@]}"
fi

docker compose down