package com.example.biteflight.data

import com.example.biteflight.data.model.CartItem
import com.example.biteflight.data.model.Meal

object CartManager {

    private val cartItems = mutableListOf<CartItem>()

    var restaurantId: String? = null
        private set

    var restaurantName: String? = null
        private set

    enum class AddResult {
        SUCCESS,
        OUT_OF_STOCK,
        NOT_ENOUGH_STOCK,
        DIFFERENT_RESTAURANT
    }

    fun getItems(): MutableList<CartItem> {
        return cartItems
    }

    fun isEmpty(): Boolean {
        return cartItems.isEmpty()
    }

    fun canAddFromRestaurant(sellerId: String): Boolean {
        return restaurantId == null || restaurantId == sellerId
    }

    fun getQuantityForMeal(mealId: String): Int {
        return cartItems.find {
            it.meal.id == mealId
        }?.quantity ?: 0
    }

    // Add one item, respecting the latest stock quantity.
    fun addItem(
        meal: Meal,
        sellerId: String,
        restaurantName: String
    ): AddResult {

        if (!canAddFromRestaurant(sellerId)) {
            return AddResult.DIFFERENT_RESTAURANT
        }

        if (meal.stockQuantity <= 0) {
            return AddResult.OUT_OF_STOCK
        }

        val existingItem = cartItems.find {
            it.meal.id == meal.id
        }

        if (existingItem != null) {

            if (existingItem.quantity >= meal.stockQuantity) {
                return AddResult.NOT_ENOUGH_STOCK
            }

            existingItem.quantity++
        } else {

            cartItems.add(
                CartItem(
                    meal = meal,
                    quantity = 1
                )
            )
        }

        if (cartItems.size == 1) {
            restaurantId = sellerId
            this.restaurantName = restaurantName
        }

        return AddResult.SUCCESS
    }

    // Increase quantity using stock information already in cart.
    // Firebase should be checked before calling this function.
    fun increaseQuantity(position: Int): AddResult {

        if (position !in cartItems.indices) {
            return AddResult.NOT_ENOUGH_STOCK
        }

        val item = cartItems[position]

        if (item.meal.stockQuantity <= 0) {
            return AddResult.OUT_OF_STOCK
        }

        if (item.quantity >= item.meal.stockQuantity) {
            return AddResult.NOT_ENOUGH_STOCK
        }

        item.quantity++

        return AddResult.SUCCESS
    }

    // Use this after checking the latest stock in Firebase.
    fun increaseQuantity(
        position: Int,
        latestStock: Int
    ): AddResult {

        if (position !in cartItems.indices) {
            return AddResult.NOT_ENOUGH_STOCK
        }

        val item = cartItems[position]

        if (latestStock <= 0) {
            return AddResult.OUT_OF_STOCK
        }

        if (item.quantity >= latestStock) {
            return AddResult.NOT_ENOUGH_STOCK
        }

        item.quantity++

        return AddResult.SUCCESS
    }

    fun decreaseQuantity(position: Int) {

        if (position !in cartItems.indices) {
            return
        }

        if (cartItems[position].quantity > 1) {
            cartItems[position].quantity--
        } else {
            cartItems.removeAt(position)

            if (cartItems.isEmpty()) {
                clearCart()
            }
        }
    }

    fun removeItem(position: Int) {

        if (position in cartItems.indices) {
            cartItems.removeAt(position)
        }

        if (cartItems.isEmpty()) {
            clearCart()
        }
    }

    fun getTotal(): Double {
        return cartItems.sumOf {
            it.meal.price * it.quantity
        }
    }

    fun clearCart() {
        cartItems.clear()
        restaurantId = null
        restaurantName = null
    }
}
