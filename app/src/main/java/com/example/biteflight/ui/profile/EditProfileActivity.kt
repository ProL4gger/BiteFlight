package com.example.biteflight.ui.profile

import android.net.Uri



import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import coil.load
import coil.transform.CircleCropTransformation
import com.example.biteflight.R
import com.example.biteflight.data.model.User
import com.example.biteflight.databinding.ActivityEditProfileBinding
import com.example.biteflight.ui.auth.AuthViewModel
import com.example.biteflight.utils.Resource
import com.example.biteflight.utils.S3Uploader
import com.google.android.material.appbar.MaterialToolbar

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private val viewModel: AuthViewModel by viewModels()

    private var currentUserProfile: User? = null
    private var buyerNavigationAttached = false
    private var uploadedImageUrl: String = ""
    private var previousImageUrl: String = ""



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

        val toolbar = findViewById<MaterialToolbar>(R.id.topToolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBarInsets.top)
            insets
        }
        ViewCompat.requestApplyInsets(toolbar)

        setSupportActionBar(toolbar)
        supportActionBar?.title = "Edit Profile"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)

        loadUserProfile()
        setupListeners()
        observeViewModel()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadUserProfile() {
        val user = viewModel.currentUser
        if (user == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        viewModel.fetchProfile(user.uid)
    }

    private fun setupListeners() {


        binding.btnUploadImage.setOnClickListener {
            selectImageLauncher.launch("image/*")
        }

        binding.btnSaveProfile.setOnClickListener {
            val profile = currentUserProfile ?: return@setOnClickListener

            val name = binding.etEditName.text.toString().trim()
            val phone = binding.etEditPhone.text.toString().trim()
            val address = binding.etEditAddress.text.toString().trim()

            if (name.isEmpty() || phone.isEmpty() || address.isEmpty()) {
                Toast.makeText(this, "Fields cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updatedUser = profile.copy(
                name = name,
                phone = phone,
                address = address,
                imageUrl = if (uploadedImageUrl.isNotEmpty()) uploadedImageUrl else profile.imageUrl
            )

            viewModel.updateProfile(updatedUser)
        }
    }

    private fun uploadProfilePictureToS3(uri: Uri) {
        S3Uploader.uploadImage(
            context = this,
            imageUri = uri,
            folderName = "profiles"
        ) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnUploadImage.isEnabled = false
                    Toast.makeText(this, "Uploading image...", Toast.LENGTH_SHORT).show()
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnUploadImage.isEnabled = true
                    uploadedImageUrl = resource.data

                    // Render preview using Coil
                    binding.ivProfilePicture.load(uploadedImageUrl) {
                        crossfade(true)
                        placeholder(R.drawable.ic_person)
                        error(R.drawable.ic_person)
                        transformations(CircleCropTransformation())
                    }

                    Toast.makeText(this, "Image uploaded successfully!", Toast.LENGTH_SHORT).show()
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnUploadImage.isEnabled = true
                    Toast.makeText(this, resource.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun observeViewModel() {
        viewModel.userProfileState.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    val profile = resource.data
                    if (profile.role.equals(User.ROLE_BUYER, true) && !buyerNavigationAttached) {
                        com.example.biteflight.ui.buyer.BuyerNavigation.attach(this, R.id.buyerProfile)
                        buyerNavigationAttached = true
                    }
                    currentUserProfile = profile
                    previousImageUrl = profile.imageUrl

                    binding.etEditName.setText(profile.name)
                    binding.etEditPhone.setText(profile.phone)
                    binding.etEditAddress.setText(profile.address)

                    if (profile.imageUrl.isNotEmpty()) {
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
                    Toast.makeText(this, resource.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewModel.updateProfileState.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnSaveProfile.isEnabled = false
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnSaveProfile.isEnabled = true

                    // Delete old image from S3 if replaced with a new one
                    if (previousImageUrl.isNotEmpty() && uploadedImageUrl.isNotEmpty() && uploadedImageUrl != previousImageUrl) {
                        S3Uploader.deleteImage(previousImageUrl)
                    }

                    Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnSaveProfile.isEnabled = true
                    Toast.makeText(this, resource.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
