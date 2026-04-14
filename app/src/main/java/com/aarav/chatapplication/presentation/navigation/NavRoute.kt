package com.aarav.chatapplication.presentation.navigation

sealed class NavRoute(val path: String) {
    object Home: NavRoute("home")
    object Chat: NavRoute("chat") {
        fun createRoute(receiverId: String, userId: String, currentUsername: String): String {
            return "chat/$receiverId/$userId/$currentUsername"
        }
    }
    object GroupChat: NavRoute("group_chat") {
        fun createRoute(groupId: String, userId: String, senderName: String): String {
            return "group_chat/$groupId/$userId/$senderName"
        }
    }
    object CreateGroup: NavRoute("create_group") {
        fun createRoute(userId: String): String {
            return "create_group/$userId"
        }
    }

    object Call: NavRoute("call") {
        fun createRoute(callId: String, callerId: String, callerName: String, receiverId: String, isCaller: Boolean): String {
            return "call/$callId/$callerId/$callerName/$receiverId/$isCaller"
        }
    }

    object Profile: NavRoute("profile")
    object Auth: NavRoute("auth")
}
