package com.example.biteflight.data.model

data class LocationPoint(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = ""
)

object DeliveryStatus {
    const val PENDING_ASSIGNMENT = "PENDING_ASSIGNMENT"
    const val ACCEPTED = "ACCEPTED"
    const val PICKED_UP = "PICKED_UP"
    const val DELIVERED = "DELIVERED"
    const val CANCELLED = "CANCELLED"
}

data class Delivery(
    val deliveryId: String = "",
    val orderId: String = "",
    val driverId: String? = null,
    val restaurantLocation: LocationPoint = LocationPoint(),
    val buyerLocation: LocationPoint = LocationPoint(),
    val distanceKm: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val status: String = DeliveryStatus.PENDING_ASSIGNMENT,
    val updatedAt: Long = 0L
)