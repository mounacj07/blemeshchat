package com.example.meshchat.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.meshchat.R
import com.example.meshchat.data.db.NodeEntity

class DeviceAdapter(private val onClick: (NodeEntity) -> Unit) :
    ListAdapter<NodeEntity, DeviceAdapter.DeviceViewHolder>(DeviceDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = getItem(position)
        holder.bind(device)
    }

    class DeviceViewHolder(itemView: View, val onClick: (NodeEntity) -> Unit) :
        RecyclerView.ViewHolder(itemView) {
        private val deviceNameTextView: TextView = itemView.findViewById(R.id.text_device_name)
        private val userNameTextView: TextView = itemView.findViewById(R.id.text_user_name)
        private var currentNode: NodeEntity? = null

        init {
            itemView.setOnClickListener {
                currentNode?.let {
                    onClick(it)
                }
            }
        }

        fun bind(node: NodeEntity) {
            currentNode = node
            // FIX: Display the user-entered name in the second text view
            deviceNameTextView.text = node.name
            userNameTextView.text = "ID: ${node.nodeId}" // Display the node ID for clarity
        }
    }
}

object DeviceDiffCallback : DiffUtil.ItemCallback<NodeEntity>() {
    override fun areItemsTheSame(oldItem: NodeEntity, newItem: NodeEntity): Boolean {
        return oldItem.nodeId == newItem.nodeId
    }

    override fun areContentsTheSame(oldItem: NodeEntity, newItem: NodeEntity): Boolean {
        return oldItem == newItem
    }
}
