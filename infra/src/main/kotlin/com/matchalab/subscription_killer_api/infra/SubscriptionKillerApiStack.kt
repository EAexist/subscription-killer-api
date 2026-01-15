//https://docs.aws.amazon.com/ko_kr/lambda/latest/dg/lambda-cdk-tutorial.html#lambda-cdk-step-2
package com.matchalab.subscription_killer_api.infra

import software.constructs.Construct
import software.amazon.awscdk.Stack
import software.amazon.awscdk.StackProps

// import software.amazon.awscdk.Duration;
// import software.amazon.awscdk.services.sqs.Queue;
class SubscriptionKillerApiStack(scope: Construct?, id: String?, props: StackProps?) : Stack(scope, id, props) {
    constructor(scope: Construct?, id: String?) : this(scope, id, null)

    init {
        val hello = Function.Builder.create(this, "SubscriptionKillerApiFunction")
            .runtime(Runtime.NODEJS_LATEST)
            .code(Code.fromAsset("lib/lambda-handler"))
            .handler("index.handler")
            .build()

        LambdaRestApi.Builder.create(this, "ApiGwEndpoint")
            .restApiName("SubscriptionKillerApi")
            .handler(hello)
            .build()
    }
}