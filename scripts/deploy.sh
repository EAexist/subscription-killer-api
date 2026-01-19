#!/bin/bash
set -eo pipefail

if [ ! -f build/distributions/myapp.zip ]; then
    echo "Error: ZIP file not found. Run ./gradlew buildLambdaWebAdapterZip."
    exit 1
fi

BUCKET=${DEPLOY_BUCKET_NAME:-my-default-dev-bucket}

echo "Packaging using bucket: $BUCKET"

aws cloudformation package --template-file template.yml --s3-bucket $BUCKET --output-template-file out.yml
aws cloudformation deploy --template-file out.yml --stack-name java-basic --capabilities CAPABILITY_NAMED_IAM