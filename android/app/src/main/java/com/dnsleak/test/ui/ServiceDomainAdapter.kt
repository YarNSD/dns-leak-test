package com.dnsleak.test.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.dnsleak.test.databinding.ItemServiceDomainBinding

class ServiceDomainAdapter(
    private var domains: List<String> = emptyList(),
    private val onItemClick: ((String) -> Unit)? = null
) : RecyclerView.Adapter<ServiceDomainAdapter.DomainViewHolder>() {

    fun updateDomains(newDomains: List<String>) {
        domains = newDomains
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DomainViewHolder {
        val binding = ItemServiceDomainBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DomainViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DomainViewHolder, position: Int) {
        holder.bind(domains[position], position + 1)
    }

    override fun getItemCount(): Int = domains.size

    inner class DomainViewHolder(private val binding: ItemServiceDomainBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(domain: String, index: Int) {
            binding.tvDomainIndex.text = "$index."
            binding.tvDomainName.text = domain
            binding.root.setOnClickListener {
                onItemClick?.invoke(domain)
            }
        }
    }
}
