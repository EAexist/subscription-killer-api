#!/bin/bash

# Using -cp because we are dealing with loose jars in the lib directory
# exec java -cp "lib/*" com.matchalab.sublog_api.SubscriptionKillerApiApplicationKt
exec java -jar lib/sublog-api-0.0.1-SNAPSHOT.jar

#  Disabled Flags
#  -XX:+TieredCompilation \
#  -XX:TieredStopAtLevel=1 \
#  -Xshare:off \
#  -Dspring.main.lazy-initialization=true \