package com.example.biteflight.data.repository

import com.example.biteflight.data.model.User
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.database.*

/** Public restaurant fields only. Private contact/delivery details never go here. */
object RestaurantProfiles {
    fun publicFields(user: User): Map<String, Any> = mapOf(
        "uid" to user.uid, "role" to User.ROLE_SELLER,
        "name" to user.name, "address" to user.address, "imageUrl" to user.imageUrl,
        "busy" to user.busy, "openingTime" to user.openingTime,
        "closingTime" to user.closingTime, "restaurantTimeZone" to user.restaurantTimeZone
    )

    fun ensurePublished(uid: String): Task<Void> = FirebaseDatabase.getInstance()
        .getReference("users").child(uid).get().continueWithTask { task ->
            val user = task.result.getValue(User::class.java)
                ?: throw IllegalStateException("Profile not found")
            if (user.role != User.ROLE_SELLER) throw IllegalStateException("Seller account required")
            val completion = TaskCompletionSource<Void>()
            FirebaseDatabase.getInstance().getReference("restaurants").child(uid)
                .runTransaction(object : Transaction.Handler {
                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                        // Never overwrite newer public data with an older profile snapshot.
                        if (currentData.value != null) return Transaction.abort()
                        currentData.value = publicFields(user.copy(uid = uid))
                        return Transaction.success(currentData)
                    }
                    override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                        if (error != null) completion.setException(error.toException())
                        else completion.setResult(null)
                    }
                })
            completion.task
        }

    fun updateSeller(uid: String, privateFields: Map<String, Any>, publicChanges: Map<String, Any>): Task<Void> =
        ensurePublished(uid).continueWithTask { task ->
            if (!task.isSuccessful) throw task.exception ?: IllegalStateException("Could not publish restaurant")
            val updates = mutableMapOf<String, Any>()
            privateFields.forEach { (key, value) -> updates["users/$uid/$key"] = value }
            publicChanges.forEach { (key, value) -> updates["restaurants/$uid/$key"] = value }
            FirebaseDatabase.getInstance().reference.updateChildren(updates)
        }
}
