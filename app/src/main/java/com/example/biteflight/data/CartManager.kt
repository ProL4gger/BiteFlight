package com.example.biteflight.data

import com.example.biteflight.data.model.CartItem
import com.example.biteflight.data.model.Meal

object CartManager {

    private val cartItems = mutableListOf<CartItem>()

    var restaurantId: String? = null
        private set

    var restaurantName: String? = null
        private set

    fun getItems(): MutableList<CartItem> {
        return cartItems
    }

    fun isEmpty(): Boolean {
        return cartItems.isEmpty()
    }

    fun canAddFromRestaurant(sellerId: String): Boolean {
        return restaurantId == null || restaurantId == sellerId
    }

    fun addItem(
        meal: Meal,
        sellerId: String,
        restaurantName: String
    ) {

        if (cartItems.isEmpty()) {
            restaurantId = sellerId
            this.restaurantName = restaurantName
        }

        val existingItem =
            cartItems.find {
                it.meal.id == meal.id
            }

        if (existingItem == null) {

            cartItems.add(
                CartItem(
                    meal = meal,
                    quantity = 1
                )
            )
        }
    }

    fun increaseQuantity(position: Int) {

        if (position in cartItems.indices) {
            cartItems[position].quantity++
        }
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