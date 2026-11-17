
package com.example.biteflight.data.repository

import android.util.Log
import com.example.biteflight.data.model.User
import com.example.biteflight.utils.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    companion object {
        private const val TAG = "AuthRepository"
    }

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun signUp(
        user: User,
        password: String,
        onResult: (Resource<FirebaseUser>) -> Unit
    ) {
        onResult(Resource.Loading)

        auth.createUserWithEmailAndPassword(user.email, password)
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    onResult(Resource.Error(
                        task.exception?.localizedMessage
                            ?: "Authentication failed."
                    ))
                    return@addOnCompleteListener
                }

                val firebaseUser = auth.currentUser
                if (firebaseUser == null) {
                    onResult(Resource.Error("Registration failed"))
                    return@addOnCompleteListener
                }

                val userWithUid = user.copy(uid = firebaseUser.uid)

                database.getReference("users")
                    .child(if (user.role.equals(User.ROLE_SELLER, true)) "sellers" else "buyers")
                    .child(firebaseUser.uid)
                    .setValue(userWithUid)
                    .addOnCompleteListener { dbTask ->
                        if (dbTask.isSuccessful) {
                            if (user.role.equals(User.ROLE_SELLER, true)) {
                                RestaurantProfiles.ensurePublished(firebaseUser.uid)
                                    .addOnSuccessListener { onResult(Resource.Success(firebaseUser)) }
                                    .addOnFailureListener { onResult(Resource.Error("Could not publish restaurant. Sign in to retry.")) }
                            } else onResult(Resource.Success(firebaseUser))
                        } else {
                            Log.e(TAG, "Profile save failed", dbTask.exception)
                            onResult(Resource.Error(
                                dbTask.exception?.localizedMessage
                                    ?: "Could not save profile"
                            ))
                        }
                    }
            }
    }

    fun signIn(
        email: String,
        password: String,
        onResult: (Resource<FirebaseUser>) -> Unit
    ) {
        onResult(Resource.Loading)

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val firebaseUser = auth.currentUser
                    if (firebaseUser != null) {
                        onResult(Resource.Success(firebaseUser))
                    } else {
                        onResult(Resource.Error("Sign in failed"))
                    }
                } else {
                    onResult(Resource.Error(
                        task.exception?.localizedMessage
                            ?: "Authentication failed."
                    ))
                }
            }
    }

    fun fetchUserProfile(
        uid: String,
        onResult: (Resource<User>) -> Unit
    ) {
        onResult(Resource.Loading)

        findProfile(uid) { user, error ->
            if (user != null) onResult(Resource.Success(user))
            else onResult(Resource.Error(error ?: "Profile not found. Please contact support."))
        }
    }

    private fun findProfile(uid: String, result: (User?, String?) -> Unit) {
        val buckets = listOf("buyers", "sellers", "riders")
        fun read(index: Int) {
            if (index == buckets.size) {
                result(null, null)
                return
            }
            database.getReference("users").child(buckets[index]).child(uid).get()
                .addOnSuccessListener { snapshot ->
                    val user = snapshot.getValue(User::class.java)
                    if (user != null) result(user.copy(uid = uid), null) else read(index + 1)
                }
                .addOnFailureListener { result(null, it.localizedMessage) }
        }
        read(0)
    }

    fun updateUserProfile(
        user: User,
        onResult: (Resource<Unit>) -> Unit
    ) {
        onResult(Resource.Loading)

        val updates = mutableMapOf<String, Any>(
            "name" to user.name,
            "phone" to user.phone,
            "address" to user.address,
            "imageUrl" to user.imageUrl
        )

        if (user.role.equals(User.ROLE_BUYER, true)) {
            updates["deliveryAddressDetails"] =
                user.deliveryAddressDetails
        }

        val saveTask = if (user.role.equals(User.ROLE_SELLER, true)) {
            RestaurantProfiles.updateSeller(user.uid, updates, mapOf(
                "name" to user.name, "address" to user.address, "imageUrl" to user.imageUrl
            ))
        } else {
            database.getReference("users").child("buyers").child(user.uid).updateChildren(updates)
        }
        saveTask
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(Resource.Success(Unit))
                } else {
                    onResult(Resource.Error(
                        task.exception?.localizedMessage
                            ?: "Profile could not be saved"
                    ))
                }
            }
    }

    fun signOut() {
        auth.signOut()
    }
}
