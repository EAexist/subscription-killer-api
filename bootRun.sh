docker compose up -d && \
DB_ENDPOINT=localhost DB_NAME=subscription_killer_db DB_USER=guest DB_PASSWORD=hello_guest \
./gradlew bootRun --args='--spring.profiles.active=local,development'