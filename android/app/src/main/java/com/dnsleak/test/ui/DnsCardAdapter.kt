package com.dnsleak.test.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.dnsleak.test.R
import com.dnsleak.test.databinding.ItemDnsCardBinding
import com.dnsleak.test.model.DnsServerItem
import com.dnsleak.test.util.CountryUtils

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
            val context = binding.root.context
            binding.tvDnsIp.text = item.ip

            // 1. Resolve country and flag
            val rawCountry = item.country.trim()
            val rawCode = item.countryCode.trim()

            val resolvedCountry = when {
                rawCountry.isNotBlank() -> rawCountry
                rawCode.isNotBlank() -> rawCode
                item.isp.contains(" - ") -> CountryUtils.parseGeoString(item.isp).first
                else -> ""
            }

            val code = if (rawCode.length == 2) rawCode.uppercase() else CountryUtils.getCountryCode(resolvedCountry)
            val flag = CountryUtils.countryCodeToEmoji(code)

            val displayCountry = resolvedCountry.ifBlank {
                if (code.isNotBlank()) code else "DNS Server"
            }
            val codeSuffix = if (code.isNotBlank() && !displayCountry.equals(code, ignoreCase = true)) " ($code)" else ""

            val countryText = if (flag.isNotEmpty()) {
                "$flag $displayCountry$codeSuffix"
            } else {
                "$displayCountry$codeSuffix"
            }

            binding.tvDnsCountry.text = countryText

            // Style badge based on leak / warning / safe status
            when {
                item.isLeak -> {
                    binding.tvDnsCountry.setBackgroundResource(R.drawable.badge_leak_bg)
                    binding.tvDnsCountry.setTextColor(ContextCompat.getColor(context, R.color.status_leak_text))
                }
                item.isWarning || item.isIspDns || CountryUtils.isRussian(resolvedCountry, item.isp) -> {
                    binding.tvDnsCountry.setBackgroundResource(R.drawable.badge_warning_bg)
                    binding.tvDnsCountry.setTextColor(ContextCompat.getColor(context, R.color.status_warning_text))
                }
                else -> {
                    binding.tvDnsCountry.setBackgroundResource(R.drawable.badge_safe_bg)
                    binding.tvDnsCountry.setTextColor(ContextCompat.getColor(context, R.color.status_safe_text))
                }
            }

            // 2. Resolve ISP line
            val resolvedIsp = if (item.isp.contains(" - ") && item.country.isBlank()) {
                CountryUtils.parseGeoString(item.isp).second.ifBlank { item.isp }
            } else {
                item.isp.ifBlank { "DNS Resolver" }
            }
            binding.tvDnsIsp.text = resolvedIsp
        }
    }
}
