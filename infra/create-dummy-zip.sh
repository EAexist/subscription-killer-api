#!/bin/bash
# 1. Create the dummy file
echo "placeholder" > dummy.txt

# 2. Use 'jar' to create the zip (c=create, v=verbose, f=file)
jar -cvf subscription-killer-api.zip dummy.txt

# 3. Upload to S3
aws s3 cp subscription-killer-api.zip s3://subscription-killer-api-stg-deploy/subscription-killer-api.zip

# 4. Cleanup
rm dummy.txt subscription-killer-api.zip