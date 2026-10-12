package com.example.biteflight.data.model

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val address: String = "",
    val role: String = ROLE_BUYER
) {
    companion object {
        const val ROLE_BUYER = "BUYER"
        const val ROLE_SELLER = "SELLER"
    }
}
