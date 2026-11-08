package com.dnsleak.test.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import com.dnsleak.test.R
import com.dnsleak.test.service.ServiceDomainRepository
import com.dnsleak.test.service.ServiceSuggestion

class ServiceAutoCompleteAdapter(
    context: Context,
    private val serviceRepo: ServiceDomainRepository
) : ArrayAdapter<ServiceSuggestion>(context, R.layout.item_service_suggestion, R.id.tvSuggestionText) {

    private val suggestions = mutableListOf<ServiceSuggestion>()
    private val inflater = LayoutInflater.from(context)

    override fun getCount(): Int = suggestions.size

    override fun getItem(position: Int): ServiceSuggestion? = suggestions.getOrNull(position)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_service_suggestion, parent, false)
        val item = getItem(position)
        val tvText = view.findViewById<TextView>(R.id.tvSuggestionText)
        tvText?.text = item?.displayText ?: ""
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return getView(position, convertView, parent)
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val filterResults = FilterResults()
                val query = constraint?.toString() ?: ""
                val results = serviceRepo.getSuggestions(query, limit = 20)
                filterResults.values = results
                filterResults.count = results.size
                return filterResults
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                suggestions.clear()
                if (results != null && results.count > 0) {
                    val list = results.values as? List<ServiceSuggestion>
                    if (list != null) {
                        suggestions.addAll(list)
                    }
                }
                notifyDataSetChanged()
            }

            override fun convertResultToString(resultValue: Any?): CharSequence {
                val suggestion = resultValue as? ServiceSuggestion
                return suggestion?.service?.name ?: super.convertResultToString(resultValue)
            }
        }
    }
}
