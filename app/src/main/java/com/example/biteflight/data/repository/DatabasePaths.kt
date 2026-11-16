package com.example.biteflight.data.repository

import com.example.biteflight.data.model.User
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

object DatabasePaths {
    fun groupForRole(role: String): String = when (role.uppercase()) {
        User.ROLE_BUYER -> "buyers"
        User.ROLE_SELLER -> "sellers"
        "RIDER", "PENDING_RIDER" -> "riders"
        else -> throw IllegalArgumentException("Unsupported account role: $role")
    }

    fun profile(database: FirebaseDatabase, uid: String, role: String): DatabaseReference =
        database.getReference("users").child(groupForRole(role)).child(uid)
}
