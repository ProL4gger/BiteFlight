package com.example.biteflight.data.repository

import com.example.biteflight.data.model.Delivery
import com.example.biteflight.data.model.DeliveryStatus
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class DeliveryRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun getPendingDeliveries(): Result<List<Delivery>> {
        return try {
            val snapshot = firestore.collection("deliveries")
                .whereEqualTo("status", DeliveryStatus.PENDING_ASSIGNMENT)
                .get()
                .await()

            val list = snapshot.toObjects(Delivery::class.java)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acceptDelivery(deliveryId: String, driverId: String): Result<Unit> {
        return try {
            firestore.collection("deliveries").document(deliveryId)
                .update(
                    mapOf(
                        "driverId" to driverId,
                        "status" to DeliveryStatus.ACCEPTED,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getActiveDeliveryForDriver(driverId: String): Result<List<Delivery>> {
        return try {
            val snapshot = firestore.collection("deliveries")
                .whereEqualTo("driverId", driverId)
                .whereIn("status", listOf(DeliveryStatus.ACCEPTED, DeliveryStatus.PICKED_UP))
                .get()
                .await()

            val list = snapshot.toObjects(Delivery::class.java)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}