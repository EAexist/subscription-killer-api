#!/bin/bash

# Using -cp because we are dealing with loose jars in the lib directory
exec java -cp "lib/*" com.matchalab.subscription_killer_api.SubscriptionKillerApiApplicationKt

#  Disabled Flags
#  -XX:+TieredCompilation \
#  -XX:TieredStopAtLevel=1 \
#  -Xshare:off \
#  -Dspring.main.lazy-initialization=true \