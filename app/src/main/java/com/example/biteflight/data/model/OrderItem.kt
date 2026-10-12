package com.example.biteflight.data.model

data class OrderItem(
    val mealId: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val quantity: Int = 0
)