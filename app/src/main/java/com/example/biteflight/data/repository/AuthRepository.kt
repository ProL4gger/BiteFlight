
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
                    .child(firebaseUser.uid)
                    .setValue(userWithUid)
                    .addOnCompleteListener { dbTask ->
                        if (dbTask.isSuccessful) {
                            onResult(Resource.Success(firebaseUser))
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

        database.getReference("users").child(uid).get()
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    onResult(Resource.Error(
                        task.exception?.localizedMessage
                            ?: "Could not load profile"
                    ))
                    return@addOnCompleteListener
                }

                val user = task.result?.getValue(User::class.java)

                if (user != null) {
                    onResult(Resource.Success(user))
                } else {
                    val email = auth.currentUser?.email.orEmpty()
                    val fallbackUser = User(
                        uid = uid,
                        email = email,
                        name = email.substringBefore("@"),
                        role = User.ROLE_BUYER
                    )

                    database.getReference("users")
                        .child(uid)
                        .setValue(fallbackUser)
                        .addOnCompleteListener { saveTask ->
                            if (saveTask.isSuccessful) {
                                onResult(Resource.Success(fallbackUser))
                            } else {
                                onResult(Resource.Error(
                                    "Could not create user profile"
                                ))
                            }
                        }
                }
            }
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

        database.getReference("users")
            .child(user.uid)
            .updateChildren(updates)
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
