package com.example.rider

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

class RiderSignupActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnSignup: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rider_signup)

        etName = findViewById(R.id.etName)
        etPhone = findViewById(R.id.etPhone)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword =
            findViewById(R.id.etConfirmPassword)

        btnSignup = findViewById(R.id.btnSignup)
        progressBar = findViewById(R.id.progressBar)

        btnSignup.setOnClickListener {
            createRiderAccount()
        }

        findViewById<TextView>(R.id.tvLogin)
            .setOnClickListener {
                finish()
            }
    }

    private fun createRiderAccount() {
        val name = etName.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        val confirmPassword =
            etConfirmPassword.text.toString()

        if (name.isBlank() ||
            phone.isBlank() ||
            email.isBlank() ||
            password.isBlank()
        ) {
            showMessage("Please complete all fields")
            return
        }

        if (password.length < 6) {
            showMessage(
                "Password must contain at least 6 characters"
            )
            return
        }

        if (password != confirmPassword) {
            showMessage("Passwords do not match")
            return
        }

        setLoading(true)

        auth.createUserWithEmailAndPassword(
            email,
            password
        ).addOnCompleteListener(this) { task ->

            if (!task.isSuccessful) {
                setLoading(false)
                showMessage(
                    task.exception?.localizedMessage
                        ?: "Registration failed"
                )
                return@addOnCompleteListener
            }

            val firebaseUser = task.result?.user

            if (firebaseUser == null) {
                setLoading(false)
                auth.signOut()
                showMessage("Could not get user ID")
                return@addOnCompleteListener
            }

            val uid = firebaseUser.uid
            val riderData = mapOf(
                "uid" to uid,
                "name" to name,
                "phone" to phone,
                "email" to email,
                "role" to "pending_rider"
            )

            database.getReference("users")
                .child("riders")
                .child(uid)
                .setValue(riderData)
                .addOnSuccessListener {
                    setLoading(false)
                    auth.signOut()

                    showMessage(
                        "Account created. Await rider approval."
                    )
                    finish()
                }
                .addOnFailureListener { error ->
                    // Undo only this newly created Auth account when its
                    // required database profile could not be saved.
                    firebaseUser.delete().addOnCompleteListener { deleteTask ->
                        setLoading(false)
                        auth.signOut()

                        val reason = error.localizedMessage
                            ?: "Could not save rider profile"
                        if (deleteTask.isSuccessful) {
                            showMessage(
                                "Registration was not completed: $reason. " +
                                    "Please try again after database access is fixed."
                            )
                        } else {
                            showMessage(
                                "Your sign-in account was created, but its rider " +
                                    "profile could not be saved: $reason. " +
                                    "Please contact support to restore your profile."
                            )
                        }
                    }
                }
        }
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility =
            if (loading) View.VISIBLE else View.GONE

        btnSignup.isEnabled = !loading
        etName.isEnabled = !loading
        etPhone.isEnabled = !loading
        etEmail.isEnabled = !loading
        etPassword.isEnabled = !loading
        etConfirmPassword.isEnabled = !loading
    }

    private fun showMessage(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }
}
