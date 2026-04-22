package com.aarav.chatapplication.di

import com.aarav.chatapplication.data.repository.AuthRepositoryImpl
import com.aarav.chatapplication.data.repository.ChatListRepositoryImpl
import com.aarav.chatapplication.data.repository.GroupChatRepositoryImpl
import com.aarav.chatapplication.data.repository.GroupRepositoryImpl
import com.aarav.chatapplication.data.repository.MessageRepositoryImpl
import com.aarav.chatapplication.data.repository.PresenceRepositoryImpl
import com.aarav.chatapplication.data.repository.TypingRepositoryImpl
import com.aarav.chatapplication.data.repository.UserRepositoryImpl
import com.aarav.chatapplication.domain.repository.AuthRepository
import com.aarav.chatapplication.domain.repository.ChatListRepository
import com.aarav.chatapplication.domain.repository.GroupChatRepository
import com.aarav.chatapplication.domain.repository.GroupRepository
import com.aarav.chatapplication.domain.repository.MessageRepository
import com.aarav.chatapplication.domain.repository.PresenceRepository
import com.aarav.chatapplication.domain.repository.TypingRepository
import com.aarav.chatapplication.domain.repository.UserRepository
import com.aarav.chatapplication.data.repository.CallHistoryRepositoryImpl
import com.aarav.chatapplication.domain.repository.CallHistoryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    abstract fun bindUserRepository(
        userRepositoryImpl: UserRepositoryImpl
    ): UserRepository

    @Binds
    abstract fun bindMessageRepository(
        messageRepositoryImpl: MessageRepositoryImpl
    ): MessageRepository

    @Binds
    abstract fun bindTypingRepository(
        typingRepositoryImpl: TypingRepositoryImpl
    ): TypingRepository

    @Binds
    abstract fun bindPresenceRepository(
        presenceRepositoryImpl: PresenceRepositoryImpl
    ): PresenceRepository

    @Binds
    abstract fun bindChatListRepository(
        chatListRepositoryImpl: ChatListRepositoryImpl
    ): ChatListRepository

    @Binds
    abstract fun bindGroupRepository(
        groupRepositoryImpl: GroupRepositoryImpl
    ): GroupRepository

    @Binds
    abstract fun bindGroupChatRepository(
        groupChatRepositoryImpl: GroupChatRepositoryImpl
    ): GroupChatRepository

    @Binds
    abstract fun bindCallHistoryRepository(
        callHistoryRepositoryImpl: CallHistoryRepositoryImpl
    ): CallHistoryRepository
}