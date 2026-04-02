#!/bin/bash
set -e

# Configuration
HEALTH_URL="http://localhost:8080/actuator/health"
BENCHMARK_START_URL="http://localhost:8080/api/benchmark/start"
BENCHMARK_URL="http://localhost:8080/api/benchmark/analyze"
MAX_WAIT_TIME=300
CHECK_INTERVAL=5
FLUSH_WAIT_TIME=10  # Wait time for observations to flush to Langfuse
BENCHMARK_RUNS=2  # Number of benchmark runs to perform (default)
NO_CLEANUP=true # Whether to skip cleanup and keep containers running

source "$(dirname "${BASH_SOURCE[0]}")/bootRun.sh"

# Load environment variables from .env.benchmark
load_env_benchmark() {
    local env_file="$(dirname "${BASH_SOURCE[0]}")/../.env.benchmark"
    if [ -f "$env_file" ]; then
        log_info "Loading environment variables from .env.benchmark..."
        # Export each variable from the file
        set -a
        source "$env_file"
        set +a
        log_info "Environment variables loaded from .env.benchmark"
    else
        log_warn ".env.benchmark file not found at $env_file"
    fi
}

cleanup() {
    if [ "$NO_CLEANUP" = true ]; then
        echo -e "\n${GREEN}[INFO] NO_CLEANUP flag set - keeping containers running for debugging${NC}"
        echo -e "\n${YELLOW}[INFO] To stop manually: Ctrl+C or run: docker-compose down${NC}"
        echo -e "\n${YELLOW}[INFO] App is running at: http://localhost:8080${NC}"
        
        # Keep the script running but don't cleanup
        trap - EXIT INT TERM  # Remove traps
        while true; do
            sleep 60
        done
        return
    fi
    
    echo -e "\n${YELLOW}[INFO] Waiting ${FLUSH_WAIT_TIME}s for observations to flush to Langfuse...${NC}"
    sleep $FLUSH_WAIT_TIME

    echo -e "\n${YELLOW}[INFO] Terminating processes...${NC}"
    
    # Kill the bootRun process gracefully first
    [ -n "$BOOTRUN_PID" ] && kill "$BOOTRUN_PID" 2>/dev/null || true

        # Wait a moment for graceful shutdown
    sleep 2
    
    # Force kill if still running
    [ -n "$BOOTRUN_PID" ] && kill -9 "$BOOTRUN_PID" 2>/dev/null || true
    
    # Kill the entire process group as backup
    trap - EXIT # Prevent recursion
    kill -9 -$$ 2>/dev/null || true
}

# Ensure cleanup runs on exit or manual interrupt (Ctrl+C)
trap cleanup EXIT INT TERM

main() {
    # Parse arguments
    for arg in "$@"; do
        case "$arg" in
            --no-cleanup)
                NO_CLEANUP=true
                echo -e "${YELLOW}[INFO] No cleanup mode enabled - containers will stay running${NC}"
                ;;
            --help|-h)
                echo "Usage: $0 [BENCHMARK_RUNS] [--no-cleanup] [--help]"
                echo "  BENCHMARK_RUNS: Number of benchmark runs to perform (default: 2)"
                echo "  --no-cleanup: Keep containers running after benchmark for debugging"
                echo "  --help: Show this help message"
                exit 0
                ;;
            -*)
                log_error "Unknown option: $arg"
                echo "Usage: $0 [BENCHMARK_RUNS] [--no-cleanup] [--help]"
                exit 1
                ;;
            *)
                if [ -z "$BENCHMARK_RUNS_ARG" ]; then
                    BENCHMARK_RUNS_ARG="$arg"
                else
                    log_error "Too many arguments"
                    echo "Usage: $0 [BENCHMARK_RUNS] [--no-cleanup] [--help]"
                    exit 1
                fi
                ;;
        esac
    done

    # Override default if BENCHMARK_RUNS argument was provided
    if [ -n "$BENCHMARK_RUNS_ARG" ]; then
        BENCHMARK_RUNS="$BENCHMARK_RUNS_ARG"
    fi

    LOG_FILE=$(mktemp)
    STATUS_FILE=$(mktemp)

    # Load environment variables from .env.benchmark
    load_env_benchmark

    log_info "Step 1: Starting bootRun..."
    # --no-daemon is crucial so the process stays attached to this shell's PID tree
    start_bootrun --profiles=benchmark 2>&1 | tee "$LOG_FILE" &
    BOOTRUN_PID=$!

# Step 2: Health Check
    log_info "Step 2: Waiting for health check..."
    local elapsed=0
    while [ "$(curl -s -o /dev/null -w "%{http_code}" "$HEALTH_URL")" != "200" ]; do
        # NEW: Check if the background process died (compile error / crash)
        if ! kill -0 "$BOOTRUN_PID" 2>/dev/null; then
            echo -e "\n"
            log_error "Process died (Compile error or Startup crash). Check logs above."
            exit 1
        fi

        if [ $elapsed -ge $MAX_WAIT_TIME ]; then
            log_error "Timeout: App failed to start."
            exit 1
        fi
        echo -n "."
        sleep $CHECK_INTERVAL
        ((elapsed+=CHECK_INTERVAL))
    done
    log_success "App Ready!"

    # Step 3: Initialize Benchmark and Get Traceparent
    log_info "Step 3: Initializing benchmark and getting traceparent..."

    # Generate a unique runId for this benchmark run
    RUN_ID="test-run-$(date +%s)-$$"
    log_info "Generated runId: $RUN_ID"

    # Initialize benchmark and get traceparent (once per test run)
    local start_response=$(curl -s -w "\nHTTP_CODE:%{http_code}" -X POST "${BENCHMARK_START_URL}?runId=${RUN_ID}")
    local start_http_code=$(echo "$start_response" | tail -n1 | cut -d: -f2)
    local start_body=$(echo "$start_response" | sed '$d')

    if [[ "$start_http_code" != "200" ]]; then
        log_error "Failed to initialize benchmark (HTTP $start_http_code)"
        echo "$start_body"
        exit 1
    fi

    # Extract traceparent from response
    local traceparent=$(echo "$start_body" | jq -r '.traceparent' 2>/dev/null)
    if [[ -z "$traceparent" || "$traceparent" == "null" ]]; then
        log_error "Failed to extract traceparent from benchmark start response"
        echo "$start_body"
        exit 1
    fi

    log_success "Benchmark initialized with runId: $RUN_ID, traceparent: $traceparent"

    # Step 4: Run Benchmark Requests
    log_info "Step 4: Running Benchmark ($BENCHMARK_RUNS requests with different UUIDs)..."
    
    # Function to run benchmark with a specific UUID
    run_benchmark() {
        local uuid=$1
        local attempt=$2

        log_info "Running benchmark attempt $attempt with UUID: $uuid (traceparent: $traceparent)"
        
        # Perform request and capture code/body with distributed tracing header
        local response=$(curl -s -w "\nHTTP_CODE:%{http_code}" -X POST "$BENCHMARK_URL" \
             -H "Content-Type: application/json" \
             -H "X-Benchmark-User-Id: $uuid" \
             -H "X-Benchmark-Index: $attempt" \
             -H "traceparent: $traceparent")
        
        # Process results
        HTTP_CODE=$(echo "$response" | tail -n1 | cut -d: -f2)
        BODY=$(echo "$response" | sed '$d')
        
        if [[ "$HTTP_CODE" =~ ^20[0-1]$ ]]; then
            log_success "Benchmark attempt $attempt success (HTTP $HTTP_CODE)"
            echo "$BODY" | jq . 2>/dev/null || echo "$BODY"
            return 0
        else
            log_error "Benchmark attempt $attempt failed (HTTP $HTTP_CODE)"
            echo "$BODY"
            return 1
        fi
    }
    
    # Function to generate UUID (cross-platform)
    generate_uuid() {
        if command -v uuidgen >/dev/null 2>&1; then
            uuidgen
        elif command -v powershell >/dev/null 2>&1; then
            powershell -Command "[System.Guid]::NewGuid().ToString()"
        else
            # Fallback: generate random hex string
            date +%s%N | sha256sum | head -c 32
        fi
    }
    
    # Run benchmarks in a loop
    overall_success=true
    for ((i=1; i<=BENCHMARK_RUNS; i++)); do
        if [ $i -gt 1 ]; then
            echo ""  # Add spacing between attempts
        fi
        
        UUID=$(generate_uuid)
        run_benchmark "$UUID" "$i"
        result=$?
        
        if [ $result -ne 0 ]; then
            overall_success=false
        fi
    done
    
    # Step 5: Final Results
    if $overall_success; then
        log_success "All $BENCHMARK_RUNS benchmark attempts completed successfully"
        exit 0
    else
        log_error "One or more benchmark attempts failed"
        exit 1
    fi
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi