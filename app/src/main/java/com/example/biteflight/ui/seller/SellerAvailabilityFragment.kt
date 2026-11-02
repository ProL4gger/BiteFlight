package com.example.biteflight.ui.seller

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.biteflight.R
import com.example.biteflight.data.model.User
import com.example.biteflight.databinding.ActivitySellerAvailabilityBinding
import com.example.biteflight.utils.RestaurantAvailability
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.Locale

class SellerAvailabilityFragment : Fragment() {

    private var _binding: ActivitySellerAvailabilityBinding? = null
    private val binding get() = _binding!!
    private var opening = ""
    private var closing = ""
    private var loaded = false
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivitySellerAvailabilityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.topToolbar)?.visibility = View.GONE

        enableControls(false)
        binding.btnOpeningTime.setOnClickListener { chooseTime(true) }
        binding.btnClosingTime.setOnClickListener { chooseTime(false) }
        binding.btnSaveAvailability.setOnClickListener { save() }

        val sellerId = uid
        if (sellerId != null) {
            FirebaseDatabase.getInstance().getReference("users").child(sellerId).get()
                .addOnSuccessListener { snapshot ->
                    if (_binding == null) return@addOnSuccessListener
                    val user = snapshot.getValue(User::class.java)
                    if (user == null || !user.role.equals(User.ROLE_SELLER, true)) {
                        Toast.makeText(requireContext(), "Seller account required", Toast.LENGTH_LONG).show()
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
                    if (_binding == null) return@addOnFailureListener
                    binding.progressBar.visibility = View.GONE
                    binding.tvHoursHelp.text = "Could not load availability. Reopen this tab to try again."
                }
        }
    }

    private fun enableControls(enabled: Boolean) {
        if (_binding == null) return
        binding.switchBusy.isEnabled = enabled
        binding.btnOpeningTime.isEnabled = enabled
        binding.btnClosingTime.isEnabled = enabled
        binding.btnSaveAvailability.isEnabled = enabled
        binding.progressBar.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    private fun chooseTime(isOpening: Boolean) {
        val value = RestaurantAvailability.minutes(if (isOpening) opening else closing)
            ?: if (isOpening) 540 else 1320
        TimePickerDialog(requireContext(), { _, hour, minute ->
            val time = String.format(Locale.US, "%02d:%02d", hour, minute)
            if (isOpening) opening = time else closing = time
            showHours()
        }, value / 60, value % 60, true).show()
    }

    private fun showHours() {
        if (_binding == null) return
        binding.btnOpeningTime.text = if (opening.isBlank()) "Set opening time" else "Opens at $opening"
        binding.btnClosingTime.text = if (closing.isBlank()) "Set closing time" else "Closes at $closing"
    }

    private fun save() {
        val sellerId = uid ?: return
        if (!loaded || _binding == null) return
        if ((opening.isNotBlank() || closing.isNotBlank()) &&
            (RestaurantAvailability.minutes(opening) == null ||
             RestaurantAvailability.minutes(closing) == null || opening == closing)) {
            Toast.makeText(requireContext(), "Choose different opening and closing times", Toast.LENGTH_LONG).show()
            return
        }
        enableControls(false)
        FirebaseDatabase.getInstance().getReference("users").child(sellerId).updateChildren(
            mapOf("busy" to binding.switchBusy.isChecked, "openingTime" to opening, "closingTime" to closing)
        ).addOnCompleteListener { task ->
            if (_binding == null) return@addOnCompleteListener
            enableControls(true)
            Toast.makeText(requireContext(), if (task.isSuccessful) "Availability saved" else
                "Could not save availability. Please try again.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
