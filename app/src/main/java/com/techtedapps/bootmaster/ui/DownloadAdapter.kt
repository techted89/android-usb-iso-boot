package com.techtedapps.bootmaster.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.techtedapps.bootmaster.R
import com.techtedapps.bootmaster.data.IsoItem

class DownloadAdapter(
    private val items: List<IsoItem>,
    private val onDownloadClick: (IsoItem) -> Unit
) : RecyclerView.Adapter<DownloadAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvIsoName)
        val desc: TextView = view.findViewById(R.id.tvIsoDesc)
        val btn: Button = view.findViewById(R.id.btnDownload)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_iso, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.desc.text = item.description

        holder.btn.setOnClickListener { onDownloadClick(item) }
    }

    override fun getItemCount() = items.size
}
