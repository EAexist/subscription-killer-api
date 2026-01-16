//https://docs.aws.amazon.com/ko_kr/lambda/latest/dg/lambda-cdk-tutorial.html#lambda-cdk-step-2
package com.matchalab.subscription_killer_api.infra

import software.amazon.awscdk.*
import software.amazon.awscdk.services.lambda.*
import software.amazon.awscdk.services.lambda.Function
import software.amazon.awscdk.services.ssm.StringParameter
import software.constructs.Construct
import java.io.File


class SubscriptionKillerApiStack(scope: Construct?, id: String?, props: StackProps?, env: String = "stg") :
    Stack(scope, id, props) {
    constructor(scope: Construct?, id: String?) : this(scope, id, null)

    init {

        Tags.of(this).add("Environment", env)
        Tags.of(this).add("Application", "SubscriptionKillerApi")

        val artifactPath = File("..", "build/distributions/subscription-killer-api-0.0.1-SNAPSHOT.zip").path

        val appGoogleClientId =
            StringParameter.valueForStringParameter(this, "/stg/subscription-killer-api/APP_GOOGLE_CLIENT_ID")
        val appGoogleClientSecret =
            StringParameter.valueForStringParameter(this, "/stg/subscription-killer-api/APP_GOOGLE_CLIENT_SECRET")
        val dbEndpoint =
            StringParameter.valueForStringParameter(this, "/stg/subscription-killer-api/DB_ENDPOINT")
        val dbName =
            StringParameter.valueForStringParameter(this, "/stg/subscription-killer-api/DB_NAME")
        val dbPassword =
            StringParameter.valueForStringParameter(this, "/stg/subscription-killer-api/DB_PASSWORD")
        val dbUser =
            StringParameter.valueForStringParameter(this, "/stg/subscription-killer-api/DB_USER")

        val handler = Function.Builder.create(this, "Handler")
//            .reservedConcurrentExecutions(10)
            .functionName("subscription-killer-api-handler-$env")
            .runtime(Runtime.JAVA_21)
            .memorySize(2048) // Required for Spring Boot performance
            .timeout(Duration.minutes(15))
            .handler("com.matchalab.subscription_killer_api.StreamLambdaHandler::handleRequest") // Future entry point
            .code(
                Code.fromAsset(artifactPath)
            )
            .environment(
                mapOf(
                    "APP_GOOGLE_CLIENT_ID" to appGoogleClientId,
                    "APP_GOOGLE_CLIENT_SECRET" to appGoogleClientSecret,
                    "DB_ENDPOINT" to dbEndpoint,
                    "DB_NAME" to dbName,
                    "DB_PASSWORD" to dbPassword,
                    "DB_USER" to dbUser,
                )
            )
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