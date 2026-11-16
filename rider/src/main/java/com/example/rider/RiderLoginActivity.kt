package com.example.rider

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class RiderLoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var tvSignup: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_rider_login)

        // Initialize Firebase Authentication
        auth = FirebaseAuth.getInstance()

        // Connect XML views
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvSignup = findViewById(R.id.tvSignup)
        progressBar = findViewById(R.id.progressBar)

        // Login button
        btnLogin.setOnClickListener {
            loginRider()
        }

        // Sign Up button
        tvSignup.setOnClickListener {
            val intent = Intent(
                this,
                RiderSignupActivity::class.java
            )
            startActivity(intent)
        }
    }

    private fun loginRider() {

        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        // Validate fields
        if (email.isEmpty()) {
            etEmail.error = "Enter your email"
            etEmail.requestFocus()
            return
        }

        if (password.isEmpty()) {
            etPassword.error = "Enter your password"
            etPassword.requestFocus()
            return
        }

        setLoading(true)

        // Sign in using Firebase Authentication
        auth.signInWithEmailAndPassword(
            email,
            password
        ).addOnCompleteListener(this) { task ->

            if (!task.isSuccessful) {

                setLoading(false)

                showMessage(
                    task.exception?.localizedMessage
                        ?: "Login failed. Please try again."
                )

                return@addOnCompleteListener
            }

            val uid = auth.currentUser?.uid

            if (uid == null) {
                setLoading(false)

                auth.signOut()
                showMessage("Unable to identify your account")
                return@addOnCompleteListener
            }

            // Verify rider role in Firebase Database
            verifyRiderRole(uid)
        }
    }

    private fun verifyRiderRole(uid: String) {

        FirebaseDatabase.getInstance()
            .getReference("users")
            .child("riders")
            .child(uid)
            .child("role")
            .get()
            .addOnSuccessListener { snapshot ->

                setLoading(false)

                val role = snapshot
                    .getValue(String::class.java)
                    ?.trim()
                    ?.lowercase()

                when (role) {

                    "rider" -> {
                        // Approved rider
                        openDashboard()
                    }

                    "pending_rider" -> {
                        // Registration requires approval
                        auth.signOut()

                        showMessage(
                            "Your rider account is waiting for approval."
                        )
                    }

                    else -> {
                        // Buyer, seller, or unknown account
                        auth.signOut()

                        showMessage(
                            "This account is not registered as an approved rider."
                        )
                    }
                }
            }
            .addOnFailureListener { error ->

                setLoading(false)
                auth.signOut()

                showMessage(
                    "Unable to verify rider account: ${error.localizedMessage}"
                )
            }
    }

    private fun openDashboard() {

        val intent = Intent(
            this,
            RiderDashboardActivity::class.java
        )

        intent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)
        finish()
    }

    private fun setLoading(loading: Boolean) {

        progressBar.visibility =
            if (loading) View.VISIBLE
            else View.GONE

        btnLogin.isEnabled = !loading
        tvSignup.isEnabled = !loading

        etEmail.isEnabled = !loading
        etPassword.isEnabled = !loading
    }

    private fun showMessage(message: String) {

        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }
}
