package com.matchalab.subscription_killer_api.infra

import software.amazon.awscdk.App
import software.amazon.awscdk.Environment
import software.amazon.awscdk.StackProps
import software.amazon.awscdk.Tags

class SubscriptionKillerApiApp {

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            val app: App = App()

            val stagingEnvName = "stg"

            val stgStack: SubscriptionKillerApiStack = SubscriptionKillerApiStack(
                app,
                "SubscriptionKillerApi-Stg-Stack",
                StackProps.builder()
                    // If you don't specify 'env', this stack will be environment-agnostic.
                    // Account/Region-dependent features and context lookups will not work,
                    // but a single synthesized template can be deployed anywhere.
                    // Uncomment the next block to specialize this stack for the AWS Account
                    // and Region that are implied by the current CLI configuration.
                    /*
                    .env(Environment.builder()
                            .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                            .region(System.getenv("CDK_DEFAULT_REGION"))
                            .build())
                    */
                    // Uncomment the next block if you know exactly what Account and Region you
                    // want to deploy the stack to.
                    .env(
                        Environment.builder()
                            .account("660410403413")
                            .region("ap-northeast-2")
                            .build()
                    )
                    // For more information, see https://docs.aws.amazon.com/cdk/latest/guide/environments.html
                    .build(), stagingEnvName
            )
            Tags.of(stgStack).add("Environment", stagingEnvName)

            app.synth()
        }
    }
}