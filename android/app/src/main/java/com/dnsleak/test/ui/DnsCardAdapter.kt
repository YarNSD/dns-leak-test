package com.dnsleak.test.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.dnsleak.test.databinding.ItemDnsCardBinding
import com.dnsleak.test.model.DnsServerItem

class DnsCardAdapter(private var items: List<DnsServerItem> = emptyList()) :
    RecyclerView.Adapter<DnsCardAdapter.DnsViewHolder>() {

    fun updateData(newItems: List<DnsServerItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DnsViewHolder {
        val binding =
            ItemDnsCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DnsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DnsViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class DnsViewHolder(private val binding: ItemDnsCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DnsServerItem) {
            binding.tvDnsIp.text = item.ip

            val flag = getCountryFlagEmoji(item.countryCode, item.country)
            val countryCodeSuffix = if (item.countryCode.isNotBlank()) " (${item.countryCode.lowercase()})" else ""
            val countryText = if (flag.isNotEmpty()) {
                "$flag ${item.country}$countryCodeSuffix"
            } else {
                "${item.country}$countryCodeSuffix"
            }

            binding.tvDnsCountry.text = countryText
            binding.tvDnsIsp.text = item.isp
        }

        private fun getCountryFlagEmoji(code: String?, countryName: String?): String {
            if (!code.isNullOrBlank() && code.trim().length == 2) {
                val upper = code.trim().uppercase()
                if (upper[0] in 'A'..'Z' && upper[1] in 'A'..'Z') {
                    val first = Character.toChars(0x1F1E6 + (upper[0] - 'A'))
                    val second = Character.toChars(0x1F1E6 + (upper[1] - 'A'))
                    return String(first) + String(second)
                }
            }

            val lower = countryName?.lowercase() ?: ""
            return when {
                lower.contains("russia") || lower.contains("россия") -> "🇷🇺"
                lower.contains("sweden") || lower.contains("швеция") -> "🇸🇪"
                lower.contains("germany") || lower.contains("германия") -> "🇩🇪"
                lower.contains("netherlands") || lower.contains("нидерланды") -> "🇳🇱"
                lower.contains("united states") || lower.contains("usa") || lower.contains("сша") -> "🇺🇸"
                lower.contains("finland") || lower.contains("финляндия") -> "🇫🇮"
                lower.contains("france") || lower.contains("франция") -> "🇫🇷"
                lower.contains("united kingdom") || lower.contains("великобритания") -> "🇬🇧"
                lower.contains("switzerland") || lower.contains("швейцария") -> "🇨🇭"
                lower.contains("austria") || lower.contains("австрия") -> "🇦🇹"
                lower.contains("poland") || lower.contains("польша") -> "🇵🇱"
                lower.contains("estonia") || lower.contains("эстония") -> "🇪🇪"
                lower.contains("latvia") || lower.contains("латвия") -> "🇱🇻"
                lower.contains("lithuania") || lower.contains("литва") -> "🇱🇹"
                lower.contains("canada") || lower.contains("канада") -> "🇨🇦"
                lower.contains("japan") || lower.contains("япония") -> "🇯🇵"
                lower.contains("singapore") || lower.contains("сингапур") -> "🇸🇬"
                lower.contains("turkey") || lower.contains("турция") -> "🇹🇷"
                lower.contains("kazakhstan") || lower.contains("казахстан") -> "🇰🇿"
                else -> ""
            }
        }
    }
}
