package com.example.biteflight.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.biteflight.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.security.MessageDigest

object BuyerPushRegistration {
    fun register() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { save(it) }
            .addOnFailureListener { Log.w("BuyerPush", "Could not fetch notification token", it) }
    }

    fun save(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val database = FirebaseDatabase.getInstance()
        database.getReference("users/buyers").child(uid).child("role").get()
            .addOnSuccessListener { profile ->
                if (FirebaseAuth.getInstance().currentUser?.uid != uid ||
                    profile.getValue(String::class.java)?.uppercase() != "BUYER") return@addOnSuccessListener
                val key = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
                    .joinToString("") { "%02x".format(it) }
                database.getReference("buyerPushTokens").child(uid).child(key).setValue(token)
                    .addOnFailureListener { Log.w("BuyerPush", "Could not register notification token", it) }
            }.addOnFailureListener { Log.w("BuyerPush", "Could not verify buyer profile", it) }
    }
}

class BuyerMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) { BuyerPushRegistration.save(token) }

    override fun onMessageReceived(message: RemoteMessage) {
        // Data-only pushes let us discard messages for a previously signed-in account.
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (message.data["buyerId"] != uid) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val orderId = message.data["orderId"] ?: return
        val status = message.data["status"] ?: return
        val stages = listOf("ASSIGNED", "WAITING_AT_RESTAURANT", "PICKED_UP", "IN_AREA", "DELIVERED")
        val phase = stages.indexOf(status)
        if (phase < 0) return
        val history = getSharedPreferences("delivery_notification_stages", MODE_PRIVATE)
        val key = "$uid:$orderId"
        if (phase < history.getInt(key, -1)) return
        history.edit().putInt(key, phase).apply()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(
            "delivery_notifications", "Delivery updates", NotificationManager.IMPORTANCE_HIGH))
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("targetTab", 2)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val id = "$uid:$orderId:$status".hashCode()
        val pending = PendingIntent.getActivity(this, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(id, NotificationCompat.Builder(this, "delivery_notifications")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(message.data["title"] ?: "Order update")
            .setContentText(message.data["body"] ?: "Your delivery has an update")
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.data["body"]))
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH).build())
    }
}
