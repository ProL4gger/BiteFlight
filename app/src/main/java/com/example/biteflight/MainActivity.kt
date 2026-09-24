package com.example.biteflight

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.biteflight.databinding.ActivityMainBinding
import com.example.biteflight.ui.auth.AuthViewModel
import com.example.biteflight.ui.auth.LoginActivity
import com.example.biteflight.utils.Resource

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupListeners()
        observeViewModel()
        loadUserProfile()
    }

    private fun loadUserProfile() {
        val user = viewModel.currentUser
        if (user == null) {
            navigateToLogin()
            return
        }
        viewModel.fetchProfile(user.uid)
    }

    private fun setupListeners() {
        binding.btnSignOut.setOnClickListener {
            viewModel.logout()
            navigateToLogin()
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
                    binding.tvName.text = profile.name
                    binding.tvRole.text = "Role: ${profile.role}"
                    binding.tvEmail.text = "Email: ${profile.email}"
                    binding.tvPhone.text = "Phone: ${profile.phone}"
                    binding.tvAddress.text = "Address: ${profile.address}"
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, resource.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
