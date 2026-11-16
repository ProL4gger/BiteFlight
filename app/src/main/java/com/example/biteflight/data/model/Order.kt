package com.example.biteflight.data.model

data class Order(
    val id: String = "",
    val buyerId: String = "",
    val sellerId: String = "",
    val restaurantName: String = "",
    val deliveryAddress: String = "",
    val items: List<OrderItem> = emptyList(),
    val total: Double = 0.0,
    val status: String = "PLACED",
    val createdAt: Long = 0L,
    val riderId: String = "",
    val updatedAt: Long = 0L
)

fun orderStatusLabel(status: String): String = when (status.uppercase()) {
    "PLACED" -> "Finding your rider"
    "ASSIGNED" -> "Rider heading to restaurant"
    "WAITING_AT_RESTAURANT" -> "Preparing your order"
    "PICKED_UP" -> "On the way"
    "IN_AREA" -> "Rider is in your area"
    "DELIVERED", "COMPLETED" -> "Delivered"
    else -> status.replace('_', ' ')
}
