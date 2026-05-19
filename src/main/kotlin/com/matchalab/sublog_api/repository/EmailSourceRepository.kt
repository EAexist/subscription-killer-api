package com.matchalab.sublog_api.repository

import com.matchalab.sublog_api.subscription.EmailSource
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.util.*

interface EmailSourceRepository : JpaRepository<EmailSource, UUID> {
    @Query("SELECT es.targetAddress FROM EmailSource es WHERE es.targetAddress IN :addresses")
    fun findExistingAddresses(@Param("addresses") addresses: Collection<String>): Set<String>

    @Modifying
    @Transactional
    @Query(value = "UPDATE email_source SET event_rules = '[]'::json", nativeQuery = true)
    fun clearAllEventRules(): Int

    @Query("SELECT e FROM EmailSource e JOIN FETCH e.serviceProvider WHERE e.id = :id")
    fun findByIdWithServiceProvider(id: UUID): EmailSource?
}
