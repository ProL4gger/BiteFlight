package com.example.biteflight.ui.seller

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import coil.load
import coil.transform.CircleCropTransformation
import com.example.biteflight.R
import com.example.biteflight.data.model.User
import com.example.biteflight.ui.auth.AuthViewModel
import com.example.biteflight.utils.Resource
import com.example.biteflight.utils.S3Uploader
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText

class SellerProfileFragment : Fragment() {

    private val viewModel: AuthViewModel = AuthViewModel()
    private var currentUserProfile: User? = null
    private var uploadedImageUrl: String = ""
    private var previousImageUrl: String = ""

    private lateinit var etEditName: TextInputEditText
    private lateinit var etEditPhone: TextInputEditText
    private lateinit var etEditAddress: TextInputEditText
    private lateinit var ivProfilePicture: ShapeableImageView
    private lateinit var btnUploadImage: Button
    private lateinit var btnSaveProfile: Button
    private lateinit var progressBar: ProgressBar

    private val selectImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            uploadProfilePictureToS3(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_edit_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.topToolbar)?.visibility = View.GONE

        // For sellers, show Restaurant Address field, hide buyer address form
        view.findViewById<View>(R.id.tilEditAddress)?.visibility = View.VISIBLE
        view.findViewById<View>(R.id.buyerAddressForm)?.visibility = View.GONE

        etEditName = view.findViewById(R.id.etEditName)
        etEditPhone = view.findViewById(R.id.etEditPhone)
        etEditAddress = view.findViewById(R.id.etEditAddress)
        ivProfilePicture = view.findViewById(R.id.ivProfilePicture)
        btnUploadImage = view.findViewById(R.id.btnUploadImage)
        btnSaveProfile = view.findViewById(R.id.btnSaveProfile)
        progressBar = view.findViewById(R.id.progressBar)

        btnUploadImage.setOnClickListener {
            selectImageLauncher.launch("image/*")
        }

        btnSaveProfile.setOnClickListener {
            val profile = currentUserProfile ?: return@setOnClickListener
            val name = etEditName.text.toString().trim()
            val phone = etEditPhone.text.toString().trim()
            val address = etEditAddress.text.toString().trim()

            if (name.isEmpty() || phone.isEmpty() || address.isEmpty()) {
                Toast.makeText(requireContext(), "Fields cannot be empty", Toast.LENGTH_SHORT).show()
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

        loadUserProfile()
        observeViewModel()
    }

    private fun loadUserProfile() {
        val user = viewModel.currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "Not signed in", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.fetchProfile(user.uid)
    }

    private fun uploadProfilePictureToS3(uri: Uri) {
        S3Uploader.uploadImage(
            context = requireContext(),
            imageUri = uri,
            folderName = "profiles"
        ) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    progressBar.visibility = View.VISIBLE
                    btnUploadImage.isEnabled = false
                    Toast.makeText(requireContext(), "Uploading image...", Toast.LENGTH_SHORT).show()
                }
                is Resource.Success -> {
                    progressBar.visibility = View.GONE
                    btnUploadImage.isEnabled = true
                    uploadedImageUrl = resource.data

                    ivProfilePicture.load(uploadedImageUrl) {
                        crossfade(true)
                        placeholder(R.drawable.ic_person)
                        error(R.drawable.ic_person)
                        transformations(CircleCropTransformation())
                    }
                    Toast.makeText(requireContext(), "Image uploaded successfully!", Toast.LENGTH_SHORT).show()
                }
                is Resource.Error -> {
                    progressBar.visibility = View.GONE
                    btnUploadImage.isEnabled = true
                    Toast.makeText(requireContext(), resource.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun observeViewModel() {
        viewModel.userProfileState.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    progressBar.visibility = View.VISIBLE
                }
                is Resource.Success -> {
                    progressBar.visibility = View.GONE
                    val profile = resource.data
                    currentUserProfile = profile
                    previousImageUrl = profile.imageUrl

                    etEditName.setText(profile.name)
                    etEditPhone.setText(profile.phone)
                    etEditAddress.setText(profile.address)

                    if (profile.imageUrl.isNotEmpty()) {
                        ivProfilePicture.load(profile.imageUrl) {
                            crossfade(true)
                            placeholder(R.drawable.ic_person)
                            error(R.drawable.ic_person)
                            transformations(CircleCropTransformation())
                        }
                    }
                }
                is Resource.Error -> {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), resource.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewModel.updateProfileState.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    progressBar.visibility = View.VISIBLE
                    btnSaveProfile.isEnabled = false
                }
                is Resource.Success -> {
                    progressBar.visibility = View.GONE
                    btnSaveProfile.isEnabled = true

                    if (previousImageUrl.isNotEmpty() && uploadedImageUrl.isNotEmpty() && uploadedImageUrl != previousImageUrl) {
                        S3Uploader.deleteImage(previousImageUrl)
                    }

                    Toast.makeText(requireContext(), "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                }
                is Resource.Error -> {
                    progressBar.visibility = View.GONE
                    btnSaveProfile.isEnabled = true
                    Toast.makeText(requireContext(), resource.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
