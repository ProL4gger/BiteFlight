package com.example.biteflight.ui.seller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.biteflight.R
import com.example.biteflight.data.model.Order
import com.example.biteflight.data.model.isCurrentOrder
import com.example.biteflight.data.model.orderStatusLabel
import com.example.biteflight.databinding.ActivitySellerCurrentOrdersBinding
import com.example.biteflight.databinding.ItemSellerCurrentOrderBinding
import com.example.biteflight.voice.VoiceCallActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class SellerCurrentOrdersActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySellerCurrentOrdersBinding
    private val adapter = CurrentOrdersAdapter(::callContact)
    private var ordersRef: DatabaseReference? = null
    private var ordersListener: ValueEventListener? = null
    private var orders = emptyList<Order>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySellerCurrentOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }
        binding.sellerCurrentOrdersToolbar.setNavigationOnClickListener { finish() }
        binding.sellerCurrentOrdersList.layoutManager = LinearLayoutManager(this)
        binding.sellerCurrentOrdersList.adapter = adapter
    }

    override fun onStart() {
        super.onStart()
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            finish()
            return
        }
        render(emptyList(), R.string.seller_current_orders_loading)
        val ref = FirebaseDatabase.getInstance().getReference("orders").child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (ordersListener !== this) return
                render(snapshot.children.mapNotNull { it.getValue(Order::class.java) }
                    .filter { it.sellerId == uid && isCurrentOrder(it.status) }
                    .sortedByDescending { it.createdAt })
            }

            override fun onCancelled(error: DatabaseError) {
                if (ordersListener === this) render(emptyList(), R.string.seller_current_orders_error)
            }
        }
        ordersRef = ref
        ordersListener = listener
        ref.addValueEventListener(listener)
    }

    override fun onStop() {
        ordersListener?.let { ordersRef?.removeEventListener(it) }
        ordersListener = null
        ordersRef = null
        super.onStop()
    }

    private fun render(activeOrders: List<Order>, message: Int = R.string.seller_no_current_orders) {
        orders = activeOrders
        adapter.submitList(activeOrders)
        binding.sellerCurrentOrdersList.isVisible = activeOrders.isNotEmpty()
        binding.sellerCurrentOrdersMessage.isVisible = activeOrders.isEmpty()
        binding.sellerCurrentOrdersMessage.setText(message)
    }

    private fun callContact(order: Order, rider: Boolean) {
        if (orders.none { it == order } || order.id.isBlank()) return
        val recipient = if (rider) order.riderId else order.buyerId
        if (recipient.isBlank()) return
        val name = getString(if (rider) R.string.seller_call_contact_rider else R.string.seller_call_contact_buyer)

        VoiceCallActivity.startOrderCall(this, order.sellerId, order.id, recipient, name)
    }

    private class CurrentOrdersAdapter(
        private val callContact: (Order, Boolean) -> Unit
    ) : ListAdapter<Order, CurrentOrdersAdapter.Holder>(object : DiffUtil.ItemCallback<Order>() {
        override fun areItemsTheSame(oldItem: Order, newItem: Order) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Order, newItem: Order) = oldItem == newItem
    }) {
        class Holder(val binding: ItemSellerCurrentOrderBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = ItemSellerCurrentOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return Holder(binding)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) = with(holder.binding) {
            val order = getItem(position)
            val context = root.context
            sellerOrderId.text = context.getString(R.string.seller_current_order_id, order.id.takeLast(6).uppercase())
            sellerCurrentOrderStatus.text = orderStatusLabel(order.status)
            sellerCurrentOrderItems.text = order.items.joinToString("\n") {
                context.getString(R.string.current_order_item, it.quantity, it.name)
            }
            sellerCallBuyer.isEnabled = order.buyerId.isNotBlank()
            sellerCallRider.isEnabled = order.riderId.isNotBlank()
            sellerCurrentOrderRiderPending.isVisible = order.riderId.isBlank()
            sellerCallBuyer.setOnClickListener { callContact(order, false) }
            sellerCallRider.setOnClickListener { callContact(order, true) }
        }
    }
}
