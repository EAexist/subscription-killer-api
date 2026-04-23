#!/bin/bash

# Common setup functions for test scripts

# Colors for output
setup_colors() {
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    NC='\033[0m' # No Color
}

# Logging function
log_info() {
    echo -e "${GREEN}$1${NC}"
}

log_success() {
    echo -e "${GREEN}$1${NC}"
}

log_warn() {
    echo -e "${YELLOW}$1${NC}"
}

log_error() {
    echo -e "${RED}$1${NC}"
}

# SSL and Google account environment setup
setup_env() {
    set -a; source .env.dev; set +a
}

setup_google_env() {
    set -a; source .env.google-account.test; set +a
}

# Wait for database to be ready
wait_for_db() {
    local docker_compose_file="$1"
    
    log_warn "Waiting for database to be ready..."
    sleep 5
    
    # Check if database is ready
    if docker-compose -f "$docker_compose_file" ps | grep -q "db.*Up"; then
        log_info "Database is running."
    else
        log_error "Error: Database failed to start!"
        exit 1
    fi
}

# Start Docker services with profile
start_docker_services() {
    local profile="$1"
    local docker_compose_file="$2"
    
    log_info "=== Starting Docker Environment (profile: $profile) ==="
    
    # Debug: Show what we're about to run
    log_info "Running: docker-compose -f $docker_compose_file --profile $profile up -d"
    
    # Check what services are running before we start
    local before_output
    before_output=$(docker-compose -f "$docker_compose_file" --profile "$profile" ps 2>/dev/null)
    local before_count
    before_count=$(echo "$before_output" | grep -c "Up" | tr -d '\n' || echo "0")
    log_info "Services running before: $before_count"
    
    # Check if all required services are already running
    local required_services_running=false
    if [ "$before_count" -gt 0 ]; then
        # Check if the services we need are already up
        if docker-compose -f "$docker_compose_file" --profile "$profile" ps | grep -q "Up.*healthy\|Up.*running"; then
            required_services_running=true
            log_info "Required services are already running and healthy."
        fi
    fi
    
    # Only start services if they're not already running properly
    if [ "$required_services_running" = false ]; then
        log_info "Starting Docker services..."
        # Try to start services and capture output
        local output
        if output=$(docker-compose -f "$docker_compose_file" --profile "$profile" up -d 2>&1); then
            local exit_code=0
        else
            local exit_code=$?
        fi
        
        # Debug: Show exit code
        log_info "Docker compose exit code: $exit_code"
        
        # Show output but filter out common "already running" messages
        echo "$output" | grep -v "is already in use" | grep -v "port is already allocated" || true
    else
        log_info "Docker services already running, skipping start."
        local exit_code=0
    fi
    
    # Check what services are running after we start
    local after_output
    after_output=$(docker-compose -f "$docker_compose_file" --profile "$profile" ps 2>/dev/null)
    local after_count
    after_count=$(echo "$after_output" | grep -c "Up" | tr -d '\n' || echo "0")
    log_info "Services running after: $after_count"
    
    # Check if services are actually running
    log_info "Checking if services are running..."
    if docker-compose -f "$docker_compose_file" --profile "$profile" ps | grep -q "Up"; then
        log_info "Docker services are running."
        # Mark that we should cleanup if we started something new
        if [ "$after_count" -gt "$before_count" ]; then
            export SHOULD_CLEANUP=true
            log_info "Set SHOULD_CLEANUP=true (started new containers: $before_count -> $after_count)"
        else
            export SHOULD_CLEANUP=false
            log_info "Set SHOULD_CLEANUP=false (no new containers started: $before_count -> $after_count)"
        fi
        return 0
    else
        log_error "Error: Failed to start Docker services!"
        log_error "Output was: $output"
        exit 1
    fi
}

# Cleanup function
cleanup_docker() {
    local profile="$1"
    local docker_compose_file="$2"
    
    log_info "=== Cleaning Up Docker Environment ==="
    docker-compose -f "$docker_compose_file" --profile "$profile" down
    log_info "Docker containers stopped and removed."
}

# Common cleanup setup
setup_cleanup() {
    local profile="$1"
    local docker_compose_file="$2"
    local cleanup_enabled="${3:-true}"
    
    CLEANUP_DOCKER="$cleanup_enabled"
    DOCKER_PROFILE="$profile"
    DOCKER_COMPOSE_FILE="$docker_compose_file"
    SHOULD_CLEANUP=false  # Default to false, will be set by start_docker_services
    
    # Cleanup function
    cleanup() {
        if [ "$CLEANUP_DOCKER" = true ]; then
            cleanup_docker "$DOCKER_PROFILE" "$DOCKER_COMPOSE_FILE"
        fi
    }
    
    # Set trap for cleanup on script exit
    trap cleanup EXIT
}
