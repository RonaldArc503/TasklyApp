package com.ronaldcolocho.taskly.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.ronaldcolocho.taskly.domain.model.UserProfile
import com.ronaldcolocho.taskly.domain.repository.IAuthRepository
import com.ronaldcolocho.taskly.domain.repository.IProfileRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val profileRepository: IProfileRepository
) : IAuthRepository {

    override fun observeAuthState(): Flow<Boolean> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener {
            trySend(it.currentUser != null)
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser != null)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun login(email: String, password: String): Result<UserProfile> = runCatching {
        val user = auth.signInWithEmailAndPassword(email.trim(), password).await().user
            ?: throw Exception("No se pudo iniciar sesión")
        profileRepository.ensureProfile(
            uid = user.uid,
            email = user.email ?: email,
            displayName = user.displayName,
            phone = null,
            photoURL = user.photoUrl?.toString() ?: ""
        ).getOrThrow()
    }

    override suspend fun register(
        email: String,
        password: String,
        phone: String?,
        displayName: String?
    ): Result<UserProfile> {
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: return Result.failure(Exception("No se pudo crear la cuenta"))

        val result = profileRepository.ensureProfile(
            uid = user.uid,
            email = user.email ?: email,
            displayName = displayName,
            phone = phone,
            photoURL = user.photoUrl?.toString() ?: ""
        )
        if (result.isFailure) {
            runCatching { user.delete().await() }
        }
        return result
    }

    override suspend fun loginWithGoogle(idToken: String): Result<UserProfile> = runCatching {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val user = auth.signInWithCredential(credential).await().user
            ?: throw Exception("No se pudo iniciar sesión")
        profileRepository.ensureProfile(
            uid = user.uid,
            email = user.email ?: "",
            displayName = user.displayName,
            phone = null,
            photoURL = user.photoUrl?.toString() ?: ""
        ).getOrThrow()
    }
}
