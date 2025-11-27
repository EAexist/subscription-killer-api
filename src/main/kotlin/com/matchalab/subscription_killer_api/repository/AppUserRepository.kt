package com.matchalab.subscription_killer_api.repository

import com.matchalab.subscription_killer_api.domain.AppUser
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface AppUserRepository : JpaRepository<AppUser, UUID> {}
