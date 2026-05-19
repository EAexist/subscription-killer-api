#!/bin/bash
# 1. Create the dummy file
echo "placeholder" > dummy.txt

# 2. Use 'jar' to create the zip (c=create, v=verbose, f=file)
jar -cvf sublog.zip dummy.txt

# 3. Upload to S3
aws s3 cp sublog.zip s3://sublog-stg-deploy/sublog.zip

# 4. Cleanup
rm dummy.txt sublog.zip