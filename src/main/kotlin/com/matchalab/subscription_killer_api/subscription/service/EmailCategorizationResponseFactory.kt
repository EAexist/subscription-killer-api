package com.matchalab.subscription_killer_api.subscription.service

import com.matchalab.subscription_killer_api.ai.dto.EmailCategorizationResponse

object EmailCategorizationResponseFactory {
    fun createSample(): EmailCategorizationResponse = EmailCategorizationResponse(
        listOf(
            "19b5eb4d89185432",
            "19b633ef8816f70b",
            "195bfcf179dd517a",
            "1905d62e1b5a4cc4",
            "1940e8e5a2e2c7d6",
            "197bc3a30d07e1a5",
            "198153271fe48cf1"
        ), listOf(
            "18fffe1927227419",
            "1901483a2036e020",
            "19b5eb36c656f8a3",
            "19b5eb7b6405f519",
            "195ee970f2954eb3",
            "190820cc8e9fd673",
            "1948ca756537adce",
            "19813abc202ea678",
            "19813abc3047adb3",
        ), listOf(
            "185becf0be8bdb2e",
            "1865e727c94cc0e0",
            "186eea2c4818cdaa",
            "1878e46c00da9d9c",
            "18828c7be8492286",
            "188c869cad94abb3",
            "18962e8d7d1a27bd",
            "18a028df5bb0c316",
            "18aa230a17350152",
            "18b3caf3134eb04e",
            "18bdc532da5dfead",
            "18c76d18829201b7",
            "18d167b6d08f876c",
            "18db61d55ee2d115",
            "18e4b75a73dc59d0",
            "18eeb1a9467cd67f",
            "18f8598119fb43e5",
        ), listOf<String>(
        )
    )

    fun createUniqueSample(): EmailCategorizationResponse = EmailCategorizationResponse(
        listOf(
            "19b5eb4d89185432",
            "19b633ef8816f70b",
            "195bfcf179dd517a",
            "1905d62e1b5a4cc4",
            "1940e8e5a2e2c7d6",
            "197bc3a30d07e1a5",
            "198153271fe48cf1"
        ), listOf(
            "18fffe1927227419",
            "1901483a2036e020",
            "19b5eb36c656f8a3",
            "19b5eb7b6405f519",
            "195ee970f2954eb3",
            "1948ca756537adce",
            "19813abc202ea678",
            "19813abc3047adb3",
        ), listOf(
            "185becf0be8bdb2e",
            "1878e46c00da9d9c",
            "18e4b75a73dc59d0",
            "18f8598119fb43e5",
        ), listOf<String>(
        )
    )

    fun createAllowedSamples(): EmailCategorizationResponse = EmailCategorizationResponse(
        listOf(
            "1940d5a407867390",
        ), listOf(
        ), listOf(
        ), listOf<String>(
        )
    )
}