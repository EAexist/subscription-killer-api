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

resolve_profile_groups() {
    local profiles="$1"
    local resolved_profiles=""
    local application_yaml="src/main/resources/application.yaml"

    if [ ! -f "$application_yaml" ]; then
        log_info "Application.yaml not found, using profiles as-is"
        echo "$profiles"
        return
    fi

    log_info "Resolving profile groups from application.yaml" >&2

    # Convert comma-separated profiles to array
    IFS=',' read -ra INPUT_PROFILES <<< "$profiles"

    for profile in "${INPUT_PROFILES[@]}"; do
        # Trim whitespace
        profile=$(echo "$profile" | xargs)

        # Check if this profile exists as a group in application.yaml
        # Look for the profile group pattern and extract the list items
        local group_profiles=$(sed -n "/^      $profile:/,/^      [a-zA-Z]/p" "$application_yaml" | \
            grep "^        - " | \
            sed 's/^        - //' | \
            sed 's/"//g' | \
            tr '\n' ',' | \
            sed 's/,$//')

        log_info "Checking for profile group '$profile'" >&2
        if [ -n "$group_profiles" ]; then
            log_info "Found profile group '$profile' with sub-profiles: $group_profiles" >&2
            # Add the resolved sub-profiles
            if [ -n "$resolved_profiles" ]; then
                resolved_profiles="$resolved_profiles,$group_profiles"
            else
                resolved_profiles="$group_profiles"
            fi
        else
            # This is not a group, add it as-is
            log_info "Profile '$profile' is not a group, using as-is" >&2
            if [ -n "$resolved_profiles" ]; then
                resolved_profiles="$resolved_profiles,$profile"
            else
                resolved_profiles="$profile"
            fi
        fi
    done

    log_info "Resolved profiles: $resolved_profiles" >&2
    echo "$resolved_profiles"
}

load_env_files_by_profiles() {
    local profiles="$1"
    log_info "Loading environment files for profiles: $profiles"
    
    # Resolve profile groups first
    local resolved_profiles=$(resolve_profile_groups "$profiles")

    # Convert comma-separated profiles to array
    IFS=',' read -ra PROFILE_ARRAY <<< "$resolved_profiles"
    
    # Load environment files for each profile
    for profile in "${PROFILE_ARRAY[@]}"; do
        # Trim whitespace
        profile=$(echo "$profile" | xargs)
        
        # Convert profile name to env file naming convention
        # Replace hyphens with dots and add .env.dev prefix
        env_file=".env.${profile//-/.}"
        
        if [ -f "$env_file" ]; then
            log_info "Loading environment file: $env_file"
            export $(grep -v '^#' "$env_file" | sed "s/['\"]//g" | xargs)
        else
            log_info "Environment file not found: $env_file (skipping)"
        fi
    done
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

    # Load environment files based on profiles
    load_env_files_by_profiles "$SPRING_PROFILES"

    # Setup logging directory and file
    local log_dir="logs"
    local timestamp=$(date '+%Y%m%d_%H%M%S')
    local log_file="$log_dir/bootrun_${timestamp}.log"
    
    # Create logs directory if it doesn't exist
    mkdir -p "$log_dir"
    
    # Cleanup old log files (keep last 5)
    cleanup_old_logs "$log_dir" 5
    
    log_info "Logging to: $log_file"

    ./gradlew bootRun --console=plain "${filtered_args[@]}" 2>&1 | tee "$log_file"
}

# Execute if run directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    start_bootrun "$@"
fi