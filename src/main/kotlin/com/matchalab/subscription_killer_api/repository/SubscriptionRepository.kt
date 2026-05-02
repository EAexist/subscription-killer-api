package com.matchalab.subscription_killer_api.repository

import com.matchalab.subscription_killer_api.domain.GoogleAccount
import com.matchalab.subscription_killer_api.subscription.Subscription
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface SubscriptionRepository : JpaRepository<Subscription, UUID> {

    fun findByGoogleAccountSubjectAndServiceProviderId(
        googleAccountId: String,
        serviceProviderId: UUID
    ): Subscription?

    @Query(
        """
        SELECT DISTINCT s FROM Subscription s 
        JOIN FETCH s.serviceProvider sp 
        JOIN FETCH sp.emailSources 
        JOIN FETCH s.googleAccount 
        WHERE s.id IN :ids
    """
    )
    fun findAllWithDetailsByIds(@Param("ids") ids: Collection<UUID>): List<Subscription>

    @Query(
        """
        SELECT DISTINCT s FROM Subscription s 
        JOIN FETCH s.serviceProvider sp 
        JOIN FETCH sp.emailSources 
        JOIN FETCH s.googleAccount g
        WHERE g.subject = :subject
    """
    )
    fun findAllWithDetailsByGoogleAccountSubject(@Param("subject") subject: String): List<Subscription>

    fun findAllByGoogleAccountSubject(subject: String): List<Subscription>

    @Query(
        """
        SELECT s FROM Subscription s 
        JOIN FETCH s.googleAccount 
        JOIN FETCH s.serviceProvider 
        WHERE s.googleAccount IN :accounts
    """
    )
    fun findAllByGoogleAccountIn(
        @Param("accounts") accounts: Collection<GoogleAccount>
    ): List<Subscription>

    @Query("SELECT COUNT(s) FROM Subscription s WHERE s.serviceProvider.id = :serviceProviderId")
    fun countByServiceProviderId(serviceProviderId: UUID): Long

    @Query(
        """
        SELECT s FROM Subscription s 
        JOIN FETCH s.googleAccount 
        JOIN FETCH s.serviceProvider 
        WHERE s.googleAccount.subject IN :subjects
    """
    )
    fun findAllByGoogleAccountSubjectIn(
        @Param("subjects") subjects: Collection<String>
    ): List<Subscription>
}
