package com.example.biteflight.ui.seller

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.biteflight.R
import com.example.biteflight.data.model.Meal
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class SellerMenuActivity : AppCompatActivity() {

    private val mealList =
        mutableListOf<Meal>()

    private lateinit var adapter: MealAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_seller_menu)
        SellerNavigation.attach(this, R.id.sellerMenu)

        // -------------------------
        // TOOLBAR
        // -------------------------

        val toolbar =
            findViewById<MaterialToolbar>(
                R.id.topToolbar
            )

        ViewCompat.setOnApplyWindowInsetsListener(
            toolbar
        ) { view, insets ->

            val statusBarInsets =
                insets.getInsets(
                    WindowInsetsCompat.Type.statusBars()
                )

            view.updatePadding(
                top = statusBarInsets.top
            )

            insets
        }

        ViewCompat.requestApplyInsets(toolbar)

        setSupportActionBar(toolbar)

        supportActionBar?.title =
            "My Menu"

        supportActionBar
            ?.setDisplayHomeAsUpEnabled(true)


        // -------------------------
        // RECYCLER VIEW
        // -------------------------

        val recyclerMenu =
            findViewById<RecyclerView>(
                R.id.recyclerMenu
            )

        adapter =
            MealAdapter(
                mealList,

                onEditClick = { meal ->

                    val intent =
                        Intent(
                            this,
                            EditItemActivity::class.java
                        )

                    intent.putExtra(
                        "itemId",
                        meal.id
                    )

                    intent.putExtra(
                        "name",
                        meal.name
                    )

                    intent.putExtra(
                        "price",
                        meal.price
                    )

                    intent.putExtra(
                        "tags",
                        ArrayList(meal.tags)
                    )

                    intent.putExtra("imageUrl", meal.imageUrl)
                    startActivity(intent)
                },

                onDeleteClick = { meal ->

                    showDeleteDialog(meal)
                }
            )

        recyclerMenu.layoutManager =
            LinearLayoutManager(this)

        recyclerMenu.adapter =
            adapter

        loadMealsFromFirebase()
    }


    override fun onResume() {
        super.onResume()

        loadMealsFromFirebase()
    }


    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }


    private fun loadMealsFromFirebase() {

        val currentUser =
            FirebaseAuth
                .getInstance()
                .currentUser

        if (currentUser == null) {

            Toast.makeText(
                this,
                "Seller not logged in",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val sellerId =
            currentUser.uid

        val database =
            FirebaseDatabase
                .getInstance()
                .getReference("menuItems")

        database
            .orderByChild("sellerId")
            .equalTo(sellerId)
            .get()
            .addOnSuccessListener { snapshot ->

                mealList.clear()

                for (
                itemSnapshot
                in snapshot.children
                ) {

                    val meal =
                        itemSnapshot.getValue(
                            Meal::class.java
                        )

                    if (meal != null) {
                        mealList.add(meal)
                    }
                }

                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "Error: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }


    private fun showDeleteDialog(
        meal: Meal
    ) {

        AlertDialog.Builder(this)
            .setTitle("Delete Item")
            .setMessage(
                "Are you sure you want to delete ${meal.name}?"
            )
            .setPositiveButton("Delete") { _, _ ->

                deleteMeal(meal)
            }
            .setNegativeButton(
                "Cancel",
                null
            )
            .show()
    }


    private fun deleteMeal(
        meal: Meal
    ) {

        FirebaseDatabase
            .getInstance()
            .getReference("menuItems")
            .child(meal.id)
            .removeValue()
            .addOnSuccessListener {

                Toast.makeText(
                    this,
                    "Item deleted",
                    Toast.LENGTH_SHORT
                ).show()

                loadMealsFromFirebase()
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "Delete failed: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }
}
