package com.matchalab.subscription_killer_api.repository

import com.matchalab.subscription_killer_api.subscription.EmailSource
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface EmailSourceRepository : JpaRepository<EmailSource, UUID> {
    //    fun findByTargetAddress(targetAddress: String): EmailSource?
//    fun findAllByTargetAddress(targetAddresses: List<String>): MutableList<EmailSource>
    @Query("SELECT es.targetAddress FROM EmailSource es WHERE es.targetAddress IN :addresses")
    fun findExistingAddresses(@Param("addresses") addresses: Collection<String>): Set<String>
}
