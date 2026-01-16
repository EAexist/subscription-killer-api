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

        val SPRING_PROFILES_ACTIVE = "prod"

        // Frontend
        val FRONTEND_URL =
            StringParameter.valueForStringParameter(this, "/stg/subscription-killer-api/FRONTEND_URL")

        // Database
        val dbEndpoint =
            StringParameter.valueForStringParameter(this, "/stg/subscription-killer-api/DB_ENDPOINT")
        val dbName =
            StringParameter.valueForStringParameter(this, "/stg/subscription-killer-api/DB_NAME")
        val dbPassword =
            StringParameter.valueForStringParameter(this, "/stg/subscription-killer-api/DB_PASSWORD")
        val dbUser =
            StringParameter.valueForStringParameter(this, "/stg/subscription-killer-api/DB_USER")

        // Google Cloud
        val appGoogleClientId =
            StringParameter.valueForStringParameter(this, "/stg/subscription-killer-api/APP_GOOGLE_CLIENT_ID")
        val appGoogleClientSecret =
            StringParameter.valueForStringParameter(this, "/stg/subscription-killer-api/APP_GOOGLE_CLIENT_SECRET")
        val GOOGLE_CLOUD_PROJECT =
            StringParameter.valueForStringParameter(this, "/stg/subscription-killer-api/GOOGLE_CLOUD_PROJECT")
        val SPRING_AI_GOOGLE_GENAI_API_KEY =
            StringParameter.valueForStringParameter(this, "/stg/subscription-killer-api/SPRING_AI_GOOGLE_GENAI_API_KEY")
        val corsAllowedOrigins = StringParameter.valueForStringParameter(
            this, "/stg/subscription-killer-api/CORS_ALLOWED_ORIGINS"
        )

        val handler = Function.Builder.create(this, "Handler")
//            .reservedConcurrentExecutions(10)
            .functionName("subscription-killer-api-handler-$env")
            .runtime(Runtime.JAVA_21)
            .memorySize(2048) // Required for Spring Boot performance
            .timeout(Duration.minutes(3))
            .handler("com.matchalab.subscription_killer_api.StreamLambdaHandler::handleRequest") // Future entry point
            .code(
                Code.fromAsset(artifactPath)
            )
            .environment(
                mapOf(
                    "SPRING_PROFILES_ACTIVE" to SPRING_PROFILES_ACTIVE,
                    "FRONTEND_URL" to FRONTEND_URL,
                    "APP_GOOGLE_CLIENT_ID" to appGoogleClientId,
                    "APP_GOOGLE_CLIENT_SECRET" to appGoogleClientSecret,
                    "GOOGLE_CLOUD_PROJECT" to GOOGLE_CLOUD_PROJECT,
                    "SPRING_AI_GOOGLE_GENAI_API_KEY" to SPRING_AI_GOOGLE_GENAI_API_KEY,
                    "DB_ENDPOINT" to dbEndpoint,
                    "DB_NAME" to dbName,
                    "DB_PASSWORD" to dbPassword,
                    "DB_USER" to dbUser,
                    "APP_CORS_ALLOWED_ORIGINS" to corsAllowedOrigins
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