
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

                val profilePath = "users/${DatabasePaths.groupForRole(user.role)}/${firebaseUser.uid}"
                val registration = mutableMapOf<String, Any>(profilePath to userWithUid)
                if (user.role.equals(User.ROLE_SELLER, true)) {
                    registration["restaurants/${firebaseUser.uid}"] =
                        RestaurantProfiles.publicFields(userWithUid)
                }
                database.reference.updateChildren(registration)
                    .addOnCompleteListener { dbTask ->
                        if (dbTask.isSuccessful) {
                            onResult(Resource.Success(firebaseUser))
                        } else {
                            Log.e(TAG, "Profile save failed", dbTask.exception)
                            // Auth and Realtime Database are separate services. Undo
                            // this new account if its required profile was rejected.
                            firebaseUser.delete().addOnCompleteListener { deleteTask ->
                                auth.signOut()
                                val reason = dbTask.exception?.localizedMessage
                                    ?: "Could not save profile"
                                if (deleteTask.isSuccessful) {
                                    onResult(Resource.Error(
                                        "Registration was not completed: $reason. " +
                                            "Please try again after database access is fixed."
                                    ))
                                } else {
                                    Log.e(TAG, "Account rollback failed", deleteTask.exception)
                                    onResult(Resource.Error(
                                        "Your sign-in account was created, but its profile " +
                                            "could not be saved: $reason. Please contact support " +
                                            "to restore your profile."
                                    ))
                                }
                            }
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
                        // Do not enter the app until the database profile exists.
                        fetchUserProfile(firebaseUser.uid) { profileResult ->
                            when (profileResult) {
                                is Resource.Loading -> Unit
                                is Resource.Success -> onResult(Resource.Success(firebaseUser))
                                is Resource.Error -> {
                                    auth.signOut()
                                    onResult(Resource.Error(profileResult.message))
                                }
                            }
                        }
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

        if (auth.currentUser?.uid != uid) {
            onResult(Resource.Error("Please sign in to load your profile"))
            return
        }
        // Read only this account's record in each group. Never guess a
        // missing seller/rider profile's role or silently turn it into a buyer.
        val groups = listOf("buyers", "sellers", "riders")
        fun findProfile(index: Int) {
            if (index == groups.size) {
                onResult(Resource.Error(
                    "Your account has no database profile. Restore or migrate its " +
                        "profile into users/buyers, users/sellers or users/riders."
                ))
                return
            }
            database.getReference("users").child(groups[index]).child(uid).get()
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        onResult(Resource.Error(task.exception?.localizedMessage
                            ?: "Could not load profile"))
                    } else if (!task.result.exists()) {
                        findProfile(index + 1)
                    } else {
                        try {
                            val profile = task.result.getValue(User::class.java)
                                ?: throw IllegalStateException("Invalid profile")
                            if (DatabasePaths.groupForRole(profile.role) != groups[index]) {
                                throw IllegalStateException("Profile role does not match its account group")
                            }
                            onResult(Resource.Success(profile.copy(uid = uid)))
                        } catch (error: Exception) {
                            onResult(Resource.Error(error.localizedMessage ?: "Invalid profile"))
                        }
                    }
                }
        }
        findProfile(0)
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
        if (user.role.equals(User.ROLE_SELLER, true)) {
            updates["lat"] = user.lat
            updates["lng"] = user.lng
        }

        val profilePath = "users/${DatabasePaths.groupForRole(user.role)}/${user.uid}"
        val changes = mutableMapOf<String, Any>()
        updates.forEach { (key, value) -> changes["$profilePath/$key"] = value }
        if (user.role.equals(User.ROLE_SELLER, true)) {
            listOf("name", "address", "imageUrl", "lat", "lng").forEach { key ->
                changes["restaurants/${user.uid}/$key"] = updates.getValue(key)
            }
        }
        database.reference.updateChildren(changes)
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
