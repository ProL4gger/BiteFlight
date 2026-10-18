package com.example.biteflight.ui.seller

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.biteflight.R
import com.example.biteflight.data.model.User
import com.example.biteflight.databinding.ActivitySellerAvailabilityBinding
import com.example.biteflight.utils.RestaurantAvailability
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.Locale

class SellerAvailabilityActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySellerAvailabilityBinding
    private var opening = ""
    private var closing = ""
    private var loaded = false
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySellerAvailabilityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SellerNavigation.attach(this, R.id.sellerAvailability)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.topToolbar))
        supportActionBar?.apply {
            title = "Availability"
            setDisplayHomeAsUpEnabled(true)
        }
        enableControls(false)
        binding.btnOpeningTime.setOnClickListener { chooseTime(true) }
        binding.btnClosingTime.setOnClickListener { chooseTime(false) }
        binding.btnSaveAvailability.setOnClickListener { save() }
        val sellerId = uid
        if (sellerId == null) { finish(); return }
        FirebaseDatabase.getInstance().getReference("users").child(sellerId).get()
            .addOnSuccessListener { snapshot ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                val user = snapshot.getValue(User::class.java)
                if (user == null || !user.role.equals(User.ROLE_SELLER, true)) {
                    Toast.makeText(this, "Seller account required", Toast.LENGTH_LONG).show()
                    finish()
                    return@addOnSuccessListener
                }
                opening = savedInstanceState?.getString("opening") ?: user.openingTime
                closing = savedInstanceState?.getString("closing") ?: user.closingTime
                binding.switchBusy.isChecked = savedInstanceState?.getBoolean("busy") ?: user.busy
                binding.tvHoursHelp.text = "Daily hours · ${user.restaurantTimeZone}. Changes apply after saving. Closing before opening means the next day."
                loaded = true
                enableControls(true)
                showHours()
            }
            .addOnFailureListener {
                if (isFinishing || isDestroyed) return@addOnFailureListener
                binding.progressBar.visibility = View.GONE
                binding.tvHoursHelp.text = "Could not load availability. Reopen this tab to try again."
            }
    }

    private fun enableControls(enabled: Boolean) {
        binding.switchBusy.isEnabled = enabled
        binding.btnOpeningTime.isEnabled = enabled
        binding.btnClosingTime.isEnabled = enabled
        binding.btnSaveAvailability.isEnabled = enabled
        binding.progressBar.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    private fun chooseTime(isOpening: Boolean) {
        val value = RestaurantAvailability.minutes(if (isOpening) opening else closing)
            ?: if (isOpening) 540 else 1320
        TimePickerDialog(this, { _, hour, minute ->
            val time = String.format(Locale.US, "%02d:%02d", hour, minute)
            if (isOpening) opening = time else closing = time
            showHours()
        }, value / 60, value % 60, true).show()
    }

    private fun showHours() {
        binding.btnOpeningTime.text = if (opening.isBlank()) "Set opening time" else "Opens at $opening"
        binding.btnClosingTime.text = if (closing.isBlank()) "Set closing time" else "Closes at $closing"
    }

    private fun save() {
        val sellerId = uid ?: return
        if (!loaded) return
        if ((opening.isNotBlank() || closing.isNotBlank()) &&
            (RestaurantAvailability.minutes(opening) == null ||
             RestaurantAvailability.minutes(closing) == null || opening == closing)) {
            Toast.makeText(this, "Choose different opening and closing times", Toast.LENGTH_LONG).show()
            return
        }
        enableControls(false)
        FirebaseDatabase.getInstance().getReference("users").child(sellerId).updateChildren(
            mapOf("busy" to binding.switchBusy.isChecked, "openingTime" to opening, "closingTime" to closing)
        ).addOnCompleteListener { task ->
            if (isFinishing || isDestroyed) return@addOnCompleteListener
            enableControls(true)
            Toast.makeText(this, if (task.isSuccessful) "Availability saved" else
                "Could not save availability. Please try again.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (loaded) {
            outState.putString("opening", opening)
            outState.putString("closing", closing)
            outState.putBoolean("busy", binding.switchBusy.isChecked)
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
