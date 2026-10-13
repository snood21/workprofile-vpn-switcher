package io.github.snood21.workprofilevpnswitcher.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.snood21.workprofilevpnswitcher.R
import io.github.snood21.workprofilevpnswitcher.service.VpnMonitorService
import io.github.snood21.workprofilevpnswitcher.util.AppSettings
import io.github.snood21.workprofilevpnswitcher.util.LanguageUtils
import io.github.snood21.workprofilevpnswitcher.util.Logger
import io.github.snood21.workprofilevpnswitcher.util.WorkProfileManager

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var settings: AppSettings
    private lateinit var workProfileManager: WorkProfileManager

    private lateinit var switchMonitoring: SwitchCompat
    private lateinit var switchMonitorProfile: SwitchCompat
    private lateinit var switchRestoreState: SwitchCompat
    private lateinit var switchDisableOnProfileEnable: SwitchCompat

    private lateinit var spinnerListMode: Spinner
    private lateinit var tvListModeDesc: TextView

    private lateinit var switchPolling: SwitchCompat
    private lateinit var etPollInterval: EditText
    private lateinit var pollIntervalContainer: View
    private lateinit var restoreStateContainer: View
    private lateinit var monitorProfileEnableContainer: View
    private lateinit var disableOnProfileEnableContainer: View

    private lateinit var btnInterfaceSettings: Button
    private lateinit var btnLoggingSettings: Button
    private lateinit var btnAbout: Button

    private lateinit var btnAddApp: Button
    private lateinit var rvVpnApps: RecyclerView
    private lateinit var tvPermissionWarning: TextView
    private lateinit var tvWorkProfileWarning: TextView
    private lateinit var tvVpnStatus: TextView
    private lateinit var vpnAppsAdapter: VpnAppsAdapter

    private val connectivityManager by lazy {
        getSystemService(ConnectivityManager::class.java)
    }

    // Текущая VPN-сеть (в Android одновременно существует только одна). Нужна, чтобы запоздавший
    // onLost прежней сети (при смене VPN-приложения) не сбросил статус новой сети.
    // Читается и меняется только в UI-потоке.
    private var currentVpn: Network? = null

    private val vpnNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread {
                currentVpn = network
                updateVpnStatus(true)
            }
        }
        override fun onLost(network: Network) {
            runOnUiThread {
                if (network == currentVpn) {
                    currentVpn = null
                    updateVpnStatus(false)
                }
            }
        }
    }

    // Данные для Spinner режима списка
    // index 0 = denylist (по умолчанию), index 1 = allowlist
    private val listModeItems by lazy {
        listOf(
            getString(R.string.list_mode_denylist),
            getString(R.string.list_mode_allowlist)
        )
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageUtils.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = AppSettings(this)
        workProfileManager = WorkProfileManager(this)

        initViews()
        loadState()
        checkWarnings()
        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        checkWarnings()
        // Пока Activity была на паузе, колбэк не работал — состояние определяется заново
        currentVpn = null
        registerVpnCallback()
        // Принудительная проверка текущего состояния VPN,
        // на случай если NetworkCallback не сработает
        //Альтернативы для получения всех текущих сетей без callback нет в публичном API
        @Suppress("DEPRECATION")
        val vpnNetwork = connectivityManager.allNetworks.firstOrNull { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
        currentVpn = vpnNetwork
        updateVpnStatus(vpnNetwork != null)
    }

    override fun onPause() {
        super.onPause()
        savePollInterval()
        try { connectivityManager.unregisterNetworkCallback(vpnNetworkCallback) }
        catch (e: IllegalArgumentException) { }
    }

    private fun initViews() {
        switchMonitoring = findViewById(R.id.switch_monitoring)
        switchMonitorProfile = findViewById(R.id.switch_monitor_profile_enable)
        switchRestoreState = findViewById(R.id.switch_restore_state)
        switchDisableOnProfileEnable = findViewById(R.id.switch_disable_on_profile_enable)
        spinnerListMode = findViewById(R.id.spinner_list_mode)
        tvListModeDesc = findViewById(R.id.tv_list_mode_desc)
        switchPolling = findViewById(R.id.switch_polling)
        etPollInterval = findViewById(R.id.et_poll_interval)
        pollIntervalContainer = findViewById(R.id.poll_interval_container)
        restoreStateContainer = findViewById(R.id.restore_state_container)
        monitorProfileEnableContainer = findViewById(R.id.monitor_profile_enable_container)
        disableOnProfileEnableContainer = findViewById(R.id.disable_on_profile_enable_container)
        btnInterfaceSettings = findViewById(R.id.btn_interface_settings)
        btnLoggingSettings = findViewById(R.id.btn_logging_settings)
        btnAbout = findViewById(R.id.btn_about)
        btnAddApp = findViewById(R.id.btn_add_app)
        rvVpnApps = findViewById(R.id.rv_vpn_apps)
        tvPermissionWarning = findViewById(R.id.tv_permission_warning)
        tvWorkProfileWarning = findViewById(R.id.tv_work_profile_warning)
        tvVpnStatus = findViewById(R.id.tv_vpn_status)

        vpnAppsAdapter = VpnAppsAdapter(
            onRemove = { pkg -> settings.removeVpnPackage(pkg); refreshAppList() }
        )
        rvVpnApps.layoutManager = LinearLayoutManager(this)
        rvVpnApps.adapter = vpnAppsAdapter
    }

    private fun setupListeners() {
        switchMonitoring.setOnCheckedChangeListener { _, isChecked ->
            settings.monitoringEnabled = isChecked
            if (isChecked) startForegroundService(VpnMonitorService.startIntent(this))
            else startService(VpnMonitorService.stopIntent(this))
            restoreStateContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            monitorProfileEnableContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            disableOnProfileEnableContainer.visibility =
                if (isChecked && settings.monitorProfileEnable) View.VISIBLE else View.GONE
        }

        switchRestoreState.setOnCheckedChangeListener { _, isChecked ->
            settings.restoreProfileState = isChecked
        }

        switchMonitorProfile.setOnCheckedChangeListener { _, isChecked ->
            settings.monitorProfileEnable = isChecked
            disableOnProfileEnableContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        switchDisableOnProfileEnable.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                requestStealthVpnConsent()
            } else {
                settings.disableVpnOnProfileEnable = false
            }
        }

        // Spinner: адаптер и listener инициализируются вместе
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listModeItems)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerListMode.adapter = spinnerAdapter
        spinnerListMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                val isAllowlist = pos == 1
                settings.listModeAllowlist = isAllowlist
                updateListModeDesc(isAllowlist)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        switchPolling.setOnCheckedChangeListener { _, isChecked ->
            savePollInterval()
            settings.pollingEnabled = isChecked
            pollIntervalContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (settings.monitoringEnabled) {
                startForegroundService(VpnMonitorService.startIntent(this))
            }
        }

        btnInterfaceSettings.setOnClickListener {
            startActivity(Intent(this, InterfaceSettingsActivity::class.java))
        }

        btnLoggingSettings.setOnClickListener {
            startActivity(Intent(this, LoggingSettingsActivity::class.java))
        }

        btnAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        btnAddApp.setOnClickListener {
            selectAppLauncher.launch(Intent(this, SelectAppActivity::class.java))
        }
    }

    private fun loadState() {
        // Отключить listeners на время загрузки
        switchMonitoring.setOnCheckedChangeListener(null)
        switchMonitorProfile.setOnCheckedChangeListener(null)
        switchRestoreState.setOnCheckedChangeListener(null)
        switchDisableOnProfileEnable.setOnCheckedChangeListener(null)
        switchPolling.setOnCheckedChangeListener(null)

        switchMonitoring.isChecked = settings.monitoringEnabled
        switchMonitorProfile.isChecked = settings.monitorProfileEnable
        switchRestoreState.isChecked = settings.restoreProfileState
        switchDisableOnProfileEnable.isChecked = settings.disableVpnOnProfileEnable

        monitorProfileEnableContainer.visibility =
            if (settings.monitoringEnabled) View.VISIBLE else View.GONE
        restoreStateContainer.visibility =
            if (settings.monitoringEnabled) View.VISIBLE else View.GONE
        disableOnProfileEnableContainer.visibility =
            if (settings.monitoringEnabled && settings.monitorProfileEnable) View.VISIBLE else View.GONE

        // index 0 = denylist (по умолчанию), index 1 = allowlist
        spinnerListMode.setSelection(if (settings.listModeAllowlist) 1 else 0)
        updateListModeDesc(settings.listModeAllowlist)

        switchPolling.isChecked = settings.pollingEnabled
        etPollInterval.setText((settings.pollIntervalMs / 1000).toString())
        pollIntervalContainer.visibility = if (settings.pollingEnabled) View.VISIBLE else View.GONE

        refreshAppList()
        setupListeners()
    }

    private fun updateListModeDesc(isAllowlist: Boolean) {
        tvListModeDesc.text = getString(
            if (isAllowlist) R.string.desc_list_mode_allowlist
            else R.string.desc_list_mode_denylist
        )
    }

    private fun savePollInterval() {
        when (val secs = etPollInterval.text.toString().toLongOrNull()) {
            null -> {
                etPollInterval.error = getString(R.string.error_invalid_number)
            }
            !in 1..3600 -> {
                etPollInterval.setText((settings.pollIntervalMs / 1000).toString())
                etPollInterval.error = getString(R.string.error_poll_interval_range)
            }
            else -> {
                settings.pollIntervalMs = secs * 1000
                etPollInterval.error = null
            }
        }
    }

    private fun checkWarnings() {
        tvWorkProfileWarning.visibility =
            if (!workProfileManager.hasWorkProfile()) View.VISIBLE else View.GONE
        tvWorkProfileWarning.text = getString(R.string.warning_no_work_profile)

        if (!workProfileManager.hasRequiredPermission()) {
            tvPermissionWarning.visibility = View.VISIBLE
            tvPermissionWarning.text = getString(R.string.warning_no_permission)
        } else {
            tvPermissionWarning.visibility = View.GONE
        }
    }

    private fun refreshAppList() {
        val items = settings.getVpnPackages().map { pkg ->
            val appName = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) { pkg }
            VpnAppItem(packageName = pkg, appName = appName)
        }.sortedBy { it.appName }

        vpnAppsAdapter.submitList(items)
        findViewById<TextView>(R.id.tv_empty_list).visibility =
            if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun registerVpnCallback() {
        // Как и в VpnMonitorService: у сетей с TRANSPORT_VPN нет NOT_VPN, поэтому
        // это требование (если оно включено в запрос по умолчанию) нужно снять.
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        try {
            connectivityManager.registerNetworkCallback(request, vpnNetworkCallback)
        } catch (e: RuntimeException) {
            // SecurityException, IllegalArgumentException (колбэк уже зарегистрирован),
            // превышение лимита запросов сети — статус при этом остаётся по запасной проверке
            Logger.w(TAG) { "Failed to register VPN network callback: ${e.message}" }
        }
    }

    private fun updateVpnStatus(active: Boolean) {
        tvVpnStatus.text = getString(if (active) R.string.vpn_status_active else R.string.vpn_status_inactive)
        tvVpnStatus.setTextColor(getColor(if (active) R.color.status_active else R.color.status_inactive))
    }

    // Результат не используется: без разрешения служба работает, просто уведомление не видно
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val selectAppLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra(SelectAppActivity.EXTRA_PACKAGE_NAME)?.let { pkg ->
                settings.addVpnPackage(pkg)
                refreshAppList()
                Toast.makeText(this, getString(R.string.app_added), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Запросить у пользователя согласие VpnService.prepare(), необходимое
     * для работы StealthVpnService. Диалог показывается только один раз —
     * если согласие уже дано ранее, prepare() вернёт null и включение
     * настройки происходит без дополнительного взаимодействия.
     *
     * Переключатель включается только по факту полученного согласия —
     * если пользователь отклонит системный диалог, switchDisableOnProfileEnable
     * возвращается в выключенное состояние.
     */
    private fun requestStealthVpnConsent() {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent == null) {
            settings.disableVpnOnProfileEnable = true
            return
        }
        stealthVpnConsentLauncher.launch(prepareIntent)
    }

    private val stealthVpnConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            settings.disableVpnOnProfileEnable = true
        } else {
            Logger.w(TAG) { "VpnService.prepare() consent denied by user" }
            switchDisableOnProfileEnable.isChecked = false
            settings.disableVpnOnProfileEnable = false
            Toast.makeText(this, getString(R.string.stealth_vpn_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }
}
