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
    val restaurantTimeZone: String = "Africa/Cairo",
    val deliveryAddressDetails: DeliveryAddress = DeliveryAddress(),
    val lat: Double = 0.0,   // restaurant location (sellers)
    val lng: Double = 0.0
) {
    companion object {
        const val ROLE_BUYER = "BUYER"
        const val ROLE_SELLER = "SELLER"
        const val ROLE_DELIVERY = "DELIVERY" // Added for delivery driver role
    }
}