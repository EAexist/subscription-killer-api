//https://docs.aws.amazon.com/ko_kr/lambda/latest/dg/lambda-cdk-tutorial.html#lambda-cdk-step-2
package com.matchalab.subscription_killer_api.infra

import software.amazon.awscdk.*
import software.amazon.awscdk.services.lambda.*
import software.amazon.awscdk.services.lambda.Function
import software.constructs.Construct
import java.io.File

class SubscriptionKillerApiStack(scope: Construct?, id: String?, props: StackProps?, env: String = "stg") :
    Stack(scope, id, props) {
    constructor(scope: Construct?, id: String?) : this(scope, id, null)

    init {

        Tags.of(this).add("Environment", env)
        Tags.of(this).add("Application", "SubscriptionKillerApi")
        
        val artifactPath = File("..", "build/distributions/subscription-killer-api-0.0.1-SNAPSHOT.zip").path

        val handler = Function.Builder.create(this, "Handler")
            .reservedConcurrentExecutions(10)
            .functionName("subscription-killer-api-handler-$env")
            .runtime(Runtime.JAVA_21)
            .memorySize(2048) // Required for Spring Boot performance
            .timeout(Duration.minutes(15))
            .code(
                Code.fromAsset(artifactPath)
            )
            .handler("com.matchalab.subscription_killer_api.StreamLambdaHandler::handleRequest") // Future entry point
            .build()

        val fnUrl = handler.addFunctionUrl(
            FunctionUrlOptions.builder()
                .authType(FunctionUrlAuthType.NONE) // Use NONE for public, or IAM for secure
                .invokeMode(InvokeMode.RESPONSE_STREAM) // CRITICAL for SSE
                .build()
        )

        CfnOutput.Builder.create(this, "ApiUrl").value(fnUrl.url).build()
    }
}