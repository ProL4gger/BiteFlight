package com.example.biteflight.ui.buyer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import coil.load
import coil.transform.CircleCropTransformation
import com.example.biteflight.R
import com.example.biteflight.data.model.DeliveryAddress
import com.example.biteflight.data.model.User
import com.example.biteflight.ui.MapPickerActivity
import com.example.biteflight.ui.auth.AuthViewModel
import com.example.biteflight.utils.LocationHelper
import com.example.biteflight.utils.Resource
import com.example.biteflight.utils.S3Uploader
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText

class BuyerProfileFragment : Fragment() {

    private val viewModel: AuthViewModel = AuthViewModel()
    private var currentUserProfile: User? = null
    private var uploadedImageUrl: String = ""
    private var previousImageUrl: String = ""

    private var pickedLat = 0.0
    private var pickedLng = 0.0
    private lateinit var tvLocationStatus: TextView

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) fetchCurrentLocation()
        else Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
    }

    private val mapPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            pickedLat = data.getDoubleExtra(MapPickerActivity.EXTRA_LAT, 0.0)
            pickedLng = data.getDoubleExtra(MapPickerActivity.EXTRA_LNG, 0.0)
            tvLocationStatus.text = "Location set ✓ (%.4f, %.4f)".format(pickedLat, pickedLng)
        }
    }

    private lateinit var etEditName: TextInputEditText
    private lateinit var etEditPhone: TextInputEditText
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

        // Hide simple address, show detailed delivery address form for buyers
        view.findViewById<View>(R.id.tilEditAddress)?.visibility = View.GONE
        view.findViewById<View>(R.id.buyerAddressForm)?.visibility = View.VISIBLE

        etEditName = view.findViewById(R.id.etEditName)
        etEditPhone = view.findViewById(R.id.etEditPhone)
        ivProfilePicture = view.findViewById(R.id.ivProfilePicture)
        btnUploadImage = view.findViewById(R.id.btnUploadImage)
        btnSaveProfile = view.findViewById(R.id.btnSaveProfile)
        progressBar = view.findViewById(R.id.progressBar)

        tvLocationStatus = view.findViewById(R.id.tvLocationStatus)
        view.findViewById<Button>(R.id.btnUseLocation).setOnClickListener {
            if (LocationHelper.hasPermission(requireContext())) fetchCurrentLocation()
            else locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        view.findViewById<Button>(R.id.btnPickOnMap).setOnClickListener {
            // Open the map where the buyer already pinned (this session or saved), if anywhere
            val saved = currentUserProfile?.deliveryAddressDetails
            val hasPicked = pickedLat != 0.0 || pickedLng != 0.0
            val startLat = if (hasPicked) pickedLat else (saved?.lat ?: 0.0)
            val startLng = if (hasPicked) pickedLng else (saved?.lng ?: 0.0)
            mapPickerLauncher.launch(
                Intent(requireContext(), MapPickerActivity::class.java)
                    .putExtra(MapPickerActivity.EXTRA_LAT, startLat)
                    .putExtra(MapPickerActivity.EXTRA_LNG, startLng)
            )
        }

        btnUploadImage.setOnClickListener {
            selectImageLauncher.launch("image/*")
        }

        btnSaveProfile.setOnClickListener {
            val profile = currentUserProfile ?: return@setOnClickListener
            val name = etEditName.text.toString().trim()
            val phone = etEditPhone.text.toString().trim()
            val deliveryAddress = readDeliveryAddress()

            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(requireContext(), "Name and phone cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val addressSummary = listOfNotNull(
                deliveryAddress.street.ifBlank { null },
                deliveryAddress.building.ifBlank { null },
                deliveryAddress.area.ifBlank { null },
                deliveryAddress.city.ifBlank { null }
            ).joinToString(", ").ifBlank { profile.address }

            // Building number confuses geocoders, so only street + area + city
            val geocodeQuery = listOfNotNull(
                deliveryAddress.street.ifBlank { null },
                deliveryAddress.area.ifBlank { null },
                deliveryAddress.city.ifBlank { null }
            ).joinToString(", ")

            btnSaveProfile.isEnabled = false
            resolveCoordinates(
                geocodeQuery,
                profile.deliveryAddressDetails.lat,
                profile.deliveryAddressDetails.lng
            ) { lat, lng ->
                if (!isAdded) return@resolveCoordinates
                btnSaveProfile.isEnabled = true
                if (lat == 0.0 && lng == 0.0) {
                    Toast.makeText(
                        requireContext(),
                        "Couldn't locate this address. Use 'Pick on map' or 'Use my current location' so delivery fees work.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                val updatedUser = profile.copy(
                    name = name,
                    phone = phone,
                    address = addressSummary,
                    deliveryAddressDetails = deliveryAddress.copy(lat = lat, lng = lng),
                    imageUrl = if (uploadedImageUrl.isNotEmpty()) uploadedImageUrl else profile.imageUrl
                )
                viewModel.updateProfile(updatedUser)
            }
        }

        loadUserProfile()
        observeViewModel()
    }

    private fun readDeliveryAddress(): DeliveryAddress {
        val view = view ?: return DeliveryAddress()
        return DeliveryAddress(
            city = view.findViewById<EditText>(R.id.etCity)?.text?.toString()?.trim().orEmpty(),
            area = view.findViewById<EditText>(R.id.etArea)?.text?.toString()?.trim().orEmpty(),
            street = view.findViewById<EditText>(R.id.etStreet)?.text?.toString()?.trim().orEmpty(),
            building = view.findViewById<EditText>(R.id.etBuilding)?.text?.toString()?.trim().orEmpty(),
            floor = view.findViewById<EditText>(R.id.etFloor)?.text?.toString()?.trim().orEmpty(),
            apartment = view.findViewById<EditText>(R.id.etApartment)?.text?.toString()?.trim().orEmpty(),
            landmark = view.findViewById<EditText>(R.id.etLandmark)?.text?.toString()?.trim().orEmpty(),
            instructions = view.findViewById<EditText>(R.id.etInstructions)?.text?.toString()?.trim().orEmpty()
        )
    }

    private fun fillDeliveryAddress(details: DeliveryAddress) {
        val view = view ?: return
        view.findViewById<EditText>(R.id.etCity)?.setText(details.city)
        view.findViewById<EditText>(R.id.etArea)?.setText(details.area)
        view.findViewById<EditText>(R.id.etStreet)?.setText(details.street)
        view.findViewById<EditText>(R.id.etBuilding)?.setText(details.building)
        view.findViewById<EditText>(R.id.etFloor)?.setText(details.floor)
        view.findViewById<EditText>(R.id.etApartment)?.setText(details.apartment)
        view.findViewById<EditText>(R.id.etLandmark)?.setText(details.landmark)
        view.findViewById<EditText>(R.id.etInstructions)?.setText(details.instructions)
        tvLocationStatus.text = if (details.hasCoordinates()) "Location set ✓" else "Location not set"
    }

    private fun loadUserProfile() {
        val user = viewModel.currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "Not signed in", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.fetchProfile(user.uid)
    }

    private fun fetchCurrentLocation() {
        tvLocationStatus.text = "Getting location…"
        LocationHelper.getCurrentLocation(requireContext()) { result ->
            if (!isAdded) return@getCurrentLocation
            if (result == null) {
                tvLocationStatus.text = "Couldn't get location. Is GPS on?"
                return@getCurrentLocation
            }
            pickedLat = result.first
            pickedLng = result.second
            tvLocationStatus.text = "Location set ✓ (%.4f, %.4f)".format(pickedLat, pickedLng)
        }
    }

    /** Priority: pin picked now (map or GPS) > geocoded typed address > coordinates already saved. */
    private fun resolveCoordinates(
        address: String,
        existingLat: Double,
        existingLng: Double,
        onDone: (Double, Double) -> Unit
    ) {
        if (pickedLat != 0.0 || pickedLng != 0.0) {
            onDone(pickedLat, pickedLng)
            return
        }
        LocationHelper.geocode(requireContext(), address) { result ->
            if (result != null) onDone(result.first, result.second)
            else onDone(existingLat, existingLng)
        }
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
                    fillDeliveryAddress(profile.deliveryAddressDetails)

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