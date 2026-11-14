package com.example.biteflight.ui.delivery

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.biteflight.R
import com.example.biteflight.data.model.Delivery
import com.example.biteflight.data.model.DeliveryStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DeliveryHomeFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var firestore: FirebaseFirestore
    private val deliveryList = mutableListOf<Delivery>()
    private lateinit var adapter: DeliveryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_delivery_home, container, false)

        recyclerView = view.findViewById(R.id.recyclerViewDeliveries)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)
        firestore = FirebaseFirestore.getInstance()

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = DeliveryAdapter(deliveryList) { delivery ->
            acceptDelivery(delivery)
        }
        recyclerView.adapter = adapter

        fetchAvailableDeliveries()

        return view
    }

    private fun fetchAvailableDeliveries() {
        firestore.collection("deliveries")
            .whereEqualTo("status", DeliveryStatus.PENDING_ASSIGNMENT)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    return@addSnapshotListener
                }

                deliveryList.clear()
                if (snapshot != null) {
                    for (doc in snapshot.documents) {
                        val delivery = doc.toObject(Delivery::class.java)
                        if (delivery != null) {
                            deliveryList.add(delivery)
                        }
                    }
                }

                adapter.notifyDataSetChanged()

                if (deliveryList.isEmpty()) {
                    tvEmptyState.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    tvEmptyState.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
            }
    }

    private fun acceptDelivery(delivery: Delivery) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        firestore.collection("deliveries").document(delivery.deliveryId)
            .update(
                mapOf(
                    "driverId" to currentUserId,
                    "status" to DeliveryStatus.ACCEPTED,
                    "updatedAt" to System.currentTimeMillis()
                )
            )
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Delivery accepted successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to accept: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}