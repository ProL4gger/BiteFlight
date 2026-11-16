package com.example.biteflight.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.biteflight.MainActivity
import com.example.biteflight.R
import com.example.biteflight.data.model.User
import com.example.biteflight.data.repository.AuthRepository
import com.example.biteflight.databinding.ActivitySignUpBinding
import com.example.biteflight.utils.Resource

class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignUpBinding
    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSignUp.setOnClickListener {
            registerUser()
        }
    }

    private fun registerUser() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val fullName = binding.etFullName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val address = binding.etCity.text.toString().trim() // Or combine city/area/street if needed

        // Capture selected role from the RadioGroup
        val selectedRoleId = binding.radioGroupRole.checkedRadioButtonId
        if (selectedRoleId == R.id.radioDelivery) {
            Toast.makeText(this, "Please register through the rider app", Toast.LENGTH_LONG).show()
            return
        }
        val role = when (selectedRoleId) {
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

        val user = User(
            name = fullName,
            email = email,
            phone = phone,
            address = address,
            role = role
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
}
