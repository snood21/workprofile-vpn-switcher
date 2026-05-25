package io.github.snood21.workprofilevpnswitcher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.snood21.workprofilevpnswitcher.R
import io.github.snood21.workprofilevpnswitcher.util.LanguageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Экран выбора VPN-приложения.
 * Показывает только приложения зарегистрировавшие VpnService —
 * те же что видны в системном меню Настройки → VPN.
 */
class SelectAppActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }

    private lateinit var etSearch: EditText
    private lateinit var rvApps: RecyclerView
    private lateinit var adapter: AppListAdapter
    private var allApps: List<AppListItem> = emptyList()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageUtils.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_app)
        title = getString(R.string.select_vpn_app)

        etSearch = findViewById(R.id.et_search)
        rvApps = findViewById(R.id.rv_apps)

        adapter = AppListAdapter { item ->
            setResult(RESULT_OK, Intent().apply {
                putExtra(EXTRA_PACKAGE_NAME, item.packageName)
            })
            finish()
        }

        rvApps.layoutManager = LinearLayoutManager(this)
        rvApps.adapter = adapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = filterApps(s?.toString() ?: "")
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        loadVpnApps()
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun loadVpnApps() {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val vpnIntent = Intent("android.net.VpnService")
                val vpnPackages = packageManager.queryIntentServices(vpnIntent, 0)
                    .map { it.serviceInfo.packageName }
                    .distinct()

                vpnPackages.mapNotNull { pkg ->
                    try {
                        val info = packageManager.getApplicationInfo(pkg, 0)
                        AppListItem(
                            packageName = pkg,
                            appName = packageManager.getApplicationLabel(info).toString()
                        )
                    } catch (e: Exception) { null }
                }.sortedBy { it.appName }
            }

            allApps = apps
            adapter.submitList(apps)
        }
    }

    private fun filterApps(query: String) {
        val filtered = if (query.isBlank()) allApps
        else allApps.filter {
            it.appName.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
        }
        adapter.submitList(filtered)
    }
}

data class AppListItem(val packageName: String, val appName: String)

class AppListAdapter(
    private val onClick: (AppListItem) -> Unit
) : ListAdapter<AppListItem, AppListAdapter.ViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<AppListItem>() {
        override fun areItemsTheSame(old: AppListItem, new: AppListItem) =
            old.packageName == new.packageName
        override fun areContentsTheSame(old: AppListItem, new: AppListItem) = old == new
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAppName: TextView = view.findViewById(R.id.tv_app_name)
        val tvPackageName: TextView = view.findViewById(R.id.tv_package_name)
        init { view.setOnClickListener { onClick(getItem(bindingAdapterPosition)) } }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_app_select, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.tvAppName.text = item.appName
        holder.tvPackageName.text = item.packageName
    }
}
