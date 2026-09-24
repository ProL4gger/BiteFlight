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
                            .addOnSuccessListener {
                                onResult(Resource.Success(firebaseUser))
                            }
                            .addOnFailureListener { exception ->
                                onResult(Resource.Error(exception.localizedMessage ?: "Failed to save user profile"))
                            }
                    } else {
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
        database.getReference("users").child(uid).get()
            .addOnSuccessListener { snapshot ->
                val user = snapshot.getValue(User::class.java)
                if (user != null) {
                    onResult(Resource.Success(user))
                } else {
                    onResult(Resource.Error("Profile not found"))
                }
            }
            .addOnFailureListener { exception ->
                onResult(Resource.Error(exception.localizedMessage ?: "Failed to fetch profile"))
            }
    }

    fun signOut() {
        auth.signOut()
    }
}
