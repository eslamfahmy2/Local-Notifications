package com.example.localnotifications.ui.list

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.localnotifications.R
import com.example.localnotifications.core.domain.model.NotificationItem
import com.example.localnotifications.databinding.ItemNotificationBinding

class NotificationAdapter(
    private val onItemClick: (Int) -> Unit,
) : ListAdapter<NotificationItem, NotificationAdapter.ViewHolder>(NotificationDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class ViewHolder(
        private val binding: ItemNotificationBinding,
        onItemClick: (Int) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        private var item: NotificationItem? = null

        init {
            binding.root.setOnClickListener { item?.let { onItemClick(it.id) } }
        }

        fun bind(item: NotificationItem) {
            this.item = item
            val resources = binding.root.resources
            val delay = resources.getQuantityString(R.plurals.seconds, item.delaySeconds, item.delaySeconds)
            binding.title.text = item.title
            binding.subtitle.text = resources.getString(R.string.item_delay, delay)
        }
    }
}

object NotificationDiffCallback : DiffUtil.ItemCallback<NotificationItem>() {
    override fun areItemsTheSame(oldItem: NotificationItem, newItem: NotificationItem) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: NotificationItem, newItem: NotificationItem) = oldItem == newItem
}
