package com.example.biteflight.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.biteflight.MainActivity
import com.example.biteflight.databinding.ActivityLoginBinding
import com.example.biteflight.ui.seller.SellerDashboardActivity
import com.example.biteflight.utils.Resource
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import android.widget.ImageButton
import com.google.firebase.database.FirebaseDatabase

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {

        binding.btnLogin.setOnClickListener {
            performLogin()
        }

        binding.tvSignUpLink.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    SignUpActivity::class.java
                )
            )

            finish()
        }
    }

    private fun performLogin() {

        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {

            Toast.makeText(
                this,
                "Please enter email and password",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        viewModel.signIn(email, password)
    }

    private fun observeViewModel() {

        viewModel.signInState.observe(this) { resource ->

            when (resource) {

                is Resource.Loading -> {

                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnLogin.isEnabled = false
                }

                is Resource.Success -> {

                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true

                    Toast.makeText(
                        this,
                        "Signed in successfully!",
                        Toast.LENGTH_SHORT
                    ).show()

                    openCorrectPage()
                }

                is Resource.Error -> {

                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true

                    Toast.makeText(
                        this,
                        resource.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun openCorrectPage() {

        val currentUser = auth.currentUser

        if (currentUser == null) {

            Toast.makeText(
                this,
                "No logged-in user found",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val userId = currentUser.uid

        FirebaseDatabase
            .getInstance()
            .getReference("users")
            .child(userId)
            .get()
            .addOnSuccessListener { snapshot ->

                val role = snapshot
                    .child("role")
                    .getValue(String::class.java)

                if (role?.uppercase() == "SELLER") {

                    val intent = Intent(
                        this,
                        SellerDashboardActivity::class.java
                    )

                    startActivity(intent)

                } else {

                    val intent = Intent(
                        this,
                        MainActivity::class.java
                    )

                    startActivity(intent)
                }

                finishAffinity()
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "Could not read user role: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }
}