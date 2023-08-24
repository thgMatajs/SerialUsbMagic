package io.poc.serialusbmagic

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class DeviceAdapter (
    private val itemClick: (item: ListItem) -> Unit,
) : ListAdapter<ListItem, DeviceViewHolder>(DeviceDiffUtil()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_list_layout, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(
            getItem(position),
            itemClick
        )
    }
}

class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {

    private val device = itemView.findViewById<TextView>(R.id.deviceItem)

    fun bind(item: ListItem,  itemClick: (item: ListItem) -> Unit) {
        device.apply {
            text = "DEVICE NAME: ${item.device.deviceName}, \n DRIVER: ${item.driver.toString()}, \n PORT:${item.port}"

            setOnClickListener { itemClick(item) }
        }
    }
}

class DeviceDiffUtil : DiffUtil.ItemCallback<ListItem>() {
    override fun areItemsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
        return oldItem.port == newItem.port
    }

    override fun areContentsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
        return oldItem == newItem
    }
}