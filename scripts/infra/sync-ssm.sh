#!/usr/bin/env bash

set -e

#https://stackoverflow.com/a/72833612
PREFIX="  /stg/sublog-api"

sync_ssm() {
    local file=$1
    local type=$2

    if [ -f "$file" ]; then
        # Use tr to remove carriage returns (CRLF fix)
        while read -r line || [[ -n "$line" ]]; do
            [[ -z "$line" || "$line" =~ ^# ]] && continue

            # Trim whitespace and split
            line=$(echo "$line" | tr -d '\r')
            key="${line%%=*}"
            value="${line#*=}"

            # Remove surrounding quotes
            value="${value%\"}"
            value="${value#\"}"

            aws ssm put-parameter \
                --name "$PREFIX/$key" \
                --value "$value" \
                --type "$type" \
                --overwrite \
                # --tags "Key=Project,Value=sublog-api" "Key=Environment,Value=stg"

        done < "$file"
    fi
}

sync_ssm ".env.ssm.string" "String"
# Temporarily use String type for SecureString values
sync_ssm ".env.ssm.secure-string" "String"