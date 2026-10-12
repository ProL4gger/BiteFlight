package com.example.biteflight.ui.seller

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.example.biteflight.R
import com.example.biteflight.data.model.Meal
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class AddItemActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_add_item)

        auth = FirebaseAuth.getInstance()

        // -------------------------
        // TOP TOOLBAR
        // -------------------------

        val toolbar =
            findViewById<MaterialToolbar>(R.id.topToolbar)

        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->

            val statusBarInsets =
                insets.getInsets(WindowInsetsCompat.Type.statusBars())

            view.updatePadding(
                top = statusBarInsets.top
            )

            insets
        }

        ViewCompat.requestApplyInsets(toolbar)

        setSupportActionBar(toolbar)

        supportActionBar?.title = "Add Item"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)


        // -------------------------
        // INPUTS
        // -------------------------

        val etMealName =
            findViewById<EditText>(R.id.etMealName)

        val etPrice =
            findViewById<EditText>(R.id.etPrice)

        val checkPizza =
            findViewById<CheckBox>(R.id.checkPizza)

        val checkPasta =
            findViewById<CheckBox>(R.id.checkPasta)

        val checkChicken =
            findViewById<CheckBox>(R.id.checkChicken)

        val checkMeat =
            findViewById<CheckBox>(R.id.checkMeat)

        val checkDessert =
            findViewById<CheckBox>(R.id.checkDessert)

        val checkBeverages =
            findViewById<CheckBox>(R.id.checkBeverages)

        val checkSandwich =
            findViewById<CheckBox>(R.id.checkSandwich)

        val btnSaveItem =
            findViewById<Button>(R.id.btnSaveItem)


        // -------------------------
        // ADD ITEM BUTTON
        // -------------------------

        btnSaveItem.setOnClickListener {

            val mealName =
                etMealName.text
                    .toString()
                    .trim()

            val priceText =
                etPrice.text
                    .toString()
                    .trim()

            val selectedTags =
                mutableListOf<String>()


            if (checkPizza.isChecked) {
                selectedTags.add("Pizza")
            }

            if (checkPasta.isChecked) {
                selectedTags.add("Pasta")
            }

            if (checkChicken.isChecked) {
                selectedTags.add("Chicken")
            }

            if (checkMeat.isChecked) {
                selectedTags.add("Meat")
            }

            if (checkDessert.isChecked) {
                selectedTags.add("Dessert")
            }

            if (checkBeverages.isChecked) {
                selectedTags.add("Beverages")
            }

            if (checkSandwich.isChecked) {
                selectedTags.add("Sandwich")
            }


            // -------------------------
            // VALIDATION
            // -------------------------

            if (mealName.isEmpty()) {

                etMealName.error = "Enter meal name"
                return@setOnClickListener
            }

            if (priceText.isEmpty()) {

                etPrice.error = "Enter price"
                return@setOnClickListener
            }

            val price =
                priceText.toDoubleOrNull()

            if (price == null || price <= 0) {

                etPrice.error = "Enter a valid price"
                return@setOnClickListener
            }

            if (selectedTags.isEmpty()) {

                Toast.makeText(
                    this,
                    "Choose at least one tag",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }


            // -------------------------
            // SAVE TO FIREBASE
            // -------------------------

            saveMealToFirebase(
                mealName,
                price,
                selectedTags
            )
        }
    }


    // -------------------------
    // TOOLBAR BACK BUTTON
    // -------------------------

    override fun onSupportNavigateUp(): Boolean {

        finish()

        return true
    }


    // -------------------------
    // SAVE ITEM TO FIREBASE
    // -------------------------

    private fun saveMealToFirebase(
        mealName: String,
        price: Double,
        tags: List<String>
    ) {

        val currentUser =
            auth.currentUser

        if (currentUser == null) {

            Toast.makeText(
                this,
                "Seller is not logged in",
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


        val itemId =
            database
                .push()
                .key


        if (itemId == null) {

            Toast.makeText(
                this,
                "Could not create item",
                Toast.LENGTH_SHORT
            ).show()

            return
        }


        val meal =
            Meal(
                id = itemId,
                name = mealName,
                price = price,
                tags = tags,
                sellerId = sellerId
            )


        database
            .child(itemId)
            .setValue(meal)
            .addOnSuccessListener {

                Toast.makeText(
                    this,
                    "Item added successfully",
                    Toast.LENGTH_SHORT
                ).show()

                finish()
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