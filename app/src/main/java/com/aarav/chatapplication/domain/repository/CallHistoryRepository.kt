package com.aarav.chatapplication.domain.repository

import com.aarav.chatapplication.data.model.CallHistoryModel
import kotlinx.coroutines.flow.Flow

interface CallHistoryRepository {
    suspend fun saveCallHistory(history: CallHistoryModel)
    fun fetchCallHistory(userId: String): Flow<List<CallHistoryModel>>
}