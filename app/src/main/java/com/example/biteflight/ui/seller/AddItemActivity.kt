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
    private var uploadedImageUrl: String = ""

    private val selectImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            uploadMealPictureToS3(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_item)

        auth = FirebaseAuth.getInstance()

        val toolbar = findViewById<MaterialToolbar>(R.id.topToolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBarInsets.top)
            insets
        }
        ViewCompat.requestApplyInsets(toolbar)

        setSupportActionBar(toolbar)
        supportActionBar?.title = "Add Item"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)

        val etMealName = findViewById<EditText>(R.id.etMealName)
        val etPrice = findViewById<EditText>(R.id.etPrice)
        val checkPizza = findViewById<CheckBox>(R.id.checkPizza)
        val checkPasta = findViewById<CheckBox>(R.id.checkPasta)
        val checkChicken = findViewById<CheckBox>(R.id.checkChicken)
        val checkMeat = findViewById<CheckBox>(R.id.checkMeat)
        val checkDessert = findViewById<CheckBox>(R.id.checkDessert)
        val checkBeverages = findViewById<CheckBox>(R.id.checkBeverages)
        val checkSandwich = findViewById<CheckBox>(R.id.checkSandwich)
        val btnUploadMealImage = findViewById<Button>(R.id.btnUploadMealImage)
        val btnSaveItem = findViewById<Button>(R.id.btnSaveItem)

        btnUploadMealImage.setOnClickListener {
            selectImageLauncher.launch("image/*")
        }

        btnSaveItem.setOnClickListener {
            val mealName = etMealName.text.toString().trim()
            val priceText = etPrice.text.toString().trim()

            val selectedTags = mutableListOf<String>()
            if (checkPizza.isChecked) selectedTags.add("Pizza")
            if (checkPasta.isChecked) selectedTags.add("Pasta")
            if (checkChicken.isChecked) selectedTags.add("Chicken")
            if (checkMeat.isChecked) selectedTags.add("Meat")
            if (checkDessert.isChecked) selectedTags.add("Dessert")
            if (checkBeverages.isChecked) selectedTags.add("Beverages")
            if (checkSandwich.isChecked) selectedTags.add("Sandwich")

            if (mealName.isEmpty()) {
                etMealName.error = "Enter meal name"
                return@setOnClickListener
            }

            if (priceText.isEmpty()) {
                etPrice.error = "Enter price"
                return@setOnClickListener
            }

            val price = priceText.toDoubleOrNull()
            if (price == null || price <= 0) {
                etPrice.error = "Enter a valid price"
                return@setOnClickListener
            }

            if (selectedTags.isEmpty()) {
                Toast.makeText(this, "Choose at least one tag", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveMealToFirebase(mealName, price, selectedTags, uploadedImageUrl)
        }
    }

    private fun uploadMealPictureToS3(uri: Uri) {
        val ivMealImage = findViewById<ImageView>(R.id.ivMealImage)
        val btnUploadMealImage = findViewById<Button>(R.id.btnUploadMealImage)

        S3Uploader.uploadImage(
            context = this,
            imageUri = uri,
            folderName = "meals"
        ) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    btnUploadMealImage.isEnabled = false
                    Toast.makeText(this, "Uploading image to AWS S3...", Toast.LENGTH_SHORT).show()
                }
                is Resource.Success -> {
                    btnUploadMealImage.isEnabled = true
                    uploadedImageUrl = resource.data
                    ivMealImage.load(uploadedImageUrl) {
                        crossfade(true)
                        placeholder(R.drawable.ic_logo)
                        error(R.drawable.ic_logo)
                    }
                    Toast.makeText(this, "Image uploaded to AWS S3 successfully!", Toast.LENGTH_SHORT).show()
                }
                is Resource.Error -> {
                    btnUploadMealImage.isEnabled = true
                    Toast.makeText(this, resource.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun saveMealToFirebase(
        mealName: String,
        price: Double,
        tags: List<String>,
        imageUrl: String
    ) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Seller is not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val sellerId = currentUser.uid
        val database = FirebaseDatabase.getInstance().getReference("menuItems")
        val itemId = database.push().key

        if (itemId == null) {
            Toast.makeText(this, "Could not create item", Toast.LENGTH_SHORT).show()
            return
        }

        val meal = Meal(
            id = itemId,
            name = mealName,
            price = price,
            tags = tags,
            sellerId = sellerId,
            imageUrl = imageUrl
        )

        database.child(itemId).setValue(meal)
            .addOnSuccessListener {
                Toast.makeText(this, "Item added successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_LONG).show()
            }
    }
}
