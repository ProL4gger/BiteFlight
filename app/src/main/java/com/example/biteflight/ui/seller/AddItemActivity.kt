
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
import com.example.biteflight.data.model.Meal
import com.example.biteflight.utils.Resource
import com.example.biteflight.utils.S3Uploader
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class AddItemActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var uploadedImageUrl = ""

    private val selectImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) uploadMealPictureToS3(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_item)

        auth = FirebaseAuth.getInstance()
        uploadedImageUrl =
            savedInstanceState?.getString("uploadedImageUrl").orEmpty()

        val image = findViewById<ImageView>(R.id.ivMealImage)

        if (uploadedImageUrl.isNotBlank()) {
            image.load(uploadedImageUrl)
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
        supportActionBar?.title = "Add Item"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val etMealName = findViewById<EditText>(R.id.etMealName)
        val etPrice = findViewById<EditText>(R.id.etPrice)
        val etCustomTags = findViewById<EditText>(R.id.etCustomTags)

        val btnUploadMealImage =
            findViewById<Button>(R.id.btnUploadMealImage)
        val btnSaveItem =
            findViewById<Button>(R.id.btnSaveItem)

        btnUploadMealImage.setOnClickListener {
            selectImageLauncher.launch("image/*")
        }

        btnSaveItem.setOnClickListener {
            val mealName = etMealName.text.toString().trim()
            val priceText = etPrice.text.toString().trim()
            val price = priceText.toDoubleOrNull()

            val selectedTags = collectTags()

            val customTags = etCustomTags.text.toString()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }

            selectedTags.addAll(customTags)

            val uniqueTags = selectedTags.distinctBy {
                it.lowercase()
            }

            if (mealName.isBlank()) {
                etMealName.error = "Enter meal name"
                return@setOnClickListener
            }

            if (price == null || !price.isFinite() || price <= 0) {
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

            saveMealToFirebase(
                mealName = mealName,
                price = price,
                tags = uniqueTags,
                imageUrl = uploadedImageUrl
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

    private fun uploadMealPictureToS3(uri: Uri) {
        val ivMealImage = findViewById<ImageView>(R.id.ivMealImage)
        val btnUpload = findViewById<Button>(R.id.btnUploadMealImage)
        val btnSave = findViewById<Button>(R.id.btnSaveItem)

        btnSave.isEnabled = false
        btnUpload.isEnabled = false

        S3Uploader.uploadImage(
            context = this,
            imageUri = uri,
            folderName = "meals"
        ) { resource ->

            if (isFinishing || isDestroyed) return@uploadImage

            when (resource) {
                is Resource.Loading -> {
                    btnUpload.text = "Uploading image..."
                }

                is Resource.Success -> {
                    uploadedImageUrl = resource.data

                    ivMealImage.load(uploadedImageUrl) {
                        crossfade(true)
                        placeholder(R.drawable.ic_logo)
                        error(R.drawable.ic_logo)
                    }

                    btnSave.isEnabled = true
                    btnUpload.isEnabled = true
                    btnUpload.text = "Replace Item Picture"

                    Toast.makeText(
                        this,
                        "Image uploaded successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                is Resource.Error -> {
                    btnSave.isEnabled = true
                    btnUpload.isEnabled = true
                    btnUpload.text = "Upload Item Picture"

                    Toast.makeText(
                        this,
                        resource.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun saveMealToFirebase(
        mealName: String,
        price: Double,
        tags: List<String>,
        imageUrl: String
    ) {
        val currentUser = auth.currentUser

        if (currentUser == null) {
            Toast.makeText(
                this,
                "Seller is not logged in",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val database = FirebaseDatabase.getInstance()
            .getReference("menuItems")

        val itemId = database.push().key

        if (itemId == null) {
            Toast.makeText(
                this,
                "Could not create item",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val meal = Meal(
            id = itemId,
            name = mealName,
            price = price,
            tags = tags,
            sellerId = currentUser.uid,
            imageUrl = imageUrl
        )

        val btnSave = findViewById<Button>(R.id.btnSaveItem)
        val btnUpload = findViewById<Button>(R.id.btnUploadMealImage)

        btnSave.isEnabled = false
        btnUpload.isEnabled = false

        database.child(itemId).setValue(meal)
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    "Item added successfully",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
            .addOnFailureListener { error ->
                btnSave.isEnabled = true
                btnUpload.isEnabled = true

                Toast.makeText(
                    this,
                    "Error: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("uploadedImageUrl", uploadedImageUrl)
        super.onSaveInstanceState(outState)
    }
}
