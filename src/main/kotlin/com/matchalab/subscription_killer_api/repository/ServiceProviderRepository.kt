package com.matchalab.subscription_killer_api.repository

import com.matchalab.subscription_killer_api.domain.LocaleType
import com.matchalab.subscription_killer_api.subscription.ServiceProvider
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*


interface ServiceProviderRepository : JpaRepository<ServiceProvider, UUID> {

    fun findByDisplayName(displayName: String): ServiceProvider?

    @Query(
        """
        SELECT DISTINCT sp FROM ServiceProvider sp 
        LEFT JOIN FETCH sp.subscriptions s
        WHERE sp.id = :id
    """
    )
    fun findByIdWithSubscriptions(id: UUID): ServiceProvider?

    @Query("SELECT sp FROM ServiceProvider sp JOIN sp.aliasNames a WHERE KEY(a) = :locale AND VALUE(a) = :aliasName")
    fun findByLocaleAndAlias(
        @Param("locale") locale: LocaleType?,
        @Param("aliasName") aliasName: String?
    ): ServiceProvider?

    @Query(
        """
        SELECT DISTINCT sp FROM ServiceProvider sp 
        LEFT JOIN FETCH sp.emailSources 
        JOIN sp.aliasNames a 
        WHERE VALUE(a) = :aliasName
    """
    )
    fun findByAliasNameWithEmailSources(@Param("aliasName") aliasName: String?): ServiceProvider?

    @Query(
        """
        SELECT DISTINCT sp 
        FROM ServiceProvider sp 
        JOIN FETCH sp.emailSources es 
        WHERE es.isActive = true 
        AND es.targetAddress IN :addressList
    """
    )
    fun findByActiveEmailAddressesInWithEmailSources(addressList: List<String>): List<ServiceProvider>

//    @Query(
//        """
//    SELECT DISTINCT sp FROM ServiceProvider sp
//    LEFT JOIN FETCH sp.emailSources
//    LEFT JOIN FETCH sp.aliasNames
//"""
//    )
//    fun findAllWithEmailSourcesAndAliases(): List<ServiceProvider>

    // Step 1: Fetch with one collection
    @Query("SELECT DISTINCT sp FROM ServiceProvider sp LEFT JOIN FETCH sp.emailSources")
    fun findAllWithEmailSources(): List<ServiceProvider>

    // Step 2: Fetch the aliases for those providers (Hibernate populates the cache)
    @Query("SELECT DISTINCT sp FROM ServiceProvider sp LEFT JOIN FETCH sp.aliasNames")
    fun findAllWithAliases(): List<ServiceProvider>

    @EntityGraph(attributePaths = ["emailSources"])
    fun findWithEmailSourceAllByIdIn(ids: Iterable<UUID>): List<ServiceProvider>

    @EntityGraph(attributePaths = ["emailSources"])
    fun findWithEmailSourceById(id: UUID): ServiceProvider?

//    @Query(
//        """
//        SELECT DISTINCT sp FROM ServiceProvider sp
//        LEFT JOIN FETCH sp.emailSources
//        WHERE sp IN :providers
//    """
//    )
//    fun fetchWithEmailSources(providers: List<ServiceProvider>): List<ServiceProvider>
//
//
//    @Query("SELECT sp FROM ServiceProvider sp LEFT JOIN FETCH sp.emailSources WHERE sp.id = :id")
//    fun findByIdWithEmailSources(id: UUID): ServiceProvider?
//
//    @Query(
//        """
//        SELECT DISTINCT s FROM ServiceProvider s
//        JOIN s.aliasNames a
//        WHERE VALUE(a) IN :aliasNames
//    """
//    )
//    fun findAllByAliasNameIn(@Param("aliasNames") aliasNames: Collection<String>): List<ServiceProvider>


}
