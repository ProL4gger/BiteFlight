package com.example.biteflight.data.model

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val address: String = "",
    val role: String = ROLE_BUYER,
    val imageUrl: String = "",
    val busy: Boolean = false,
    val openingTime: String = "",
    val closingTime: String = "",
    val restaurantTimeZone: String = "Africa/Cairo"
) {
    companion object {
        const val ROLE_BUYER = "BUYER"
        const val ROLE_SELLER = "SELLER"
    }
}
