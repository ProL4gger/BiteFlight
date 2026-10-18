
package com.example.biteflight.ui.seller

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import coil.load
import com.example.biteflight.R
import com.example.biteflight.utils.Resource
import com.example.biteflight.utils.S3Uploader
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.database.FirebaseDatabase

class EditItemActivity : AppCompatActivity() {

    private var replacementImageUrl: String? = null

    private val selectImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) uploadItemPhoto(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_item)

        val itemId = intent.getStringExtra("itemId")
        val originalName = intent.getStringExtra("name").orEmpty()
        val originalPrice = intent.getDoubleExtra("price", 0.0)
        val originalTags =
            intent.getStringArrayListExtra("tags") ?: arrayListOf()

        if (itemId.isNullOrEmpty()) {
            Toast.makeText(
                this,
                "Item not found",
                Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.topToolbar)

        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val statusBarInsets = insets.getInsets(
                WindowInsetsCompat.Type.statusBars()
            )
            view.updatePadding(top = statusBarInsets.top)
            insets
        }

        ViewCompat.requestApplyInsets(toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Edit Item"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val etMealName = findViewById<EditText>(R.id.etMealName)
        val etPrice = findViewById<EditText>(R.id.etPrice)
        val etCustomTags = findViewById<EditText>(R.id.etCustomTags)

        val btnSaveChanges =
            findViewById<Button>(R.id.btnSaveChanges)
        val btnUploadMealImage =
            findViewById<Button>(R.id.btnUploadMealImage)

        replacementImageUrl =
            savedInstanceState?.getString("replacementImageUrl")

        val currentImageUrl =
            replacementImageUrl ?: intent.getStringExtra("imageUrl")

        findViewById<ImageView>(R.id.ivMealImage).load(
            currentImageUrl.orEmpty().ifBlank { null }
        ) {
            placeholder(R.drawable.ic_logo)
            fallback(R.drawable.ic_logo)
            error(R.drawable.ic_logo)
        }

        btnUploadMealImage.setOnClickListener {
            selectImageLauncher.launch("image/*")
        }

        // Load original item data
        if (savedInstanceState == null) {
            etMealName.setText(originalName)
            etPrice.setText(originalPrice.toString())

            val predefinedTags = listOf(
                R.id.checkPizza to "Pizza",
                R.id.checkPasta to "Pasta",
                R.id.checkChicken to "Chicken",
                R.id.checkMeat to "Meat",
                R.id.checkDessert to "Dessert",
                R.id.checkBeverages to "Beverages",
                R.id.checkSandwich to "Sandwich"
            )

            for ((checkboxId, tagName) in predefinedTags) {
                findViewById<CheckBox>(checkboxId).isChecked =
                    originalTags.any {
                        it.equals(tagName, ignoreCase = true)
                    }
            }

            // Restore custom tags from Firebase item
            val knownNames = predefinedTags.map { it.second }

            val customTags = originalTags.filter { tag ->
                knownNames.none {
                    it.equals(tag, ignoreCase = true)
                }
            }

            etCustomTags.setText(customTags.joinToString(", "))
        }

        btnSaveChanges.setOnClickListener {
            val newName = etMealName.text.toString().trim()
            val newPrice = etPrice.text.toString().trim().toDoubleOrNull()

            val newTags = collectTags()

            val customTags = etCustomTags.text.toString()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }

            newTags.addAll(customTags)

            val uniqueTags = newTags.distinctBy {
                it.lowercase()
            }

            if (newName.isBlank()) {
                etMealName.error = "Enter meal name"
                return@setOnClickListener
            }

            if (newPrice == null ||
                !newPrice.isFinite() ||
                newPrice <= 0
            ) {
                etPrice.error = "Enter a valid price"
                return@setOnClickListener
            }

            if (uniqueTags.isEmpty()) {
                Toast.makeText(
                    this,
                    "Select or add at least one tag",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            updateMeal(
                itemId = itemId,
                name = newName,
                price = newPrice,
                tags = uniqueTags
            )
        }
    }

    private fun collectTags(): MutableList<String> {
        val tags = mutableListOf<String>()

        val predefinedTags = listOf(
            R.id.checkPizza to "Pizza",
            R.id.checkPasta to "Pasta",
            R.id.checkChicken to "Chicken",
            R.id.checkMeat to "Meat",
            R.id.checkDessert to "Dessert",
            R.id.checkBeverages to "Beverages",
            R.id.checkSandwich to "Sandwich"
        )

        for ((checkboxId, tagName) in predefinedTags) {
            if (findViewById<CheckBox>(checkboxId).isChecked) {
                tags.add(tagName)
            }
        }

        return tags
    }

    private fun updateMeal(
        itemId: String,
        name: String,
        price: Double,
        tags: List<String>
    ) {
        val updates = mutableMapOf<String, Any>(
            "name" to name,
            "price" to price,
            "tags" to tags
        )

        replacementImageUrl?.let {
            updates["imageUrl"] = it
        }

        val btnSave = findViewById<Button>(R.id.btnSaveChanges)
        val btnUpload = findViewById<Button>(R.id.btnUploadMealImage)

        btnSave.isEnabled = false
        btnUpload.isEnabled = false

        FirebaseDatabase.getInstance()
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
                btnSave.isEnabled = true
                btnUpload.isEnabled = true

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

        S3Uploader.uploadImage(
            context = this,
            imageUri = uri,
            folderName = "meals"
        ) { result ->
            if (isFinishing || isDestroyed) return@uploadImage

            when (result) {
                is Resource.Loading -> {
                    upload.text = "Uploading photo..."
                }

                is Resource.Success -> {
                    replacementImageUrl = result.data

                    findViewById<ImageView>(R.id.ivMealImage)
                        .load(result.data) {
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

                    Toast.makeText(
                        this,
                        result.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("replacementImageUrl", replacementImageUrl)
        super.onSaveInstanceState(outState)
    }
}
