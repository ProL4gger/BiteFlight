package com.example.biteflight.data.model

data class Meal(
    val id: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val tags: List<String> = emptyList(),
    val sellerId: String = "",
    val imageUrl: String = ""
)
