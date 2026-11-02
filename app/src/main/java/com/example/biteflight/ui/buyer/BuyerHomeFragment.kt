package com.example.biteflight.ui.buyer

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.biteflight.MainActivity
import com.example.biteflight.R
import com.example.biteflight.data.model.Meal
import com.example.biteflight.data.model.User
import com.example.biteflight.utils.RestaurantAvailability
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class BuyerHomeFragment : Fragment() {

    private val allRestaurants = mutableListOf<User>()
    private val visibleRestaurants = mutableListOf<User>()
    private val meals = mutableListOf<Meal>()
    private lateinit var adapter: RestaurantAdapter
    private val database = FirebaseDatabase.getInstance()
    private var usersListener: ValueEventListener? = null
    private var mealsListener: ValueEventListener? = null
    private var openOnly = false
    private var favouritesOnly = false
    private var loaded = false
    private var loadError: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            filterRestaurants()
            handler.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_buyer_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = RestaurantAdapter(visibleRestaurants, { filterRestaurants() }) { restaurant ->
            startActivity(Intent(requireContext(), RestaurantMenuActivity::class.java)
                .putExtra("sellerId", restaurant.uid).putExtra("restaurantName", restaurant.name))
        }
        view.findViewById<RecyclerView>(R.id.recyclerRestaurants)?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@BuyerHomeFragment.adapter
        }
        view.findViewById<EditText>(R.id.etRestaurantSearch)?.apply {
            doAfterTextChanged { filterRestaurants() }
        }
        view.findViewById<TextView>(R.id.tvBuyerLocation)?.setOnClickListener {
            val act = activity
            if (act is MainActivity) {
                act.setViewPagerItem(3) // Switch to Profile tab in ViewPager
            }
        }
        view.findViewById<ImageButton>(R.id.btnRestaurantFilters)?.setOnClickListener {
            val choices = booleanArrayOf(openOnly, favouritesOnly)
            AlertDialog.Builder(requireContext()).setTitle("Find your next favourite")
                .setMultiChoiceItems(arrayOf("Accepting orders now", "Saved restaurants"), choices) { _, index, checked ->
                    choices[index] = checked
                }.setPositiveButton("Apply") { _, _ ->
                    openOnly = choices[0]; favouritesOnly = choices[1]; filterRestaurants()
                }.setNegativeButton("Cancel", null).show()
        }
        view.findViewById<TextView>(R.id.btnSeeAll)?.setOnClickListener {
            openOnly = false; favouritesOnly = false
            view.findViewById<EditText>(R.id.etRestaurantSearch)?.text?.clear()
            filterRestaurants()
        }
    }

    override fun onStart() {
        super.onStart()
        usersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allRestaurants.clear()
                for (child in snapshot.children) {
                    val user = child.getValue(User::class.java) ?: continue
                    if (user.role.equals(User.ROLE_SELLER, true)) {
                        allRestaurants.add(user.copy(uid = child.key ?: continue))
                    }
                    if (child.key == FirebaseAuth.getInstance().currentUser?.uid) {
                        view?.findViewById<TextView>(R.id.tvBuyerLocation)?.text =
                            user.address.ifBlank { "Set your delivery address" } + "  ⌄"
                    }
                }
                loaded = true; loadError = null
                filterRestaurants()
            }
            override fun onCancelled(error: DatabaseError) {
                loaded = true
                loadError = "Could not load restaurants. Please reopen this page to retry."
                filterRestaurants()
            }
        }
        mealsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                meals.clear()
                snapshot.children.mapNotNullTo(meals) { it.getValue(Meal::class.java) }
                filterRestaurants()
            }
            override fun onCancelled(error: DatabaseError) {
                meals.clear()
                filterRestaurants()
            }
        }
        database.getReference("users").addValueEventListener(usersListener!!)
        database.getReference("menuItems").addValueEventListener(mealsListener!!)
        handler.post(refresh)
    }

    override fun onStop() {
        usersListener?.let { database.getReference("users").removeEventListener(it) }
        mealsListener?.let { database.getReference("menuItems").removeEventListener(it) }
        handler.removeCallbacks(refresh)
        super.onStop()
    }

    private fun filterRestaurants() {
        val view = view ?: return
        if (!::adapter.isInitialized) return
        val query = view.findViewById<EditText>(R.id.etRestaurantSearch)?.text?.toString()?.trim().orEmpty()
        val matchingSellers = meals.filter {
            it.name.contains(query, true) || it.tags.any { tag -> tag.contains(query, true) }
        }.map { it.sellerId }.toSet()
        val favourites = BuyerFavourites.ids(requireContext())
        visibleRestaurants.clear()
        visibleRestaurants.addAll(allRestaurants.filter { user ->
            (query.isBlank() || user.name.contains(query, true) ||
                user.address.contains(query, true) || user.uid in matchingSellers) &&
            (!openOnly || RestaurantAvailability.status(user) == RestaurantAvailability.Status.OPEN) &&
            (!favouritesOnly || user.uid in favourites)
        })
        adapter.notifyDataSetChanged()
        view.findViewById<TextView>(R.id.tvRestaurantHeading)?.text =
            if (query.isNotBlank() || openOnly || favouritesOnly) "Your matches" else "Discover kitchens"
        view.findViewById<TextView>(R.id.tvRestaurantEmpty)?.apply {
            visibility = if (visibleRestaurants.isEmpty() || loadError != null) View.VISIBLE else View.GONE
            text = loadError ?: if (!loaded) "Loading restaurants…" else
                if (allRestaurants.isEmpty()) "No restaurants available yet." else "No restaurants match. Try See all."
        }
    }
}
