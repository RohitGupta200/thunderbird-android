package app.k9mail.feature.navigation.drawer.domain.entity

import app.k9mail.legacy.account.Account

data class DisplayAccount(
    val account: Account,
    val unreadMessageCount: Int,
    val starredMessageCount: Int,
)
