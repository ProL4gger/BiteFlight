package com.example.biteflight.ui.profile
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import coil.load
import coil.transform.CircleCropTransformation
import com.example.biteflight.R
import com.example.biteflight.data.model.DeliveryAddress
import com.example.biteflight.data.model.User
import com.example.biteflight.databinding.ActivityEditProfileBinding
import com.example.biteflight.ui.auth.AuthViewModel
import com.example.biteflight.ui.buyer.BuyerNavigation
import com.example.biteflight.ui.seller.SellerNavigation
import com.example.biteflight.utils.Resource
import com.example.biteflight.utils.S3Uploader
import com.google.android.material.appbar.MaterialToolbar

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding

    private val viewModel: AuthViewModel by viewModels()

    private var currentUserProfile: User? = null

    // Prevent multiple navigation bars
    private var navigationAttached = false

    private var uploadedImageUrl = ""
    private var previousImageUrl = ""

    private val selectImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            uploadProfilePictureToS3(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar =
            findViewById<MaterialToolbar>(R.id.topToolbar)

        setSupportActionBar(toolbar)
        supportActionBar?.title = "Edit Profile"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)

        binding.btnUploadImage.setOnClickListener {
            selectImageLauncher.launch("image/*")
        }

        binding.btnSaveProfile.setOnClickListener {
            saveProfile()
        }

        observeViewModel()

        val user = viewModel.currentUser

        if (user == null) {
            Toast.makeText(
                this,
                "Not signed in",
                Toast.LENGTH_SHORT
            ).show()
            finish()
        } else {
            viewModel.fetchProfile(user.uid)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // ==========================================
    // READ BUYER DELIVERY ADDRESS
    // ==========================================

    private fun readDeliveryAddress(): DeliveryAddress {

        return DeliveryAddress(
            city = findViewById<EditText>(R.id.etCity)
                .text.toString().trim(),

            area = findViewById<EditText>(R.id.etArea)
                .text.toString().trim(),

            street = findViewById<EditText>(R.id.etStreet)
                .text.toString().trim(),

            building = findViewById<EditText>(R.id.etBuilding)
                .text.toString().trim(),

            floor = findViewById<EditText>(R.id.etFloor)
                .text.toString().trim(),

            apartment = findViewById<EditText>(R.id.etApartment)
                .text.toString().trim(),

            landmark = findViewById<EditText>(R.id.etLandmark)
                .text.toString().trim(),

            instructions = findViewById<EditText>(R.id.etInstructions)
                .text.toString().trim()
        )
    }

    // ==========================================
    // FILL BUYER DELIVERY ADDRESS
    // ==========================================

    private fun fillDeliveryAddress(details: DeliveryAddress) {

        findViewById<EditText>(R.id.etCity)
            .setText(details.city)

        findViewById<EditText>(R.id.etArea)
            .setText(details.area)

        findViewById<EditText>(R.id.etStreet)
            .setText(details.street)

        findViewById<EditText>(R.id.etBuilding)
            .setText(details.building)

        findViewById<EditText>(R.id.etFloor)
            .setText(details.floor)

        findViewById<EditText>(R.id.etApartment)
            .setText(details.apartment)

        findViewById<EditText>(R.id.etLandmark)
            .setText(details.landmark)

        findViewById<EditText>(R.id.etInstructions)
            .setText(details.instructions)
    }

    // ==========================================
    // SAVE PROFILE
    // ==========================================

    private fun saveProfile() {

        val profile = currentUserProfile ?: return

        val name =
            binding.etEditName.text.toString().trim()

        val phone =
            binding.etEditPhone.text.toString().trim()

        val isBuyer =
            profile.role.equals(User.ROLE_BUYER, true)

        if (name.isBlank() || phone.isBlank()) {
            Toast.makeText(
                this,
                "Name and phone are required",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val details = if (isBuyer) {
            readDeliveryAddress()
        } else {
            profile.deliveryAddressDetails
        }

        if (isBuyer && !details.isValid()) {
            Toast.makeText(
                this,
                "City, area, street and building are required",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val address = if (isBuyer) {
            details.formatted()
        } else {
            binding.etEditAddress.text.toString().trim()
        }

        if (address.isBlank()) {
            Toast.makeText(
                this,
                "Address is required",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val updatedUser = profile.copy(
            name = name,
            phone = phone,
            address = address,
            deliveryAddressDetails = details,
            imageUrl = uploadedImageUrl.ifBlank {
                profile.imageUrl
            }
        )

        viewModel.updateProfile(updatedUser)
    }

    // ==========================================
    // UPLOAD PROFILE PICTURE TO AWS S3
    // ==========================================

    private fun uploadProfilePictureToS3(uri: Uri) {

        S3Uploader.uploadImage(
            context = this,
            imageUri = uri,
            folderName = "profiles"
        ) { resource ->

            if (isFinishing || isDestroyed) {
                return@uploadImage
            }

            when (resource) {

                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnUploadImage.isEnabled = false
                }

                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnUploadImage.isEnabled = true

                    uploadedImageUrl = resource.data

                    binding.ivProfilePicture.load(uploadedImageUrl) {
                        crossfade(true)
                        placeholder(R.drawable.ic_person)
                        error(R.drawable.ic_person)
                        transformations(CircleCropTransformation())
                    }

                    Toast.makeText(
                        this,
                        "Image uploaded successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnUploadImage.isEnabled = true

                    Toast.makeText(
                        this,
                        resource.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // ==========================================
    // OBSERVE FIREBASE PROFILE
    // ==========================================

    private fun observeViewModel() {

        viewModel.userProfileState.observe(this) { resource ->

            when (resource) {

                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                }

                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE

                    val profile = resource.data

                    currentUserProfile = profile
                    previousImageUrl = profile.imageUrl

                    val isBuyer = profile.role.equals(
                        User.ROLE_BUYER,
                        true
                    )

                    val isSeller = profile.role.equals(
                        User.ROLE_SELLER,
                        true
                    )

                    // =====================================
                    // ATTACH CORRECT NAVIGATION BAR
                    // =====================================

                    if (!navigationAttached) {

                        if (isBuyer) {
                            BuyerNavigation.attach(
                                this,
                                R.id.buyerProfile
                            )
                            navigationAttached = true

                        } else if (isSeller) {
                            SellerNavigation.attach(
                                this,
                                R.id.sellerProfile
                            )
                            navigationAttached = true
                        }
                    }

                    // =====================================
                    // SHOW CORRECT ADDRESS FIELDS
                    // =====================================

                    binding.tilEditAddress.visibility =
                        if (isBuyer) View.GONE else View.VISIBLE

                    findViewById<View>(R.id.buyerAddressForm).visibility =
                        if (isBuyer) View.VISIBLE else View.GONE

                    // =====================================
                    // LOAD USER INFORMATION
                    // =====================================

                    binding.etEditName.setText(profile.name)
                    binding.etEditPhone.setText(profile.phone)
                    binding.etEditAddress.setText(profile.address)

                    if (isBuyer) {
                        fillDeliveryAddress(
                            profile.deliveryAddressDetails
                        )
                    }

                    // =====================================
                    // LOAD PROFILE PICTURE
                    // =====================================

                    if (profile.imageUrl.isNotBlank()) {

                        binding.ivProfilePicture.load(profile.imageUrl) {
                            crossfade(true)
                            placeholder(R.drawable.ic_person)
                            error(R.drawable.ic_person)
                            transformations(CircleCropTransformation())
                        }
                    }
                }

                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE

                    Toast.makeText(
                        this,
                        resource.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        // ==========================================
        // OBSERVE SAVE PROFILE RESULT
        // ==========================================

        viewModel.updateProfileState.observe(this) { resource ->

            when (resource) {

                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnSaveProfile.isEnabled = false
                }

                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnSaveProfile.isEnabled = true

                    if (
                        previousImageUrl.isNotBlank() &&
                        uploadedImageUrl.isNotBlank() &&
                        previousImageUrl != uploadedImageUrl
                    ) {
                        S3Uploader.deleteImage(previousImageUrl)
                    }

                    Toast.makeText(
                        this,
                        "Profile updated successfully",
                        Toast.LENGTH_SHORT
                    ).show()

                    finish()
                }

                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnSaveProfile.isEnabled = true

                    Toast.makeText(
                        this,
                        resource.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
