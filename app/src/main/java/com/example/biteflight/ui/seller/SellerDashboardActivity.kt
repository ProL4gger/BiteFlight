package com.example.biteflight.ui.seller
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.biteflight.R
import com.example.biteflight.ui.auth.LoginActivity
import com.google.firebase.auth.FirebaseAuth

class SellerDashboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_seller_dashboard)

        // Seller bottom navigation
        SellerNavigation.attach(this, R.id.sellerHome)

        val btnAddItem =
            findViewById<android.view.View>(R.id.btnAddItem)

        val btnMenu =
            findViewById<android.view.View>(R.id.btnMenu)

        val btnEdit =
            findViewById<ImageButton>(R.id.btnEdit)

        // Dashboard Orders shortcut
        findViewById<android.view.View>(R.id.btnOrders)
            .setOnClickListener {
                startActivity(
                    Intent(
                        this,
                        SellerOrdersActivity::class.java
                    )
                )
            }

        // Notifications
        findViewById<ImageButton>(R.id.btnNotifications)
            .setOnClickListener {
                startActivity(
                    Intent(
                        this,
                        SellerOrdersActivity::class.java
                    )
                )
            }

        // Add Item
        btnAddItem.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    AddItemActivity::class.java
                )
            )
        }

        // Restaurant Menu
        btnMenu.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    SellerMenuActivity::class.java
                )
            )
        }

        // Top-right Action Menu
        btnEdit.setOnClickListener { view ->

            val popupMenu = PopupMenu(this, view)

            // Only these options remain
            popupMenu.menu.add("About Us")
            popupMenu.menu.add("Support")
            popupMenu.menu.add("Log Out")

            popupMenu.setOnMenuItemClickListener { item ->

                when (item.title.toString()) {

                    "About Us" -> {
                        Toast.makeText(
                            this,
                            "About Us",
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
