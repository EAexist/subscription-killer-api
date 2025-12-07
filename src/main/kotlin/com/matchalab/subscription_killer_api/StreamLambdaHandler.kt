package com.matchalab.subscription_killer_api

import com.amazonaws.serverless.exceptions.ContainerInitializationException
import com.amazonaws.serverless.proxy.internal.testutils.Timer
import com.amazonaws.serverless.proxy.model.AwsProxyRequest
import com.amazonaws.serverless.proxy.model.AwsProxyResponse
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class StreamLambdaHandler : RequestStreamHandler {

    init {
        // we enable the timer for debugging. This SHOULD NOT be enabled in production.
        Timer.enable()
    }

    @Throws(IOException::class)
    override fun handleRequest(
            inputStream: InputStream,
            outputStream: OutputStream,
            context: Context
    ) {
        handler.proxyStream(inputStream, outputStream, context)
    }

    companion object {

        private val handler:
                SpringBootLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> by lazy {
            try {
                val initializedHandler =
                        SpringBootLambdaContainerHandler.getAwsProxyHandler(
                                SubscriptionKillerApiApplication::class.java
                        )

                // we use the onStartup method of the handler to register our custom filter
                // initializedHandler.onStartup {servletContext ->
                //     val registration: FilterRegistration.Dynamic =
                // servletContext.addFilter("CognitoIdentityFilter", CognitoIdentityFilter .class)
                //     registration.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST),
                // true, "/*")
                // }
                initializedHandler
            } catch (e: ContainerInitializationException) {
                // if we fail here. We re-throw the exception to force another cold start
                e.printStackTrace()
                throw RuntimeException("Could not initialize Spring Boot application", e)
            }
        }
    }
}
