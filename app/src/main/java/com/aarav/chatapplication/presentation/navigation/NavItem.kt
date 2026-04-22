package com.aarav.chatapplication.presentation.navigation

import com.aarav.chatapplication.R

sealed class NavItem(val name: String, val path: String, val icon: Int, val filledIcon: Int) {
    object Chat : NavItem(
        "Home",
        NavRoute.Home.path,
        R.drawable.chat_circle,
        R.drawable.chat_nav_filled
    )
    object Profile : NavItem(
        "Profile",
        NavRoute.Profile.path,
        R.drawable.user_nav,
        R.drawable.user_nav_filled
    )
    object CallHistory : NavItem(
        "Calls",
        NavRoute.CallHistory.path,
        R.drawable.phone,
        R.drawable.phone
    )

}