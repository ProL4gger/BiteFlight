package com.example.biteflight.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.biteflight.MainActivity
import com.example.biteflight.R
import com.example.biteflight.data.model.DeliveryAddress
import com.example.biteflight.data.model.User
import com.example.biteflight.data.repository.AuthRepository
import com.example.biteflight.databinding.ActivitySignUpBinding
import com.example.biteflight.ui.MapPickerActivity
import com.example.biteflight.utils.LocationHelper
import com.example.biteflight.utils.Resource

class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignUpBinding
    private val authRepository = AuthRepository()
    private var location: Pair<Double, Double>? = null
    private var selectedGovernorate: String? = null

    private val mapPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val lat = data.getDoubleExtra(MapPickerActivity.EXTRA_LAT, Double.NaN)
        val lng = data.getDoubleExtra(MapPickerActivity.EXTRA_LNG, Double.NaN)
        if (LocationHelper.hasValidCoordinates(lat, lng)) {
            location = lat to lng
            showLocationStatus()
        } else {
            Toast.makeText(this, R.string.sign_up_location_required, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState != null) {
            val lat = savedInstanceState.getDouble(MapPickerActivity.EXTRA_LAT, Double.NaN)
            val lng = savedInstanceState.getDouble(MapPickerActivity.EXTRA_LNG, Double.NaN)
            if (LocationHelper.hasValidCoordinates(lat, lng)) location = lat to lng
        }
        showLocationStatus()
        setupAddressDropdowns(savedInstanceState)
        binding.btnPickSignUpLocation.setOnClickListener {
            mapPicker.launch(Intent(this, MapPickerActivity::class.java).apply {
                location?.let { (lat, lng) ->
                    putExtra(MapPickerActivity.EXTRA_LAT, lat)
                    putExtra(MapPickerActivity.EXTRA_LNG, lng)
                }
            })
        }
        binding.btnSignUp.setOnClickListener {
            registerUser()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_GOVERNORATE, selectedGovernorate)
        outState.putString(STATE_AREA, binding.areaDropdown.text.toString())
        location?.let { (lat, lng) ->
            outState.putDouble(MapPickerActivity.EXTRA_LAT, lat)
            outState.putDouble(MapPickerActivity.EXTRA_LNG, lng)
        }
    }

    private fun setupAddressDropdowns(savedInstanceState: Bundle?) {
        val governorates = EgyptAddressOptions.areasByGovernorate
        binding.governorateDropdown.setSimpleItems(governorates.keys.toTypedArray())
        binding.governorateDropdown.setOnItemClickListener { parent, _, position, _ ->
            val governorate = parent.getItemAtPosition(position).toString()
            if (selectedGovernorate != governorate) {
                binding.etOtherArea.text?.clear()
                showAreas(governorate)
            }
            binding.governorateField.error = null
        }
        binding.areaDropdown.setOnItemClickListener { parent, _, position, _ ->
            showOtherArea(parent.getItemAtPosition(position).toString())
            binding.areaField.error = null
        }
        val governorate = savedInstanceState?.getString(STATE_GOVERNORATE)
            ?.takeIf { it in governorates }
        binding.governorateDropdown.setText(governorate.orEmpty(), false)
        showAreas(governorate, savedInstanceState?.getString(STATE_AREA))
    }

    private fun showAreas(governorate: String?, restoredArea: String? = null) {
        selectedGovernorate = governorate
        val areas = if (governorate == null) emptyList() else
            EgyptAddressOptions.areasByGovernorate.getValue(governorate).sorted() +
                getString(R.string.sign_up_other_area)
        val area = restoredArea?.takeIf { it in areas }.orEmpty()
        binding.areaDropdown.setSimpleItems(areas.toTypedArray())
        binding.areaDropdown.setText(area, false)
        binding.areaField.isEnabled = governorate != null
        binding.areaField.error = null
        showOtherArea(area)
    }

    private fun showOtherArea(area: String) {
        binding.otherAreaField.isVisible = area == getString(R.string.sign_up_other_area)
        binding.otherAreaField.error = null
    }

    private fun showLocationStatus() {
        binding.tvSignUpLocation.setText(
            if (location == null) R.string.sign_up_location_required else R.string.sign_up_location_selected
        )
        binding.btnPickSignUpLocation.setText(
            if (location == null) R.string.sign_up_pick_location else R.string.sign_up_change_location
        )
    }

    private fun registerUser() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val fullName = binding.etFullName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()

        val role = when (binding.radioGroupRole.checkedRadioButtonId) {
            R.id.radioSeller -> User.ROLE_SELLER
            else -> User.ROLE_BUYER
        }

        if (email.isEmpty() || password.isEmpty() || fullName.isEmpty()) {
            Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 6) {
            binding.etPassword.error = "Password must be at least 6 characters"
            return
        }

        val governorate = selectedGovernorate
        if (governorate == null) {
            binding.governorateField.error = getString(R.string.sign_up_select_governorate)
            binding.governorateDropdown.requestFocus()
            return
        }
        val selectedArea = binding.areaDropdown.text.toString()
        if (selectedArea.isBlank()) {
            binding.areaField.error = getString(R.string.sign_up_select_area)
            binding.areaDropdown.requestFocus()
            return
        }
        val area = if (selectedArea == getString(R.string.sign_up_other_area)) {
            binding.etOtherArea.text.toString().trim().ifBlank {
                binding.otherAreaField.error = getString(R.string.sign_up_enter_area)
                binding.etOtherArea.requestFocus()
                return
            }
        } else {
            selectedArea
        }

        val coordinates = location
        if (coordinates == null) {
            Toast.makeText(this, R.string.sign_up_location_required, Toast.LENGTH_SHORT).show()
            binding.btnPickSignUpLocation.requestFocus()
            return
        }

        val (lat, lng) = coordinates
        val address = DeliveryAddress(
            city = governorate,
            area = area,
            street = binding.etStreet.text.toString().trim(),
            lat = lat,
            lng = lng
        )
        val isSeller = role == User.ROLE_SELLER
        val user = User(
            name = fullName,
            email = email,
            phone = phone,
            address = address.formatted(),
            role = role,
            deliveryAddressDetails = if (isSeller) DeliveryAddress() else address,
            lat = if (isSeller) lat else 0.0,
            lng = if (isSeller) lng else 0.0
        )

        authRepository.signUp(user, password) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.btnSignUp.isEnabled = false
                }
                is Resource.Success -> {
                    Toast.makeText(this, "Account created successfully as $role!", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                }
                is Resource.Error -> {
                    binding.btnSignUp.isEnabled = true
                    Toast.makeText(this, resource.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private companion object {
        const val STATE_GOVERNORATE = "signup.governorate"
        const val STATE_AREA = "signup.area"
    }
}
