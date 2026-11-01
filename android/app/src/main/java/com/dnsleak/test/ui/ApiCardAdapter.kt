package com.dnsleak.test.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.dnsleak.test.R
import com.dnsleak.test.databinding.ItemApiCardBinding
import com.dnsleak.test.model.ApiAvailabilityResult
import com.dnsleak.test.model.ApiAvailabilityState
import com.dnsleak.test.model.DnsProvider

class ApiCardAdapter(
    private val providers: List<DnsProvider>,
    private var selectedId: String,
    private val onSelect: (DnsProvider) -> Unit
) : RecyclerView.Adapter<ApiCardAdapter.ApiViewHolder>() {

    private val availabilityMap = mutableMapOf<String, ApiAvailabilityResult>()

    fun setSelected(id: String) {
        selectedId = id
        notifyDataSetChanged()
    }

    fun setAvailabilityResult(providerId: String, result: ApiAvailabilityResult) {
        availabilityMap[providerId] = result
        val index = providers.indexOfFirst { it.id == providerId }
        if (index != -1) {
            notifyItemChanged(index)
        }
    }

    fun setAllChecking() {
        for (p in providers) {
            availabilityMap[p.id] = ApiAvailabilityResult(state = ApiAvailabilityState.CHECKING)
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ApiViewHolder {
        val binding =
            ItemApiCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ApiViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ApiViewHolder, position: Int) {
        holder.bind(providers[position], providers[position].id == selectedId)
    }

    override fun getItemCount(): Int = providers.size

    inner class ApiViewHolder(private val binding: ItemApiCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(provider: DnsProvider, isSelected: Boolean) {
            val context = binding.root.context

            binding.tvApiName.text = provider.name
            binding.tvApiUrl.text = provider.url
            binding.tvApiDescription.text = provider.description
            binding.tvApiMethod.text = provider.methodDetails
            binding.tvApiRuAvailability.text = provider.ruAvailability

            if (isSelected) {
                binding.cardApi.strokeColor = ContextCompat.getColor(context, R.color.primary)
                binding.cardApi.strokeWidth = 2
                binding.cardApi.setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.surface_variant)
                )
                binding.tvApiBadge.text = "АКТИВЕН"
                binding.tvApiBadge.background =
                    ContextCompat.getDrawable(context, R.drawable.badge_safe_bg)
                binding.tvApiBadge.setTextColor(
                    ContextCompat.getColor(context, R.color.status_safe_text)
                )
            } else {
                binding.cardApi.strokeColor = ContextCompat.getColor(context, R.color.divider)
                binding.cardApi.strokeWidth = 1
                binding.cardApi.setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.surface)
                )
                binding.tvApiBadge.text = "ВЫБРАТЬ"
                binding.tvApiBadge.background =
                    ContextCompat.getDrawable(context, R.drawable.badge_neutral_bg)
                binding.tvApiBadge.setTextColor(
                    ContextCompat.getColor(context, R.color.on_surface_variant)
                )
            }

            val avail = availabilityMap[provider.id]
            if (avail == null || avail.state == ApiAvailabilityState.NOT_CHECKED) {
                binding.tvApiStatusBadge.visibility = View.GONE
            } else {
                binding.tvApiStatusBadge.visibility = View.VISIBLE
                when (avail.state) {
                    ApiAvailabilityState.CHECKING -> {
                        binding.tvApiStatusBadge.text = "ПРОВЕРКА..."
                        binding.tvApiStatusBadge.background =
                            ContextCompat.getDrawable(context, R.drawable.badge_neutral_bg)
                        binding.tvApiStatusBadge.setTextColor(
                            ContextCompat.getColor(context, R.color.on_surface_variant)
                        )
                    }
                    ApiAvailabilityState.AVAILABLE -> {
                        binding.tvApiStatusBadge.text = "ДОСТУПЕН (${avail.pingMs} ms)"
                        binding.tvApiStatusBadge.background =
                            ContextCompat.getDrawable(context, R.drawable.badge_safe_bg)
                        binding.tvApiStatusBadge.setTextColor(
                            ContextCompat.getColor(context, R.color.status_safe_text)
                        )
                    }
                    ApiAvailabilityState.BLOCKED -> {
                        binding.tvApiStatusBadge.text = "НЕДОСТУПЕН / БЛОК"
                        binding.tvApiStatusBadge.background =
                            ContextCompat.getDrawable(context, R.drawable.badge_leak_bg)
                        binding.tvApiStatusBadge.setTextColor(
                            ContextCompat.getColor(context, R.color.status_leak_text)
                        )
                    }
                    else -> {
                        binding.tvApiStatusBadge.visibility = View.GONE
                    }
                }
            }

            binding.cardApi.setOnClickListener {
                onSelect(provider)
            }
        }
    }
}
