package com.matchalab.subscription_killer_api.repository

import com.matchalab.subscription_killer_api.domain.GoogleAccount
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface GoogleAccountRepository : JpaRepository<GoogleAccount, String> {

    @Query(
        """
        SELECT DISTINCT ga FROM GoogleAccount ga 
        LEFT JOIN FETCH ga.subscriptions s
        WHERE ga.subject = :subject
    """
    )
    fun findByIdWithSubscriptions(subject: String): GoogleAccount?

    @Query(
        """
        SELECT DISTINCT ga FROM GoogleAccount ga 
        LEFT JOIN FETCH ga.subscriptions s
        LEFT JOIN FETCH s.serviceProvider 
        WHERE ga.subject = :subject
    """
    )
    fun findByIdWithSubscriptionsAndProviders(subject: String): GoogleAccount?

    @Query("""
        SELECT CASE WHEN COUNT(ga) > 0 THEN true ELSE false END 
        FROM GoogleAccount ga 
        WHERE ga.appUser.id = :appUserId 
        AND ga.analyzedAt IS NOT NULL
    """)
    fun existsAnalyzedSubscriptionByAppUserId(appUserId: UUID): Boolean
}
