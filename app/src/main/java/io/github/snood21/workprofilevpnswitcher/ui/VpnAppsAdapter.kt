package io.github.snood21.workprofilevpnswitcher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.snood21.workprofilevpnswitcher.R

data class VpnAppItem(
    val packageName: String,
    val appName: String
)

class VpnAppsAdapter(
    private val onRemove: (String) -> Unit
) : ListAdapter<VpnAppItem, VpnAppsAdapter.ViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<VpnAppItem>() {
        override fun areItemsTheSame(old: VpnAppItem, new: VpnAppItem) =
            old.packageName == new.packageName

        override fun areContentsTheSame(old: VpnAppItem, new: VpnAppItem) =
            old == new
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAppName: TextView = view.findViewById(R.id.tv_app_name)
        val tvPackageName: TextView = view.findViewById(R.id.tv_package_name)
        val btnRemove: ImageButton = view.findViewById(R.id.btn_remove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vpn_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.tvAppName.text = item.appName
        holder.tvPackageName.text = item.packageName
        holder.btnRemove.setOnClickListener { onRemove(item.packageName) }
    }
}
