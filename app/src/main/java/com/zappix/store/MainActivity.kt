package com.zappix.store

import android.os.Bundle
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
    private lateinit var mainContent: View
    private lateinit var detailsOverlay: FrameLayout
    private lateinit var detailArtworkFull: ImageView
    private lateinit var detailNameFull: TextView
    private lateinit var detailTypeFull: TextView
    private lateinit var detailDescriptionFull: TextView
    private lateinit var detailErrorFull: TextView
    private lateinit var installFullButton: Button
    private lateinit var backFullButton: Button

    private val detailBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            closeFullDetails()
        }
    }

    private var allApps: List<StoreApp> = emptyList()
    private var currentType = AppType.FREE
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
        mainContent = findViewById(R.id.mainContent)
        detailsOverlay = findViewById(R.id.detailsOverlay)
        detailArtworkFull = findViewById(R.id.detailArtworkFull)
        detailNameFull = findViewById(R.id.detailNameFull)
        detailTypeFull = findViewById(R.id.detailTypeFull)
        detailDescriptionFull = findViewById(R.id.detailDescriptionFull)
        detailErrorFull = findViewById(R.id.detailErrorFull)
        installFullButton = findViewById(R.id.installFullButton)
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
            isInstalled = ::isInstalled
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

        freeTab.setOnClickListener { showCategory(AppType.FREE) }
        subscriptionTab.setOnClickListener { showCategory(AppType.SUBSCRIPTION) }
        adultTab.setOnClickListener { showCategory(AppType.ADULT) }

        freeTab.nextFocusDownId = R.id.appsRecycler
        subscriptionTab.nextFocusDownId = R.id.appsRecycler
        adultTab.nextFocusDownId = R.id.appsRecycler
        findViewById<View>(R.id.refreshButton).setOnClickListener { vm.refresh() }

        installFullButton.setOnClickListener {
            focusedApp?.let { startInstall(it, installFullButton, detailErrorFull) }
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
                        adultTab.visibility =
                            if (allApps.any { it.type == AppType.ADULT }) View.VISIBLE else View.GONE
                        if (currentType == AppType.ADULT && adultTab.visibility != View.VISIBLE) {
                            currentType = AppType.FREE
                        }
                        showCategory(currentType, requestFirstFocus = false)
                    }
                }
            }
        }
    }

    private fun showCategory(type: AppType, requestFirstFocus: Boolean = true) {
        currentType = type
        val apps = allApps.filter { it.type == type }
        sectionTitle.text = when (type) {
            AppType.FREE -> "Free Apps"
            AppType.SUBSCRIPTION -> "Subscription Apps"
            AppType.ADULT -> "Adult Apps"
        }
        updateTabs(type)
        adapter.submitList(apps) {
            appsRecycler.scrollToPosition(0)
            val tabId = when (type) {
                AppType.FREE -> R.id.freeTab
                AppType.SUBSCRIPTION -> R.id.subscriptionTab
                AppType.ADULT -> R.id.adultTab
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

    private fun updateTabs(selected: AppType) {
        listOf(
            freeTab to AppType.FREE,
            subscriptionTab to AppType.SUBSCRIPTION,
            adultTab to AppType.ADULT
        ).forEach { (view, type) ->
            view.isSelected = type == selected
            view.alpha = if (type == selected) 1f else 0.70f
        }
    }

    private fun openFullDetails(app: StoreApp) {
        detailArtworkFull.load(app.iconUrl) {
            crossfade(false)
            allowHardware(true)
            size(620, 620)
        }
        detailNameFull.text = app.name
        detailDescriptionFull.text = app.description.ifBlank { "Ready to install from Zappix." }
        detailTypeFull.text = when (app.type) {
            AppType.FREE -> "FREE"
            AppType.SUBSCRIPTION -> app.priceLabel ?: "SUBSCRIPTION"
            AppType.ADULT -> app.priceLabel ?: "18+"
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
        val apps = allApps.filter { it.type == currentType }
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
            button.isEnabled = true
            button.text = "Install"
        }
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) adapter.notifyDataSetChanged()
        if (::detailsOverlay.isInitialized && detailsOverlay.visibility == View.VISIBLE) {
            focusedApp?.let { updateInstallButton(it) }
        }
    }

    private fun isInstalled(app: StoreApp): Boolean {
        val packageName = app.packageName?.trim().orEmpty()
        if (packageName.isEmpty()) return false
        return try {
            packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun updateInstallButton(app: StoreApp) {
        val installed = isInstalled(app)
        installFullButton.isEnabled = !installed
        installFullButton.text = if (installed) "Installed" else "Install"
    }

}
