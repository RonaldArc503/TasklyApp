package com.ronaldcolocho.taskly.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.ronaldcolocho.taskly.data.mapper.toDto
import com.ronaldcolocho.taskly.domain.model.SavedItem
import com.ronaldcolocho.taskly.domain.repository.ISavedRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class SavedRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : ISavedRepository {

    override suspend fun saveMessage(uid: String, item: SavedItem): Result<Unit> = runCatching {
        firestore.collection("users").document(uid).collection("saved")
            .document(item.id)
            .set(item.toDto())
            .await()
    }
}
