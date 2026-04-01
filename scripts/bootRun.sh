#!/bin/bash

# Source common setup functions
source "$(dirname "${BASH_SOURCE[0]}")/common-setup.sh"

cleanup_old_logs() {
    local log_dir="$1"
    local keep_count="$2"
    
    if [ -d "$log_dir" ]; then
        # Remove old log files, keeping only the most recent $keep_count
        find "$log_dir" -name "bootrun_*.log" -type f | sort -r | tail -n +$((keep_count + 1)) | xargs -r rm
        log_info "Cleaned up old log files, keeping last $keep_count"
    fi
}

start_bootrun() {
    local docker_compose_file="docker-compose.yaml"
    local observe_flag=false
    local custom_profiles=""
    local filtered_args=()

    # Parse arguments
    for arg in "$@"; do
        case "$arg" in
            --help|-h)
                echo "Usage: start_bootrun [--observe] [--profiles=<profiles>] [args...]"
                return 0 ;;
            --observe)
                observe_flag=true ;;
            --profiles=*)
                custom_profiles="${arg#*=}" ;;
            *)
                filtered_args+=("$arg") ;;
        esac
    done

    setup_colors

    # Unified Docker/Cleanup Logic
    local profile_param=""
    [ "$observe_flag" = true ] && profile_param="observe"
    
    log_info "=== Starting application (Observe: $observe_flag) ==="
    setup_cleanup "$profile_param" "$docker_compose_file" true
    start_docker_services "$profile_param" "$docker_compose_file"
    wait_for_db "$docker_compose_file"

    setup_ssl_env
    setup_google_env

    # Determine Profiles
    SPRING_PROFILES=${custom_profiles:-"dev,oauth,gmail,local-ssl"}
    log_info "Using profiles: $SPRING_PROFILES"
    export SPRING_PROFILES_ACTIVE="$SPRING_PROFILES"

    # Setup logging directory and file
    local log_dir="logs"
    local timestamp=$(date '+%Y%m%d_%H%M%S')
    local log_file="$log_dir/bootrun_${timestamp}.log"
    
    # Create logs directory if it doesn't exist
    mkdir -p "$log_dir"
    
    # Cleanup old log files (keep last 5)
    cleanup_old_logs "$log_dir" 5
    
    log_info "Logging to: $log_file"
    
    # Load .env
    [ -f .env ] && export $(grep -v '^#' .env | sed "s/['\"]//g" | xargs)

    ./gradlew bootRun --console=plain --no-daemon "${filtered_args[@]}" 2>&1 | tee "$log_file"
}

# Execute if run directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    start_bootrun "$@"
fi