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
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.database.FirebaseDatabase

class EditItemActivity : AppCompatActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_edit_item
        )


        // -------------------------
        // GET ORIGINAL DATA
        // -------------------------

        val itemId =
            intent.getStringExtra("itemId")

        val originalName =
            intent.getStringExtra("name")
                ?: ""

        val originalPrice =
            intent.getDoubleExtra(
                "price",
                0.0
            )

        val originalTags =
            intent.getStringArrayListExtra(
                "tags"
            ) ?: arrayListOf()


        if (itemId.isNullOrEmpty()) {

            Toast.makeText(
                this,
                "Item not found",
                Toast.LENGTH_SHORT
            ).show()

            finish()
            return
        }


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
            "Edit Item"

        supportActionBar
            ?.setDisplayHomeAsUpEnabled(true)


        // -------------------------
        // INPUTS
        // -------------------------

        val etMealName =
            findViewById<EditText>(
                R.id.etMealName
            )

        val etPrice =
            findViewById<EditText>(
                R.id.etPrice
            )

        val checkPizza =
            findViewById<CheckBox>(
                R.id.checkPizza
            )

        val checkPasta =
            findViewById<CheckBox>(
                R.id.checkPasta
            )

        val checkChicken =
            findViewById<CheckBox>(
                R.id.checkChicken
            )

        val checkMeat =
            findViewById<CheckBox>(
                R.id.checkMeat
            )

        val checkDessert =
            findViewById<CheckBox>(
                R.id.checkDessert
            )

        val checkBeverages =
            findViewById<CheckBox>(
                R.id.checkBeverages
            )

        val checkSandwich =
            findViewById<CheckBox>(
                R.id.checkSandwich
            )

        val btnSaveChanges =
            findViewById<Button>(
                R.id.btnSaveChanges
            )


        // -------------------------
        // SHOW ORIGINAL DATA
        // -------------------------

        etMealName.setText(
            originalName
        )

        etPrice.setText(
            originalPrice.toString()
        )

        checkPizza.isChecked =
            originalTags.contains("Pizza")

        checkPasta.isChecked =
            originalTags.contains("Pasta")

        checkChicken.isChecked =
            originalTags.contains("Chicken")

        checkMeat.isChecked =
            originalTags.contains("Meat")

        checkDessert.isChecked =
            originalTags.contains("Dessert")

        checkBeverages.isChecked =
            originalTags.contains("Beverages")

        checkSandwich.isChecked =
            originalTags.contains("Sandwich")


        // -------------------------
        // SAVE CHANGES
        // -------------------------

        btnSaveChanges.setOnClickListener {

            val newName =
                etMealName
                    .text
                    .toString()
                    .trim()

            val priceText =
                etPrice
                    .text
                    .toString()
                    .trim()

            val newPrice =
                priceText.toDoubleOrNull()

            val newTags =
                mutableListOf<String>()


            if (checkPizza.isChecked) {
                newTags.add("Pizza")
            }

            if (checkPasta.isChecked) {
                newTags.add("Pasta")
            }

            if (checkChicken.isChecked) {
                newTags.add("Chicken")
            }

            if (checkMeat.isChecked) {
                newTags.add("Meat")
            }

            if (checkDessert.isChecked) {
                newTags.add("Dessert")
            }

            if (checkBeverages.isChecked) {
                newTags.add("Beverages")
            }

            if (checkSandwich.isChecked) {
                newTags.add("Sandwich")
            }


            if (newName.isEmpty()) {

                etMealName.error =
                    "Enter meal name"

                return@setOnClickListener
            }


            if (
                newPrice == null ||
                newPrice <= 0
            ) {

                etPrice.error =
                    "Enter valid price"

                return@setOnClickListener
            }


            if (newTags.isEmpty()) {

                Toast.makeText(
                    this,
                    "Choose at least one tag",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }


            updateMeal(
                itemId,
                newName,
                newPrice,
                newTags
            )
        }
    }


    override fun onSupportNavigateUp(): Boolean {

        finish()

        return true
    }


    private fun updateMeal(
        itemId: String,
        name: String,
        price: Double,
        tags: List<String>
    ) {

        val updates =
            mapOf<String, Any>(
                "name" to name,
                "price" to price,
                "tags" to tags
            )


        FirebaseDatabase
            .getInstance()
            .getReference("menuItems")
            .child(itemId)
            .updateChildren(updates)

            .addOnSuccessListener {

                Toast.makeText(
                    this,
                    "Item updated successfully",
                    Toast.LENGTH_SHORT
                ).show()

                finish()
            }

            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "Update failed: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }
}