package com.example.biteflight.data.model

import com.google.firebase.database.Exclude
import com.google.firebase.database.DataSnapshot

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
) {
    // Legacy total stays readable, but screens never trust a buyer-supplied aggregate.
    @get:Exclude
    val calculatedTotal: Double
        get() = items.sumOf { java.math.BigDecimal.valueOf(it.price)
            .multiply(java.math.BigDecimal.valueOf(it.quantity.toLong())) }
            .setScale(2, java.math.RoundingMode.HALF_UP).toDouble()

    companion object {
        // Iterate children rather than relying on Firebase's dense-array heuristic.
        // This supports old orders and valid numeric item keys with gaps.
        fun fromSnapshot(snapshot: DataSnapshot): Order = Order(
            id = snapshot.key.orEmpty(),
            buyerId = snapshot.child("buyerId").getValue(String::class.java).orEmpty(),
            sellerId = snapshot.child("sellerId").getValue(String::class.java).orEmpty(),
            restaurantName = snapshot.child("restaurantName").getValue(String::class.java).orEmpty(),
            deliveryAddress = snapshot.child("deliveryAddress").getValue(String::class.java).orEmpty(),
            items = snapshot.child("items").children.mapNotNull { it.getValue(OrderItem::class.java) },
            status = snapshot.child("status").getValue(String::class.java) ?: "PLACED",
            createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L
        )
    }
}
