package com.matchalab.subscription_killer_api.repository

import com.matchalab.subscription_killer_api.domain.AppUser
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

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
}
