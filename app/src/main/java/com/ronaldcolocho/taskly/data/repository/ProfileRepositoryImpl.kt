package com.ronaldcolocho.taskly.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ronaldcolocho.taskly.data.mapper.toDomain
import com.ronaldcolocho.taskly.data.mapper.toDto
import com.ronaldcolocho.taskly.data.model.MemberSnapshotDto
import com.ronaldcolocho.taskly.data.model.UserProfileDto
import com.ronaldcolocho.taskly.domain.model.MemberSnapshot
import com.ronaldcolocho.taskly.domain.model.UserProfile
import com.ronaldcolocho.taskly.domain.repository.IProfileRepository
import com.ronaldcolocho.taskly.domain.util.normalizePhone
import com.ronaldcolocho.taskly.domain.util.phoneTakenError
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class ProfileRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : IProfileRepository {

    override fun getCurrentUserId(): String? = auth.currentUser?.uid

    override fun getProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        val listener = firestore.collection("profiles").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val profile = snapshot.toObject(UserProfileDto::class.java)?.copy(uid = snapshot.id)?.toDomain()
                    trySend(profile)
                } else {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun ensureProfile(
        uid: String,
        email: String,
        displayName: String?,
        phone: String?,
        photoURL: String
    ): Result<UserProfile> = runCatching {
        val ref = firestore.collection("profiles").document(uid)
        val snap = ref.get().await()
        val normalized = normalizePhone(phone.orEmpty())

        if (snap.exists()) {
            val data = snap.toObject(UserProfileDto::class.java) ?: UserProfileDto(uid = uid)
            val patch = mutableMapOf<String, Any>()

            if (normalized.isNotEmpty() && data.phone.isEmpty()) {
                claimPhone(normalized, uid)
                patch["phone"] = normalized
            }
            val cleanName = displayName?.trim()
            if (!cleanName.isNullOrEmpty() && data.displayName.isEmpty()) {
                patch["displayName"] = cleanName
            }

            if (patch.isNotEmpty()) ref.set(patch, com.google.firebase.firestore.SetOptions.merge()).await()

            (data.copy(
                uid = uid,
                displayName = patch["displayName"] as? String ?: data.displayName,
                phone = patch["phone"] as? String ?: data.phone
            )).toDomain()
        } else {
            val fallbackName = displayName?.trim()
                ?: email.substringBefore("@").ifEmpty { "Usuario" }
            val profile = UserProfile(
                uid = uid,
                displayName = fallbackName,
                email = email,
                emailLower = email.lowercase(),
                phone = normalized,
                photoURL = photoURL,
                createdAt = System.currentTimeMillis()
            )
            if (profile.phone.isNotEmpty()) claimPhone(profile.phone, uid)
            ref.set(profile.toDto()).await()
            profile
        }
    }

    override suspend fun updateProfile(profile: UserProfile): Result<Unit> = runCatching {
        val ref = firestore.collection("profiles").document(profile.uid)
        val snap = ref.get().await()
        val current = if (snap.exists())
            snap.toObject(UserProfileDto::class.java) ?: UserProfileDto()
        else UserProfileDto()

        val newPhone = normalizePhone(profile.phone)
        val oldPhone = current.phone

        if (newPhone != oldPhone) {
            if (newPhone.isNotEmpty()) claimPhone(newPhone, profile.uid)
            if (oldPhone.isNotEmpty()) {
                firestore.collection("phoneIndex").document(oldPhone).delete().await()
            }
        }

        val updated = profile.copy(
            phone = newPhone,
            email = profile.email.ifEmpty { current.email },
            emailLower = profile.emailLower.ifEmpty { current.emailLower },
            createdAt = if (current.createdAt != 0L) current.createdAt else profile.createdAt
        )
        ref.set(updated.toDto()).await()
        syncProfileInConversations(
            updated.uid,
            MemberSnapshot(
                displayName = updated.displayName,
                photoURL = updated.photoURL,
                phone = updated.phone
            )
        )
    }

    override suspend fun searchProfiles(term: String): Result<List<UserProfile>> = runCatching {
        val t = term.trim()
        if (t.length < 2) return@runCatching emptyList()

        val results = mutableMapOf<String, UserProfile>()
        val col = firestore.collection("profiles")
        val typed = normalizePhone(t)

        if (typed.length >= 4) {
            val query = if (typed.length >= 8) {
                col.whereEqualTo("phone", typed)
            } else {
                col.whereGreaterThanOrEqualTo("phone", typed)
                    .whereLessThanOrEqualTo("phone", typed + "\uf8ff")
            }
            query.get().await().documents.forEach { d ->
                d.toObject(UserProfileDto::class.java)?.copy(uid = d.id)?.toDomain()?.let { results[it.uid] = it }
            }
        }

        if (t.contains("@")) {
            col.whereEqualTo("emailLower", t.lowercase()).get().await().documents.forEach { d ->
                d.toObject(UserProfileDto::class.java)?.copy(uid = d.id)?.toDomain()?.let { results[it.uid] = it }
            }
        }

        results.values.toList()
    }

    override suspend fun logout(): Result<Unit> = runCatching {
        auth.signOut()
    }

    private suspend fun claimPhone(phone: String, uid: String) {
        if (phone.isEmpty()) return
        val ref = firestore.collection("phoneIndex").document(phone)
        firestore.runTransaction { tx ->
            val snap = tx.get(ref)
            if (snap.exists()) {
                val existingUid = snap.get("uid") as? String
                if (existingUid != uid) throw phoneTakenError()
            }
            tx.set(ref, mapOf("uid" to uid))
        }.await()
    }

    private suspend fun syncProfileInConversations(uid: String, snapshot: MemberSnapshot) {
        val snap = firestore.collection("conversations")
            .whereArrayContains("participantIds", uid)
            .get()
            .await()
        if (snap.isEmpty) return

        val batch = firestore.batch()
        snap.documents.forEach { doc ->
            val dto = MemberSnapshotDto(
                displayName = snapshot.displayName,
                photoURL = snapshot.photoURL,
                phone = snapshot.phone
            )
            batch.update(doc.reference, mapOf("members.$uid" to dto))
        }
        batch.commit().await()
    }
}
