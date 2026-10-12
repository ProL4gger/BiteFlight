package com.example.biteflight.ui.seller

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.biteflight.R
import com.example.biteflight.ui.auth.LoginActivity
import com.google.firebase.auth.FirebaseAuth

class SellerDashboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_seller_dashboard)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnAddItem = findViewById<TextView>(R.id.btnAddItem)
        val btnMenu = findViewById<TextView>(R.id.btnMenu)
        val btnEdit = findViewById<ImageButton>(R.id.btnEdit)


        // -------------------------
        // BACK BUTTON
        // -------------------------

        btnBack.setOnClickListener {
            finish()
        }


        // -------------------------
        // ADD ITEM
        // -------------------------

        btnAddItem.setOnClickListener {

            val intent = Intent(
                this,
                AddItemActivity::class.java
            )

            startActivity(intent)
        }


        // -------------------------
        // MENU
        // -------------------------

        btnMenu.setOnClickListener {

            val intent = Intent(
                this,
                SellerMenuActivity::class.java
            )

            startActivity(intent)
        }


        // -------------------------
        // TOP RIGHT POPUP MENU
        // -------------------------

        btnEdit.setOnClickListener { view ->

            val popupMenu = PopupMenu(this, view)

            popupMenu.menu.add("My Orders")
            popupMenu.menu.add("About Us")
            popupMenu.menu.add("Support")
            popupMenu.menu.add("Log Out")


            popupMenu.setOnMenuItemClickListener { item ->

                when (item.title.toString()) {

                    "My Orders" -> {

                        startActivity(
                            Intent(
                                this,
                                SellerOrdersActivity::class.java
                            )
                        )
                    }


                    "About Us" -> {

                        Toast.makeText(
                            this,
                            "About Us",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Later:
                        // startActivity(
                        //     Intent(
                        //         this,
                        //         AboutUsActivity::class.java
                        //     )
                        // )
                    }


                    "Support" -> {

                        Toast.makeText(
                            this,
                            "Support",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Later:
                        // startActivity(
                        //     Intent(
                        //         this,
                        //         SupportActivity::class.java
                        //     )
                        // )
                    }


                    "Log Out" -> {

                        FirebaseAuth
                            .getInstance()
                            .signOut()

                        val intent = Intent(
                            this,
                            LoginActivity::class.java
                        )

                        intent.flags =
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK

                        startActivity(intent)

                        finish()
                    }
                }

                true
            }

            popupMenu.show()
        }
    }
}