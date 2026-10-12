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
    val createdAt: Long = 0L
)