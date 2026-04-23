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

    object GroupCall: NavRoute("groupCall") {
        fun createRoute(callId: String, myUserId: String, callerName: String, isCaller: Boolean, isVideoCall: Boolean): String {
            return "groupCall/$callId/$myUserId/$callerName/$isCaller/$isVideoCall"
        }
    }

    object OneToOne: NavRoute("oneToOne") {
        fun createRoute(callId: String, myUserId: String, callerName: String, isCaller: Boolean, isVideoCall: Boolean): String {
            return "oneToOne/$callId/$myUserId/$callerName/$isCaller/$isVideoCall"
        }
    }

    object Profile: NavRoute("profile")
    object CallHistory: NavRoute("call_history")
    object Auth: NavRoute("auth")

    object GroupInfo: NavRoute("group_info") {
        fun createRoute(groupId: String, currentUserId: String): String {
            return "group_info/$groupId/$currentUserId"
        }
    }

    object ChatInfo: NavRoute("chat_info") {
        fun createRoute(userId: String): String {
            return "chat_info/$userId"
        }
    }
}