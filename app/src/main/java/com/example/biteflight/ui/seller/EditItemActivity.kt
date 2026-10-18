package com.example.biteflight.ui.seller

import android.os.Bundle
import android.net.Uri
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import coil.load
import com.example.biteflight.utils.S3Uploader
import com.example.biteflight.utils.Resource
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
    private var replacementImageUrl: String? = null
    private val selectImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) uploadItemPhoto(uri)
    }

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

        replacementImageUrl = savedInstanceState?.getString("replacementImageUrl")
        findViewById<ImageView>(R.id.ivMealImage).load(
            (replacementImageUrl ?: intent.getStringExtra("imageUrl")).orEmpty().ifBlank { null }
        ) {
            placeholder(R.drawable.ic_logo)
            fallback(R.drawable.ic_logo)
            error(R.drawable.ic_logo)
        }
        findViewById<Button>(R.id.btnUploadMealImage).setOnClickListener {
            selectImageLauncher.launch("image/*")
        }


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
            mutableMapOf<String, Any>(
                "name" to name,
                "price" to price,
                "tags" to tags
            )
        replacementImageUrl?.let { updates["imageUrl"] = it }
        findViewById<Button>(R.id.btnSaveChanges).isEnabled = false
        findViewById<Button>(R.id.btnUploadMealImage).isEnabled = false


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
                findViewById<Button>(R.id.btnSaveChanges).isEnabled = true
                findViewById<Button>(R.id.btnUploadMealImage).isEnabled = true

                Toast.makeText(
                    this,
                    "Update failed: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun uploadItemPhoto(uri: Uri) {
        val upload = findViewById<Button>(R.id.btnUploadMealImage)
        val save = findViewById<Button>(R.id.btnSaveChanges)
        upload.isEnabled = false
        save.isEnabled = false
        S3Uploader.uploadImage(context = this, imageUri = uri, folderName = "meals") { result ->
            if (isFinishing || isDestroyed) return@uploadImage
            when (result) {
                is Resource.Loading -> upload.text = "Uploading photo…"
                is Resource.Success -> {
                    replacementImageUrl = result.data
                    findViewById<ImageView>(R.id.ivMealImage).load(result.data) {
                        placeholder(R.drawable.ic_logo)
                        error(R.drawable.ic_logo)
                    }
                    upload.text = "Replace item photo"
                    upload.isEnabled = true
                    save.isEnabled = true
                }
                is Resource.Error -> {
                    upload.text = "Upload or replace item photo"
                    upload.isEnabled = true
                    save.isEnabled = true
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("replacementImageUrl", replacementImageUrl)
        super.onSaveInstanceState(outState)
    }
}
