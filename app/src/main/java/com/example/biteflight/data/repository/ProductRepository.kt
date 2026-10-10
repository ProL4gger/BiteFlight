package com.example.biteflight.data.repository

import com.example.biteflight.data.model.Product
import com.google.firebase.database.FirebaseDatabase

class ProductRepository(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    /** Reads the "products" node. Falls back to sample data until real products exist. */
    fun fetchAll(onResult: (List<Product>) -> Unit) {
        database.getReference("products").get().addOnCompleteListener { task ->
            val list = if (task.isSuccessful) {
                task.result.children.mapNotNull { it.getValue(Product::class.java) }
            } else {
                emptyList()
            }
            onResult(list.ifEmpty { sampleProducts() })
        }
    }

    // TEMPORARY: delete this once sellers can add real products (Milestone 3).
    private fun sampleProducts() = listOf(
        Product("1", "Fresh Apples (1 kg)", 45.0, 30, "Green Market"),
        Product("2", "Bananas (1 kg)", 30.0, 40, "Green Market"),
        Product("3", "Whole Wheat Bread", 12.0, 25, "Daily Bakery"),
        Product("4", "Greek Yogurt (500 g)", 38.0, 20, "Dairy Corner"),
        Product("5", "Chicken Breast (1 kg)", 160.0, 15, "Fresh Meats"),
        Product("6", "Brown Rice (1 kg)", 55.0, 35, "Daily Pantry"),
        Product("7", "Spinach Bunch", 10.0, 22, "Green Market"),
        Product("8", "Eggs (12 pack)", 70.0, 50, "Dairy Corner"),
        Product("9", "Olive Oil (500 ml)", 190.0, 12, "Daily Pantry"),
        Product("10", "Oats (500 g)", 35.0, 28, "Daily Pantry")
    )
}