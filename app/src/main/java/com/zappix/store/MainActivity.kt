package com.zappix.store

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.launch

enum class InstallState { NOT_INSTALLED, INSTALLED, UPDATE }
enum class StoreSection { FREE, SUBSCRIPTION, ADULT, TOOLS, UPDATES }

class MainActivity : ComponentActivity() {
    private val vm: StoreViewModel by viewModels()

    private lateinit var adapter: AppAdapter
    private lateinit var appsRecycler: RecyclerView
    private lateinit var loading: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var sectionTitle: TextView
    private lateinit var freeTab: TextView
    private lateinit var subscriptionTab: TextView
    private lateinit var adultTab: TextView
    private lateinit var toolsTab: TextView
    private lateinit var updatesTab: TextView
    private lateinit var mainContent: View
    private lateinit var detailsOverlay: FrameLayout
    private lateinit var detailArtworkFull: ImageView
    private lateinit var detailNameFull: TextView
    private lateinit var detailTypeFull: TextView
    private lateinit var detailDescriptionFull: TextView
    private lateinit var detailErrorFull: TextView
    private lateinit var installFullButton: Button
    private lateinit var uninstallFullButton: Button
    private lateinit var backFullButton: Button

    private val detailBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            closeFullDetails()
        }
    }

    private var allApps: List<StoreApp> = emptyList()
    private var currentSection = StoreSection.FREE
    private var focusedApp: StoreApp? = null
    private lateinit var installer: ApkInstaller

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        installer = ApkInstaller(applicationContext)
        appsRecycler = findViewById(R.id.appsRecycler)
        loading = findViewById(R.id.loading)
        errorText = findViewById(R.id.errorText)
        sectionTitle = findViewById(R.id.sectionTitle)
        freeTab = findViewById(R.id.freeTab)
        subscriptionTab = findViewById(R.id.subscriptionTab)
        adultTab = findViewById(R.id.adultTab)
        toolsTab = findViewById(R.id.toolsTab)
        updatesTab = findViewById(R.id.updatesTab)
        mainContent = findViewById(R.id.mainContent)
        detailsOverlay = findViewById(R.id.detailsOverlay)
        detailArtworkFull = findViewById(R.id.detailArtworkFull)
        detailNameFull = findViewById(R.id.detailNameFull)
        detailTypeFull = findViewById(R.id.detailTypeFull)
        detailDescriptionFull = findViewById(R.id.detailDescriptionFull)
        detailErrorFull = findViewById(R.id.detailErrorFull)
        installFullButton = findViewById(R.id.installFullButton)
        uninstallFullButton = findViewById(R.id.uninstallFullButton)
        backFullButton = findViewById(R.id.backFullButton)
        onBackPressedDispatcher.addCallback(this, detailBackCallback)

        adapter = AppAdapter(
            onFocused = { app ->
                focusedApp = app
            },
            onClicked = { app ->
                focusedApp = app
                openFullDetails(app)
            },
            installState = ::installState
        )

        appsRecycler.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false)
            adapter = this@MainActivity.adapter
            setHasFixedSize(true)
            itemAnimator = null
            setItemViewCacheSize(12)
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFocusable = false
            descendantFocusability = RecyclerView.FOCUS_AFTER_DESCENDANTS
        }

        freeTab.setOnClickListener { showSection(StoreSection.FREE) }
        subscriptionTab.setOnClickListener { showSection(StoreSection.SUBSCRIPTION) }
        adultTab.setOnClickListener { showSection(StoreSection.ADULT) }
        toolsTab.setOnClickListener { showSection(StoreSection.TOOLS) }
        updatesTab.setOnClickListener { showSection(StoreSection.UPDATES) }

        freeTab.nextFocusDownId = R.id.appsRecycler
        subscriptionTab.nextFocusDownId = R.id.appsRecycler
        adultTab.nextFocusDownId = R.id.appsRecycler
        toolsTab.nextFocusDownId = R.id.appsRecycler
        updatesTab.nextFocusDownId = R.id.appsRecycler
        findViewById<View>(R.id.refreshButton).setOnClickListener { vm.refresh() }

        installFullButton.setOnClickListener {
            focusedApp?.let { app ->
                when (installState(app)) {
                    InstallState.INSTALLED -> openInstalledApp(app)
                    InstallState.NOT_INSTALLED, InstallState.UPDATE ->
                        startInstall(app, installFullButton, detailErrorFull)
                }
            }
        }
        uninstallFullButton.setOnClickListener {
            focusedApp?.let { uninstallInstalledApp(it) }
        }
        backFullButton.setOnClickListener { closeFullDetails() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { state ->
                    loading.visibility = if (state.loading) View.VISIBLE else View.GONE
                    errorText.visibility = if (state.error != null) View.VISIBLE else View.GONE
                    errorText.text = state.error.orEmpty()
                    if (!state.loading && state.error == null) {
                        allApps = state.apps
                        refreshDynamicTabs()
                        if (currentSection == StoreSection.ADULT && adultTab.visibility != View.VISIBLE) {
                            currentSection = StoreSection.FREE
                        }
                        if (currentSection == StoreSection.TOOLS && toolsTab.visibility != View.VISIBLE) {
                            currentSection = StoreSection.FREE
                        }
                        if (currentSection == StoreSection.UPDATES && updatesTab.visibility != View.VISIBLE) {
                            currentSection = StoreSection.FREE
                        }
                        showSection(currentSection, requestFirstFocus = false)
                    }
                }
            }
        }
    }

    private fun appsForSection(section: StoreSection): List<StoreApp> = when (section) {
        StoreSection.FREE -> allApps.filter { it.type == AppType.FREE }
        StoreSection.SUBSCRIPTION -> allApps.filter { it.type == AppType.SUBSCRIPTION }
        StoreSection.ADULT -> allApps.filter { it.type == AppType.ADULT }
        StoreSection.TOOLS -> allApps.filter { it.type == AppType.TOOLS }
        StoreSection.UPDATES -> allApps.filter { installState(it) == InstallState.UPDATE }
    }

    private fun showSection(section: StoreSection, requestFirstFocus: Boolean = true) {
        currentSection = section
        val apps = appsForSection(section)
        sectionTitle.text = when (section) {
            StoreSection.FREE -> "Free Apps"
            StoreSection.SUBSCRIPTION -> "Subscription Apps"
            StoreSection.ADULT -> "Adult Apps"
            StoreSection.TOOLS -> "Tools"
            StoreSection.UPDATES -> "Updates Available"
        }
        updateTabs(section)
        adapter.submitList(apps) {
            appsRecycler.scrollToPosition(0)
            val tabId = when (section) {
                StoreSection.FREE -> R.id.freeTab
                StoreSection.SUBSCRIPTION -> R.id.subscriptionTab
                StoreSection.ADULT -> R.id.adultTab
                StoreSection.TOOLS -> R.id.toolsTab
                StoreSection.UPDATES -> R.id.updatesTab
            }
            appsRecycler.post {
                for (i in 0 until appsRecycler.childCount) {
                    appsRecycler.getChildAt(i).nextFocusUpId = tabId
                }
            }
            if (requestFirstFocus && apps.isNotEmpty()) {
                appsRecycler.post {
                    appsRecycler.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                }
            }
        }
        focusedApp = apps.firstOrNull()
    }

    private fun updateTabs(selected: StoreSection) {
        listOf(
            freeTab to StoreSection.FREE,
            subscriptionTab to StoreSection.SUBSCRIPTION,
            adultTab to StoreSection.ADULT,
            toolsTab to StoreSection.TOOLS,
            updatesTab to StoreSection.UPDATES
        ).forEach { (view, section) ->
            view.isSelected = section == selected
            view.alpha = if (section == selected) 1f else 0.70f
        }
    }

    private fun refreshDynamicTabs() {
        adultTab.visibility =
            if (allApps.any { it.type == AppType.ADULT }) View.VISIBLE else View.GONE
        toolsTab.visibility =
            if (allApps.any { it.type == AppType.TOOLS }) View.VISIBLE else View.GONE
        val updateCount = allApps.count { installState(it) == InstallState.UPDATE }
        updatesTab.visibility = if (updateCount > 0) View.VISIBLE else View.GONE
        updatesTab.text = if (updateCount > 0) "Updates  $updateCount" else "Updates"
    }

    private fun openFullDetails(app: StoreApp) {
        detailArtworkFull.load(app.iconUrl) {
            crossfade(false)
            allowHardware(true)
            size(620, 620)
        }
        detailNameFull.text = app.name
        detailDescriptionFull.text = app.description.ifBlank {
            when (installState(app)) {
                InstallState.NOT_INSTALLED -> "Ready to install from Zappix."
                InstallState.INSTALLED -> "Installed and ready to open."
                InstallState.UPDATE -> "A newer version is available."
            }
        }
        detailTypeFull.text = when (app.type) {
            AppType.FREE -> "FREE"
            AppType.SUBSCRIPTION -> app.priceLabel ?: "SUBSCRIPTION"
            AppType.ADULT -> app.priceLabel ?: "18+"
            AppType.TOOLS -> "TOOLS"
        }
        detailErrorFull.visibility = View.GONE
        updateInstallButton(app)
        mainContent.visibility = View.INVISIBLE
        mainContent.isEnabled = false
        detailsOverlay.visibility = View.VISIBLE
        detailsOverlay.bringToFront()
        detailBackCallback.isEnabled = true
        installFullButton.post {
            installFullButton.isFocusable = true
            installFullButton.requestFocus()
        }
    }

    private fun closeFullDetails() {
        if (detailsOverlay.visibility != View.VISIBLE) return
        detailsOverlay.visibility = View.GONE
        mainContent.visibility = View.VISIBLE
        mainContent.isEnabled = true
        detailBackCallback.isEnabled = false
        val apps = appsForSection(currentSection)
        val index = focusedApp?.let { app -> apps.indexOfFirst { it.id == app.id } } ?: -1
        if (index >= 0) {
            appsRecycler.post {
                appsRecycler.findViewHolderForAdapterPosition(index)?.itemView?.requestFocus()
            }
        }
    }

    private fun startInstall(app: StoreApp, button: Button, errorView: TextView) {
        button.isEnabled = false
        button.text = "Downloading 0%"
        errorView.visibility = View.GONE
        lifecycleScope.launch {
            installer.downloadAndOpenInstaller(app) { progress ->
                runOnUiThread { button.text = "Downloading $progress%" }
            }.onFailure { e ->
                runOnUiThread {
                    errorView.text = e.message ?: "Download failed"
                    errorView.visibility = View.VISIBLE
                }
            }
            updateInstallButton(app)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
            refreshDynamicTabs()
            if (currentSection == StoreSection.UPDATES && updatesTab.visibility != View.VISIBLE) {
                currentSection = StoreSection.FREE
            }
            showSection(currentSection, requestFirstFocus = false)
        }
        if (::detailsOverlay.isInitialized && detailsOverlay.visibility == View.VISIBLE) {
            focusedApp?.let { updateInstallButton(it) }
        }
    }

    private fun installState(app: StoreApp): InstallState {
        val packageName = app.packageName?.trim().orEmpty()
        if (packageName.isEmpty()) return InstallState.NOT_INSTALLED
        return try {
            val info = packageManager.getPackageInfo(packageName, 0)
            val installedCode = if (android.os.Build.VERSION.SDK_INT >= 28) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            val remoteCode = app.versionCode
            if (remoteCode != null && remoteCode > installedCode) InstallState.UPDATE
            else InstallState.INSTALLED
        } catch (_: Exception) {
            InstallState.NOT_INSTALLED
        }
    }

    private fun updateInstallButton(app: StoreApp) {
        val state = installState(app)
        uninstallFullButton.visibility =
            if (state == InstallState.INSTALLED || state == InstallState.UPDATE) View.VISIBLE else View.GONE
        uninstallFullButton.isEnabled = uninstallFullButton.visibility == View.VISIBLE

        if (uninstallFullButton.visibility == View.VISIBLE) {
            installFullButton.nextFocusRightId = R.id.uninstallFullButton
            uninstallFullButton.nextFocusLeftId = R.id.installFullButton
            uninstallFullButton.nextFocusRightId = R.id.backFullButton
            backFullButton.nextFocusLeftId = R.id.uninstallFullButton
        } else {
            installFullButton.nextFocusRightId = R.id.backFullButton
            backFullButton.nextFocusLeftId = R.id.installFullButton
        }

        when (state) {
            InstallState.NOT_INSTALLED -> {
                installFullButton.isEnabled = true
                installFullButton.text = "Install"
            }
            InstallState.INSTALLED -> {
                installFullButton.isEnabled = true
                installFullButton.text = "Open"
            }
            InstallState.UPDATE -> {
                installFullButton.isEnabled = true
                installFullButton.text = "Update"
            }
        }
    }

    private fun openInstalledApp(app: StoreApp) {
        val packageName = app.packageName?.trim().orEmpty()
        if (packageName.isEmpty()) {
            detailErrorFull.text = "Package name unavailable."
            detailErrorFull.visibility = View.VISIBLE
            return
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            detailErrorFull.visibility = View.GONE
            startActivity(launchIntent)
        } else {
            detailErrorFull.text = "This app cannot be opened from Zappix."
            detailErrorFull.visibility = View.VISIBLE
        }
    }

    private fun uninstallInstalledApp(app: StoreApp) {
        val packageName = app.packageName?.trim().orEmpty()
        if (packageName.isEmpty()) {
            detailErrorFull.text = "Package name unavailable."
            detailErrorFull.visibility = View.VISIBLE
            return
        }
        detailErrorFull.visibility = View.GONE

        val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val canUninstall = uninstallIntent.resolveActivity(packageManager) != null
        if (canUninstall) {
            startActivity(uninstallIntent)
            return
        }

        val settingsIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (settingsIntent.resolveActivity(packageManager) != null) {
            startActivity(settingsIntent)
        } else {
            detailErrorFull.text = "This TV does not provide an uninstall screen."
            detailErrorFull.visibility = View.VISIBLE
        }
    }


}
