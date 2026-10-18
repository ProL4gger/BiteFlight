package com.example.biteflight.ui.auth
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.biteflight.MainActivity
import com.example.biteflight.R
import com.example.biteflight.data.model.DeliveryAddress
import com.example.biteflight.data.model.User
import com.example.biteflight.databinding.ActivitySignUpBinding
import com.example.biteflight.ui.seller.SellerDashboardActivity
import com.example.biteflight.utils.Resource
import android.widget.EditText
import com.google.android.material.appbar.MaterialToolbar

class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignUpBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = findViewById<MaterialToolbar>(R.id.topToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Sign Up"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    returnToLogin()
                }
            }
        )

        binding.rgRole.setOnCheckedChangeListener { _, _ ->
            updateRoleFields()
        }

        updateRoleFields()

        binding.btnSignUp.setOnClickListener {
            performSignUp()
        }

        binding.tvLoginLink.setOnClickListener {
            returnToLogin()
        }

        observeViewModel()
    }

    override fun onSupportNavigateUp(): Boolean {
        returnToLogin()
        return true
    }

    private fun returnToLogin() {
        if (isTaskRoot) {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }


    private fun updateRoleFields() {
        val isSeller = binding.rbSeller.isChecked

        binding.tilName.hint =
            if (isSeller) "Business / Restaurant Name"
            else "Full Name"

        binding.tilAddress.visibility =
            if (isSeller) View.VISIBLE else View.GONE

        findViewById<View>(R.id.buyerAddressForm).visibility =
            if (isSeller) View.GONE else View.VISIBLE
    }



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


    private fun performSignUp() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val phone = binding.etPhone.text.toString().trim()

        val isSeller = binding.rbSeller.isChecked
        val role = if (isSeller) User.ROLE_SELLER else User.ROLE_BUYER

        val details = if (isSeller) DeliveryAddress() else readDeliveryAddress()

        val address = if (isSeller) {
            binding.etAddress.text.toString().trim()
        } else {
            details.formatted()
        }

        if (name.isBlank() || email.isBlank() ||
            password.isBlank() || phone.isBlank()
        ) {
            Toast.makeText(
                this, "Please complete all account fields",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (isSeller && address.isBlank()) {
            binding.etAddress.error = "Enter restaurant address"
            return
        }

        if (!isSeller && !details.isValid()) {
            Toast.makeText(
                this,
                "City, area, street and building are required",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (password.length < 6) {
            Toast.makeText(
                this, "Password must be at least 6 characters",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val user = User(
            name = name,
            email = email,
            phone = phone,
            role = role,
            address = address,
            deliveryAddressDetails = details
        )

        viewModel.signUp(user, password)
    }

    private fun observeViewModel() {
        viewModel.signUpState.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnSignUp.isEnabled = false
                }

                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnSignUp.isEnabled = true

                    val destination =
                        if (binding.rbSeller.isChecked)
                            SellerDashboardActivity::class.java
                        else
                            MainActivity::class.java

                    startActivity(Intent(this, destination).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                }

                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnSignUp.isEnabled = true
                    Toast.makeText(
                        this, resource.message, Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
