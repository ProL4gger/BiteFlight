
package com.example.biteflight.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.biteflight.MainActivity
import com.example.biteflight.R
import com.example.biteflight.ui.seller.SellerDashboardActivity

object NotificationHelper {

    private const val CHANNEL_ID = "delivery_notifications"
    private const val CHANNEL_NAME = "Delivery Updates"
    private const val SELLER_CHANNEL_ID = "seller_orders"

    fun sendNotification(context: Context, title: String, message: String, orderId: String = "") {
        val intent = Intent(context, MainActivity::class.java).putExtra("targetTab", 2)
        send(context, CHANNEL_ID, CHANNEL_NAME, title, message, orderId, intent)
    }

    fun notifySellerNewOrder(context: Context, sellerId: String, orderId: String) {
        val intent = Intent(context, SellerDashboardActivity::class.java)
            .setData(Uri.fromParts("biteflight-seller-order", "$sellerId/$orderId", null))
            .putExtra("targetTab", 1)
            .putExtra(SellerDashboardActivity.EXTRA_NOTIFICATION_SELLER_ID, sellerId)
        send(
            context,
            SELLER_CHANNEL_ID,
            context.getString(R.string.seller_order_notification_channel),
            context.getString(R.string.seller_order_notification_title),
            context.getString(R.string.seller_order_notification_body, orderId.takeLast(6).uppercase()),
            orderId,
            intent
        )
    }

    private fun send(
        context: Context,
        channelId: String,
        channelName: String,
        title: String,
        message: String,
        orderId: String,
        destination: Intent
    ) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val id = if (orderId.isBlank()) System.currentTimeMillis().toInt() else orderId.hashCode()
        val openOrders = PendingIntent.getActivity(context, id,
            destination.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Uses built-in system icon safely
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openOrders)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        if (channelId == SELLER_CHANNEL_ID) {
            notificationManager.notify(destination.dataString, id, notification)
        } else {
            notificationManager.notify(id, notification)
        }
    }
}
