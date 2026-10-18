package com.example.biteflight.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.example.biteflight.MainActivity
import com.example.biteflight.R
import com.example.biteflight.data.model.User
import com.example.biteflight.databinding.ActivitySignUpBinding
import com.example.biteflight.ui.seller.SellerDashboardActivity
import com.example.biteflight.utils.Resource
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignUpBinding
    private lateinit var auth: FirebaseAuth

    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth

        // -------------------------
        // TOP TOOLBAR
        // -------------------------

        val toolbar =
            findViewById<MaterialToolbar>(R.id.topToolbar)

        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->

            val statusBarInsets =
                insets.getInsets(WindowInsetsCompat.Type.statusBars())

            view.updatePadding(
                top = statusBarInsets.top
            )

            insets
        }

        ViewCompat.requestApplyInsets(toolbar)

        setSupportActionBar(toolbar)

        supportActionBar?.title = "Sign Up"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    returnToLogin()
                }
            }
        )

        setupListeners()
        observeViewModel()
    }

    // -------------------------
    // BACK BUTTON
    // -------------------------

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun returnToLogin() {
        // Login stays beneath signup during normal navigation.
        if (isTaskRoot) {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }
        finish()
    }

    private fun setupListeners() {

        // Change hints depending on selected role
        binding.rgRole.setOnCheckedChangeListener { _, checkedId ->

            if (checkedId == binding.rbSeller.id) {

                binding.tilName.hint =
                    "Business / Restaurant Name"

                binding.tilAddress.hint =
                    "Store / Restaurant Address"

            } else {

                binding.tilName.hint =
                    "Full Name"

                binding.tilAddress.hint =
                    "Delivery Address"
            }
        }

        // SIGN UP BUTTON
        binding.btnSignUp.setOnClickListener {
            performSignUp()
        }

        // ALREADY HAVE ACCOUNT
        binding.tvLoginLink.setOnClickListener {
            returnToLogin()
        }
    }

    private fun performSignUp() {

        val name =
            binding.etName.text.toString().trim()

        val email =
            binding.etEmail.text.toString().trim()

        val password =
            binding.etPassword.text.toString().trim()

        val phone =
            binding.etPhone.text.toString().trim()

        val address =
            binding.etAddress.text.toString().trim()

        val role =
            if (binding.rbSeller.isChecked) {

                User.ROLE_SELLER

            } else {

                User.ROLE_BUYER
            }

        // -------------------------
        // VALIDATION
        // -------------------------

        if (
            name.isEmpty() ||
            email.isEmpty() ||
            password.isEmpty() ||
            phone.isEmpty() ||
            address.isEmpty()
        ) {

            Toast.makeText(
                this,
                "Please fill in all required fields",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        if (password.length < 6) {

            Toast.makeText(
                this,
                "Password must be at least 6 characters",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        // -------------------------
        // CREATE USER OBJECT
        // -------------------------

        val user =
            User(
                name = name,
                email = email,
                phone = phone,
                address = address,
                role = role
            )

        viewModel.signUp(
            user,
            password
        )
    }

    private fun observeViewModel() {

        viewModel.signUpState.observe(this) { resource ->

            when (resource) {

                is Resource.Loading -> {

                    binding.progressBar.visibility =
                        View.VISIBLE

                    binding.btnSignUp.isEnabled =
                        false
                }

                is Resource.Success -> {

                    binding.progressBar.visibility =
                        View.GONE

                    binding.btnSignUp.isEnabled =
                        true

                    Toast.makeText(
                        this,
                        "Account created successfully!",
                        Toast.LENGTH_SHORT
                    ).show()

                    if (binding.rbSeller.isChecked) {

                        startActivity(
                            Intent(
                                this,
                                SellerDashboardActivity::class.java
                            ).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                        )

                    } else {

                        startActivity(
                            Intent(
                                this,
                                MainActivity::class.java
                            ).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                        )
                    }

                    finish()
                }

                is Resource.Error -> {

                    binding.progressBar.visibility =
                        View.GONE

                    binding.btnSignUp.isEnabled =
                        true

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
