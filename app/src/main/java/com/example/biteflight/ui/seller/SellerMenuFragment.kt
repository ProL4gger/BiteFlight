package com.example.biteflight.ui.seller

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.biteflight.R
import com.example.biteflight.data.model.Meal
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class SellerMenuFragment : Fragment() {

    private val mealList = mutableListOf<Meal>()
    private lateinit var adapter: MealAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_seller_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.topToolbar)?.visibility = View.GONE

        val recyclerMenu = view.findViewById<RecyclerView>(R.id.recyclerMenu)
        recyclerMenu?.clipToPadding = false
        recyclerMenu?.setPadding(
            recyclerMenu.paddingLeft,
            recyclerMenu.paddingTop,
            recyclerMenu.paddingRight,
            (130 * resources.displayMetrics.density).toInt()
        )

        adapter = MealAdapter(
            mealList,
            onEditClick = { meal ->
                val intent = Intent(requireContext(), EditItemActivity::class.java).apply {
                    putExtra("itemId", meal.id)
                    putExtra("name", meal.name)
                    putExtra("price", meal.price)
                    putExtra("tags", ArrayList(meal.tags))
                    putExtra("imageUrl", meal.imageUrl)
                }
                startActivity(intent)
            },
            onDeleteClick = { meal ->
                showDeleteDialog(meal)
            }
        )

        recyclerMenu?.layoutManager = LinearLayoutManager(requireContext())
        recyclerMenu?.adapter = adapter

        loadMealsFromFirebase()
    }

    override fun onResume() {
        super.onResume()
        loadMealsFromFirebase()
    }

    private fun loadMealsFromFirebase() {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val sellerId = currentUser.uid
        val database = FirebaseDatabase.getInstance().getReference("menuItems")

        database.orderByChild("sellerId").equalTo(sellerId).get()
            .addOnSuccessListener { snapshot ->
                mealList.clear()
                for (itemSnapshot in snapshot.children) {
                    val meal = itemSnapshot.getValue(Meal::class.java)
                    if (meal != null) {
                        mealList.add(meal)
                    }
                }
                if (::adapter.isInitialized) {
                    adapter.notifyDataSetChanged()
                }
            }
            .addOnFailureListener { error ->
                Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showDeleteDialog(meal: Meal) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Item")
            .setMessage("Are you sure you want to delete ${meal.name}?")
            .setPositiveButton("Delete") { _, _ ->
                deleteMeal(meal)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteMeal(meal: Meal) {
        FirebaseDatabase.getInstance().getReference("menuItems")
            .child(meal.id)
            .removeValue()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Item deleted", Toast.LENGTH_SHORT).show()
                loadMealsFromFirebase()
            }
            .addOnFailureListener { error ->
                Toast.makeText(requireContext(), "Delete failed: ${error.message}", Toast.LENGTH_LONG).show()
            }
    }
}
