package com.example.biteflight.data.model

data class CartItem(
    val meal: Meal,
    var quantity: Int = 1
)