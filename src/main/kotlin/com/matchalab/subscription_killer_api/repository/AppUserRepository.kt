package com.matchalab.subscription_killer_api.repository

import com.matchalab.subscription_killer_api.domain.AppUser
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.*

interface AppUserRepository : JpaRepository<AppUser, UUID> {
    @Query(
        """
        SELECT DISTINCT u 
        FROM AppUser u 
        JOIN FETCH u.googleAccounts ga 
        WHERE ga.subject = :subject
    """
    )
    fun findByGoogleAccounts_Subject(subject: String): AppUser?

    @Query("SELECT ga.subject FROM AppUser u JOIN u.googleAccounts ga WHERE u.id = :appUserId")
    fun findGoogleAccountSubjectsByAppUserId(appUserId: UUID): List<String>

    @Query("SELECT u FROM AppUser u LEFT JOIN FETCH u.googleAccounts WHERE u.id = :id")
    fun findByIdWithAccounts(id: UUID): AppUser?

    @Query("SELECT COUNT(u) > 0 FROM AppUser u JOIN u.googleAccounts ga WHERE ga.subject = :subject")
    fun existsByGoogleAccounts_Subject(subject: String): Boolean
}
