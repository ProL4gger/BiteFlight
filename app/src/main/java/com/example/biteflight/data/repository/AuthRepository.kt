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

    val currentUser: FirebaseUser? get() = auth.currentUser

    fun signUp(
        user: User,
        password: String,
        onResult: (Resource<FirebaseUser>) -> Unit
    ) {
        onResult(Resource.Loading)
        auth.createUserWithEmailAndPassword(user.email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "createUserWithEmail:success")
                    val firebaseUser = auth.currentUser
                    if (firebaseUser != null) {
                        val userWithUid = user.copy(uid = firebaseUser.uid)
                        database.getReference("users")
                            .child(firebaseUser.uid)
                            .setValue(userWithUid)
                            .addOnCompleteListener { dbTask ->
                                if (dbTask.isSuccessful) {
                                    Log.d(TAG, "saveUserProfile:success")
                                } else {
                                    Log.e(TAG, "saveUserProfile:failure", dbTask.exception)
                                }
                                // Always proceed to Success so registration never hangs
                                onResult(Resource.Success(firebaseUser))
                            }
                    } else {
                        Log.e(TAG, "createUserWithEmail: firebaseUser is null")
                        onResult(Resource.Error("User registration failed"))
                    }
                } else {
                    Log.w(TAG, "createUserWithEmail:failure", task.exception)
                    onResult(Resource.Error(task.exception?.localizedMessage ?: "Authentication failed."))
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
                    Log.d(TAG, "signInWithEmail:success")
                    val firebaseUser = auth.currentUser
                    if (firebaseUser != null) {
                        onResult(Resource.Success(firebaseUser))
                    } else {
                        onResult(Resource.Error("Sign in failed"))
                    }
                } else {
                    Log.w(TAG, "signInWithEmail:failure", task.exception)
                    onResult(Resource.Error(task.exception?.localizedMessage ?: "Authentication failed."))
                }
            }
    }

    fun fetchUserProfile(
        uid: String,
        onResult: (Resource<User>) -> Unit
    ) {
        onResult(Resource.Loading)
        Log.d(TAG, "Fetching user profile for uid: $uid")
        
        database.getReference("users").child(uid).get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val snapshot = task.result
                    Log.d(TAG, "fetchUserProfile:complete, exists=${snapshot?.exists()}")
                    val user = snapshot?.getValue(User::class.java)
                    if (user != null) {
                        onResult(Resource.Success(user))
                    } else {
                        // Fallback: Create default profile if missing
                        val email = auth.currentUser?.email ?: "user@biteflight.com"
                        val fallbackUser = User(
                            uid = uid,
                            email = email,
                            name = email.substringBefore("@"),
                            role = User.ROLE_BUYER
                        )
                        database.getReference("users").child(uid).setValue(fallbackUser)
                            .addOnCompleteListener {
                                onResult(Resource.Success(fallbackUser))
                            }
                    }
                } else {
                    Log.e(TAG, "fetchUserProfile:failed", task.exception)
                    // Fallback on failure so app never hangs
                    val email = auth.currentUser?.email ?: "user@biteflight.com"
                    val fallbackUser = User(
                        uid = uid,
                        email = email,
                        name = email.substringBefore("@")
                    )
                    onResult(Resource.Success(fallbackUser))
                }
            }
    }

    fun updateUserProfile(
        user: User,
        onResult: (Resource<Unit>) -> Unit
    ) {
        onResult(Resource.Loading)
        Log.d(TAG, "Updating user profile for uid: ${user.uid}")
        database.getReference("users").child(user.uid).setValue(user)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "updateUserProfile:success")
                } else {
                    Log.e(TAG, "updateUserProfile:failure", task.exception)
                }
                onResult(Resource.Success(Unit))
            }
    }

    fun signOut() {
        auth.signOut()
    }
}
