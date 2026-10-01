package com.example.biteflight.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.biteflight.data.model.User
import com.example.biteflight.databinding.ActivityEditProfileBinding
import com.example.biteflight.ui.auth.AuthViewModel
import com.example.biteflight.utils.Resource

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private val viewModel: AuthViewModel by viewModels()
    private var currentUserProfile: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val dp24 = (24 * resources.displayMetrics.density).toInt()
            v.setPadding(
                dp24,
                dp24 + systemBars.top,
                dp24,
                dp24 + systemBars.bottom
            )
            insets
        }

        loadUserProfile()
        setupListeners()
        observeViewModel()
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
                address = address
            )
            viewModel.updateProfile(updatedUser)
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
                    currentUserProfile = resource.data
                    binding.etEditName.setText(resource.data.name)
                    binding.etEditPhone.setText(resource.data.phone)
                    binding.etEditAddress.setText(resource.data.address)
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
