package com.aarav.chatapplication.notification

import android.content.Context
import com.aarav.chatapplication.data.model.GroupMessage
import com.aarav.chatapplication.data.model.Message
import com.aarav.chatapplication.data.remote.FirebasePaths
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseDatabase: FirebaseDatabase,
    private val auth: FirebaseAuth
) {
    private val prefs = NotificationPrefs(context)
    private val activeListeners = mutableMapOf<String, ChildEventListener>()
    private val rootRef = firebaseDatabase.reference

    private var currentUserId: String? = null

    fun startListening(userId: String) {
        if (currentUserId == userId) return
        stopListening()
        currentUserId = userId

        rootRef.child(FirebasePaths.userChats(userId)).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach { chatSnapshot ->
                    val chatId = chatSnapshot.key ?: return@forEach
                    setupChatListener(chatId)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        rootRef.child(FirebasePaths.userGroups(userId)).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach { groupSnapshot ->
                    val groupId = groupSnapshot.key ?: return@forEach
                    setupGroupListener(groupId)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun setupChatListener(chatId: String) {
        if (activeListeners.containsKey(chatId)) return

        val messageRef = rootRef.child(FirebasePaths.messages(chatId))
        val lastSeen = prefs.getLastSeenTimestamp(chatId)
        
        var isInitialLoad = true

        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val message = snapshot.getValue(Message::class.java) ?: return
                
                android.util.Log.d("NOTIFICATION_LOG", "New message added in chat $chatId: ${message.text} from ${message.senderId}. isInitialLoad: $isInitialLoad")

                if (!isInitialLoad && message.senderId != currentUserId && message.timestamp > lastSeen) {
                    android.util.Log.d("NOTIFICATION_LOG", "Triggering notification for chat $chatId")
                    fetchUserNameAndNotify(message.senderId, message.text, chatId)
                } else {
                    android.util.Log.d("NOTIFICATION_LOG", "Notification skipped. Reason: initialLoad=$isInitialLoad, isSelfMessage=${message.senderId == currentUserId}, isOld=${message.timestamp <= lastSeen}")
                }
                
                if (message.timestamp > prefs.getLastSeenTimestamp(chatId)) {
                    prefs.saveLastSeenTimestamp(chatId, message.timestamp)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }

        messageRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                android.util.Log.d("NOTIFICATION_LOG", "Initial load complete for chat $chatId")
                isInitialLoad = false
            }
            override fun onCancelled(error: DatabaseError) {
                isInitialLoad = false
            }
        })

        messageRef.addChildEventListener(listener)
        activeListeners[chatId] = listener
    }

    private fun setupGroupListener(groupId: String) {
        if (activeListeners.containsKey(groupId)) return

        val messageRef = rootRef.child(FirebasePaths.groupMessages(groupId))
        val lastSeen = prefs.getLastSeenTimestamp(groupId)
        var isInitialLoad = true

        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val message = snapshot.getValue(GroupMessage::class.java) ?: return

                android.util.Log.d("NOTIFICATION_LOG", "New message in group $groupId: ${message.text}. isInitialLoad: $isInitialLoad")

                if (!isInitialLoad && message.senderId != currentUserId && message.timestamp > lastSeen) {
                    android.util.Log.d("NOTIFICATION_LOG", "Triggering group notification for group $groupId")
                    fetchGroupNameAndNotify(groupId, message.senderName, message.text)
                } else {
                    android.util.Log.d("NOTIFICATION_LOG", "Group Notification skipped. Reason: initialLoad=$isInitialLoad, isSelfMessage=${message.senderId == currentUserId}, isOld=${message.timestamp <= lastSeen}")
                }

                if (message.timestamp > prefs.getLastSeenTimestamp(groupId)) {
                    prefs.saveLastSeenTimestamp(groupId, message.timestamp)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }

        messageRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                android.util.Log.d("NOTIFICATION_LOG", "Initial load complete for group $groupId")
                isInitialLoad = false
            }
            override fun onCancelled(error: DatabaseError) {
                isInitialLoad = false
            }
        })

        messageRef.addChildEventListener(listener)
        activeListeners[groupId] = listener
    }

    private fun fetchUserNameAndNotify(senderId: String, text: String, chatId: String) {
        rootRef.child("users").child(senderId).child("name").get().addOnSuccessListener { snapshot ->
            val senderName = snapshot.getValue(String::class.java) ?: "New Message"
            NotificationHelper.showChatNotification(
                context = context,
                senderName = senderName,
                messageText = text,
                receiverId = senderId, 
                receiverName = senderName
            )
        }
    }

    private fun fetchGroupNameAndNotify(groupId: String, senderName: String, text: String) {
        rootRef.child("groups").child(groupId).child("name").get().addOnSuccessListener { snapshot ->
            val groupName = snapshot.getValue(String::class.java) ?: "Group Message"
            NotificationHelper.showGroupNotification(
                context = context,
                groupName = groupName,
                senderName = senderName,
                messageText = text,
                groupId = groupId
            )
        }
    }

    fun stopListening() {
        activeListeners.forEach { (id, listener) ->
            if (id.contains("_")) { 
                rootRef.child(FirebasePaths.messages(id)).removeEventListener(listener)
            } else { 
                rootRef.child(FirebasePaths.groupMessages(id)).removeEventListener(listener)
            }
        }
        activeListeners.clear()
        currentUserId = null
    }
}
