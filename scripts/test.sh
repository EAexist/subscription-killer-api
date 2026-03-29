#!/bin/bash

set -e

# Source common setup functions
source "$(dirname "$0")/common-setup.sh"

EXTRA_ARGS=("$@")
CLEAN_ARGS=()
DOCKER_COMPOSE_FILE="docker-compose.yaml"

# Show help
if [ "$1" = "--help" ] || [ "$1" = "-h" ]; then
    echo "Usage: $0 [--observe] [--profiles=<profiles>] [additional_gradle_args...]"
    echo ""
    echo "Options:"
    echo "  --observe            Enable Zipkin observation with Docker containers (Elasticsearch + Zipkin)"
    echo "  --profiles=<profiles> Set Spring profiles (comma-separated, overrides default)"
    echo "  --help, -h           Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                                    # Run tests with default profiles: test"
    echo "  $0 --observe                         # Run tests with observation and default profiles"
    echo "  $0 --profiles=custom        # Run tests with custom profiles"
    echo "  $0 --observe --profiles=custom     # Run tests with observation and custom profiles"
    echo "  $0 --profiles=custom -PincludeTags=blah  # Run tests with profiles and Gradle property"
    echo ""
    exit 0
fi

# Parse arguments
OBSERVE_PROFILE=""
OBSERVE_FLAG=false
#CUSTOM_PROFILES=""
FILTERED_ARGS=()

for arg in "${EXTRA_ARGS[@]}"; do
    if [ "$arg" = "--observe" ]; then
        OBSERVE_PROFILE="observe"
        OBSERVE_FLAG=true
        # Skip this argument (don't add to FILTERED_ARGS)
#    elif [[ "$arg" =~ ^--profiles=(.+)$ ]]; then
#        CUSTOM_PROFILES="${BASH_REMATCH[1]}"
#        # Skip this argument (don't add to FILTERED_ARGS)
    else
        # Add to filtered arguments
        FILTERED_ARGS+=("$arg")
    fi
done

# Replace EXTRA_ARGS with filtered version
EXTRA_ARGS=("${FILTERED_ARGS[@]}")

# Initialize colors and logging
setup_colors

if [ "$OBSERVE_FLAG" = true ]; then
    log_info "=== Running with OBSERVE profile enabled ==="
    # Setup cleanup with observe profile for Zipkin
    setup_cleanup "observe" "$DOCKER_COMPOSE_FILE" true
    # Start Docker services with observe profile
    start_docker_services "observe" "$DOCKER_COMPOSE_FILE"
else
    log_info "=== Running without OBSERVE profile (default) ==="
    # Setup cleanup with empty profile (default)
    setup_cleanup "" "$DOCKER_COMPOSE_FILE" true
    # Start Docker services
    start_docker_services "" "$DOCKER_COMPOSE_FILE"
fi

# Wait for database to be ready
wait_for_db "$DOCKER_COMPOSE_FILE"

setup_google_env

# Determine Spring profiles
#if [ -n "$CUSTOM_PROFILES" ]; then
#    SPRING_PROFILES="test,$CUSTOM_PROFILES"
#    log_info "Using custom profiles: $SPRING_PROFILES"
#else
#    SPRING_PROFILES="test"
#    log_info "Using default profiles: $SPRING_PROFILES"
#fi

# Set default Gradle arguments if none provided
if [ ${#EXTRA_ARGS[@]} -eq 0 ]; then
    echo "Running: ./gradlew test"
else
    echo "Running: ./gradlew test ${EXTRA_ARGS[@]}"
fi

if [ -f .env.test ]; then
    export $(grep -v '^#' .env.test | xargs)
fi

# 2. Run Gradle
./gradlew test "${EXTRA_ARGS[@]}"
