package com.matchalab.subscription_killer_api.domain

sealed class LoginOrRegisterResult {

    abstract val appUser: AppUser
    data class Registered(override val appUser: AppUser) : LoginOrRegisterResult()
    data class LoggedIn(override val appUser: AppUser) : LoginOrRegisterResult()
}
