package com.matchalab.subscription_killer_api.repository

import com.matchalab.subscription_killer_api.domain.LocaleType
import com.matchalab.subscription_killer_api.subscription.ServiceProvider
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*


interface ServiceProviderRepository : JpaRepository<ServiceProvider, UUID> {

    @Query("SELECT sp FROM ServiceProvider sp JOIN sp.aliasNames a WHERE KEY(a) = :locale AND VALUE(a) = :aliasName")
    fun findByLocaleAndAlias(
        @Param("locale") locale: LocaleType?,
        @Param("aliasName") aliasName: String?
    ): ServiceProvider?

    @Query("SELECT sp FROM ServiceProvider sp JOIN sp.aliasNames a WHERE VALUE(a) = :aliasName")
    fun findByAliasName(@Param("aliasName") aliasName: String?): ServiceProvider?

    @Query(
        """
        SELECT DISTINCT s FROM ServiceProvider s 
        JOIN s.aliasNames a 
        WHERE VALUE(a) IN :aliasNames
    """
    )
    fun findAllByAliasNameIn(@Param("aliasNames") aliasNames: Collection<String>): List<ServiceProvider>

    @Query(
        """
        SELECT DISTINCT sp 
        FROM ServiceProvider sp 
        JOIN sp.emailSources es 
        WHERE es.isActive = true 
        AND es.targetAddress IN :addressList
    """
    )
    fun findByActiveEmailAddressesIn(addressList: List<String>): List<ServiceProvider>
}
