package com.quickdial.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.quickdial.app.databinding.ItemContactPickerBinding

class ContactPickerAdapter(
    private val onContactClick: (Contact) -> Unit
) : ListAdapter<Contact, ContactPickerAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(private val binding: ItemContactPickerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: Contact) {
            binding.tvName.text = contact.name
            binding.tvPhone.text = contact.phone
            binding.tvInitials.text = contact.initials

            if (contact.photoUri != null) {
                Glide.with(binding.root)
                    .load(contact.photoUri)
                    .circleCrop()
                    .placeholder(android.R.color.transparent)
                    .into(binding.ivPhoto)
                binding.ivPhoto.visibility = android.view.View.VISIBLE
                binding.tvInitials.visibility = android.view.View.GONE
            } else {
                binding.ivPhoto.visibility = android.view.View.GONE
                binding.tvInitials.visibility = android.view.View.VISIBLE
            }

            binding.root.setOnClickListener { onContactClick(contact) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContactPickerBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<Contact>() {
        override fun areItemsTheSame(oldItem: Contact, newItem: Contact) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Contact, newItem: Contact) = oldItem == newItem
    }
}
