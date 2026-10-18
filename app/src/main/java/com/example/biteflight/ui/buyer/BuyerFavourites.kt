package com.example.biteflight.ui.buyer

import android.content.Context
import com.google.firebase.auth.FirebaseAuth

object BuyerFavourites {
    private fun preferences(context: Context) = context.getSharedPreferences(
        "favourites_" + (FirebaseAuth.getInstance().currentUser?.uid ?: "guest"), Context.MODE_PRIVATE)
    fun ids(context: Context): Set<String> = preferences(context).getStringSet("restaurants", emptySet())!!.toSet()
    fun toggle(context: Context, id: String) {
        val saved = ids(context).toMutableSet()
        if (!saved.add(id)) saved.remove(id)
        preferences(context).edit().putStringSet("restaurants", saved).apply()
    }
}
