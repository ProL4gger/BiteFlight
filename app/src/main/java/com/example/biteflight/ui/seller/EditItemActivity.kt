package com.example.biteflight.ui.seller

import android.content.Intent
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

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

        val toolbar =
            findViewById<MaterialToolbar>(R.id.topToolbar)

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

        val etMealName =
            findViewById<EditText>(R.id.etMealName)

        val etPrice =
            findViewById<EditText>(R.id.etPrice)

        // NEW: Stock input
        val etStockQuantity =
            findViewById<EditText>(R.id.etStockQuantity)

        val etCustomTags =
            findViewById<EditText>(R.id.etCustomTags)

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

            val knownNames = predefinedTags.map { it.second }

            val customTags = originalTags.filter { tag ->
                knownNames.none {
                    it.equals(tag, ignoreCase = true)
                }
            }

            etCustomTags.setText(customTags.joinToString(", "))
        }

        // NEW: Load current stock from Firebase.
        // Disable saving until the quantity has been loaded.
        val itemReference = FirebaseDatabase.getInstance()
            .getReference("menuItems")
            .child(itemId)

        btnSaveChanges.isEnabled = false

        itemReference.addListenerForSingleValueEvent(
            object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    if (isFinishing || isDestroyed) return

                    if (!snapshot.exists()) {
                        Toast.makeText(
                            this@EditItemActivity,
                            "Item no longer exists",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                        return
                    }

                    val currentSellerId =
                        FirebaseAuth.getInstance().currentUser?.uid

                    val itemSellerId =
                        snapshot.child("sellerId")
                            .getValue(String::class.java)

                    if (currentSellerId == null ||
                        currentSellerId != itemSellerId
                    ) {
                        Toast.makeText(
                            this@EditItemActivity,
                            "You cannot edit this item",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                        return
                    }

                    // Do not overwrite a restored form after rotation.
                    if (savedInstanceState == null) {
                        val stock =
                            snapshot.child("stockQuantity")
                                .getValue(Int::class.java)

                        // Existing items may not have stockQuantity.
                        // Leave blank until the seller assigns stock.
                        etStockQuantity.setText(
                            stock?.toString().orEmpty()
                        )
                    }

                    btnSaveChanges.isEnabled = true
                }

                override fun onCancelled(error: DatabaseError) {
                    if (isFinishing || isDestroyed) return

                    Toast.makeText(
                        this@EditItemActivity,
                        "Could not load stock: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )

        btnSaveChanges.setOnClickListener {

            val newName =
                etMealName.text.toString().trim()

            val newPrice =
                etPrice.text.toString().trim().toDoubleOrNull()

            // NEW: Read stock quantity
            val newStockQuantity =
                etStockQuantity.text.toString()
                    .trim()
                    .toIntOrNull()

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

            // NEW: Validate stock quantity
            if (newStockQuantity == null || newStockQuantity < 0) {
                etStockQuantity.error =
                    "Enter a valid stock quantity"
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
                stockQuantity = newStockQuantity,
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
        stockQuantity: Int,
        tags: List<String>
    ) {
        val updates = mutableMapOf<String, Any>(
            "name" to name,
            "price" to price,
            "stockQuantity" to stockQuantity,
            "tags" to tags
        )

        replacementImageUrl?.let {
            updates["imageUrl"] = it
        }

        val btnSave =
            findViewById<Button>(R.id.btnSaveChanges)

        val btnUpload =
            findViewById<Button>(R.id.btnUploadMealImage)

        btnSave.isEnabled = false
        btnUpload.isEnabled = false

        val currentUid =
            FirebaseAuth.getInstance().currentUser?.uid

        if (currentUid == null) {
            btnSave.isEnabled = true
            btnUpload.isEnabled = true

            Toast.makeText(
                this,
                "Seller is not logged in",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // The UI checks ownership, but Firebase rules
        // must enforce it for real security.
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
                val intent = Intent(this, SellerDashboardActivity::class.java).apply {
                    putExtra("targetTab", 0) // Menu tab is at index 0 [Menu, Orders, Home, Availability, Profile]
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
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
        val upload =
            findViewById<Button>(R.id.btnUploadMealImage)

        val save =
            findViewById<Button>(R.id.btnSaveChanges)

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
        outState.putString(
            "replacementImageUrl",
            replacementImageUrl
        )
        super.onSaveInstanceState(outState)
    }
}
