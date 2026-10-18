package com.example.biteflight.utils

import com.example.biteflight.data.model.User
import com.google.firebase.database.FirebaseDatabase

object RestaurantGate {
    fun check(sellerId: String, result: (String?) -> Unit) {
        // Fail closed when availability cannot be confirmed.
        FirebaseDatabase.getInstance().getReference("users").child(sellerId).get()
            .addOnSuccessListener { snapshot ->
                val seller = snapshot.getValue(User::class.java)
                result(when {
                    seller == null -> "Restaurant is unavailable"
                    RestaurantAvailability.status(seller) == RestaurantAvailability.Status.BUSY ->
                        "This restaurant is busy. Please try again later."
                    RestaurantAvailability.status(seller) == RestaurantAvailability.Status.CLOSED ->
                        "This restaurant is closed. ${RestaurantAvailability.hours(seller)}"
                    else -> null
                })
            }
            .addOnFailureListener {
                result("Could not check restaurant availability. Please try again.")
            }
    }
}
