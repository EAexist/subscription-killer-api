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

        val webAdapterLayer = LayerVersion.fromLayerVersionArn(
            this, "WebAdapterLayer",
            "arn:aws:lambda:ap-northeast-2:753240598075:layer:LambdaAdapterLayerX86:25"
        )

        val artifactPath = File("..", "build/distributions/subscription-killer-api-0.0.1-SNAPSHOT.zip").path

        val SPRING_PROFILES_ACTIVE = "prod"

        // Frontend
        val FRONTEND_URL =
            StringParameter.valueForStringParameter(this, "/stg/subscription-killer-api/FRONTEND_URL")
        val SERVICE_URL =
            StringParameter.valueForStringParameter(this, "/stg/subscription-killer-api/SERVICE_URL")

        // Database
        val SPRING_DATASOURCE_URL =
            StringParameter.valueForStringParameter(this, "/stg/subscription-killer-api/SPRING_DATASOURCE_URL")
        val SPRING_DATASOURCE_USERNAME =
            StringParameter.valueForStringParameter(this, "/stg/subscription-killer-api/SPRING_DATASOURCE_USERNAME")
        val SPRING_DATASOURCE_PASSWORD =
            StringParameter.valueForStringParameter(this, "/stg/subscription-killer-api/SPRING_DATASOURCE_PASSWORD")

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
            .handler("run.sh")
            .code(
                Code.fromAsset(artifactPath)
            )
            .layers(listOf(webAdapterLayer))
            .environment(
                mapOf(
                    // profile
                    "SPRING_PROFILES_ACTIVE" to SPRING_PROFILES_ACTIVE,

                    // frontend
                    "FRONTEND_URL" to FRONTEND_URL,
                    "SERVICE_URL" to SERVICE_URL,
                    "APP_CORS_ALLOWED_ORIGINS" to corsAllowedOrigins,

                    // database
                    "SPRING_DATASOURCE_URL" to SPRING_DATASOURCE_URL,
                    "SPRING_DATASOURCE_USERNAME" to SPRING_DATASOURCE_USERNAME,
                    "SPRING_DATASOURCE_PASSWORD" to SPRING_DATASOURCE_PASSWORD,

                    // google cloud
                    "APP_GOOGLE_CLIENT_ID" to appGoogleClientId,
                    "APP_GOOGLE_CLIENT_SECRET" to appGoogleClientSecret,
                    "GOOGLE_CLOUD_PROJECT" to GOOGLE_CLOUD_PROJECT,
                    "SPRING_AI_GOOGLE_GENAI_API_KEY" to SPRING_AI_GOOGLE_GENAI_API_KEY,

                    // web adapter layer
                    "AWS_LAMBDA_EXEC_WRAPPER" to "/opt/bootstrap",
                    "AWS_LWA_INVOKE_MODE" to "response_stream",
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