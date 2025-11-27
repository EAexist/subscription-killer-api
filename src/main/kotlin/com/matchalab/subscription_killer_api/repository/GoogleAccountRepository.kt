package com.matchalab.subscription_killer_api.repository

import com.matchalab.subscription_killer_api.domain.GoogleAccount
import org.springframework.data.jpa.repository.JpaRepository

interface GoogleAccountRepository : JpaRepository<GoogleAccount, String> {}
