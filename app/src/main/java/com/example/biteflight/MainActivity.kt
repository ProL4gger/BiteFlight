package com.example.biteflight
import android.widget.ImageButton
import com.example.biteflight.ui.buyer.CartActivity
import android.content.Intent
import android.os.Bundle
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.biteflight.data.model.User
import com.example.biteflight.ui.auth.LoginActivity
import com.example.biteflight.ui.buyer.RestaurantAdapter
import com.example.biteflight.ui.buyer.RestaurantMenuActivity
import com.example.biteflight.ui.profile.EditProfileActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity() {


    private val restaurantList =
        mutableListOf<User>()

    private lateinit var adapter: RestaurantAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        // -------------------------
        // SETTINGS BUTTON
        // -------------------------
        val btnCart =
            findViewById<ImageButton>(
                R.id.btnCart
            )

        btnCart.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    CartActivity::class.java
                )
            )
        }

        val btnSettings =
            findViewById<ImageButton>(R.id.btnSettings)

        btnSettings.setOnClickListener { view ->

            val popupMenu =
                PopupMenu(this, view)

            popupMenu.menu.add("My Orders")
            popupMenu.menu.add("Edit Profile")
            popupMenu.menu.add("Contact Us")
            popupMenu.menu.add("Support")
            popupMenu.menu.add("Log Out")

            popupMenu.setOnMenuItemClickListener { item ->

                when (item.title.toString()) {

                    "My Orders" -> {

                        Toast.makeText(
                            this,
                            "My Orders",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Later we will open:
                        // MyOrdersActivity
                    }

                    "Edit Profile" -> {

                        val intent =
                            Intent(
                                this,
                                EditProfileActivity::class.java
                            )

                        startActivity(intent)
                    }


                    "Contact Us" -> {

                        Toast.makeText(
                            this,
                            "Contact Us",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    "Support" -> {

                        Toast.makeText(
                            this,
                            "Support",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    "Log Out" -> {

                        FirebaseAuth.getInstance().signOut()

                        val intent =
                            Intent(
                                this,
                                LoginActivity::class.java
                            )

                        intent.flags =
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK

                        startActivity(intent)
                    }
                }

                true
            }

            popupMenu.show()
        }


        // -------------------------
        // RESTAURANT LIST
        // -------------------------

        val recyclerRestaurants =
            findViewById<RecyclerView>(
                R.id.recyclerRestaurants
            )

        adapter =
            RestaurantAdapter(
                restaurantList
            ) { restaurant ->

                val intent =
                    Intent(
                        this,
                        RestaurantMenuActivity::class.java
                    )

                intent.putExtra(
                    "sellerId",
                    restaurant.uid
                )

                intent.putExtra(
                    "restaurantName",
                    restaurant.name
                )

                startActivity(intent)
            }

        recyclerRestaurants.layoutManager =
            LinearLayoutManager(this)

        recyclerRestaurants.adapter =
            adapter

        loadRestaurants()
    }


    // -------------------------
    // LOAD SELLERS
    // -------------------------

    private fun loadRestaurants() {

        val database =
            FirebaseDatabase
                .getInstance()
                .getReference("users")

        database
            .get()
            .addOnSuccessListener { snapshot ->

                restaurantList.clear()

                for (userSnapshot in snapshot.children) {

                    val user =
                        userSnapshot.getValue(
                            User::class.java
                        )

                    if (
                        user != null &&
                        user.role.uppercase() == "SELLER"
                    ) {

                        val restaurantWithId =
                            user.copy(
                                uid =
                                    userSnapshot.key ?: ""
                            )

                        restaurantList.add(
                            restaurantWithId
                        )
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
}