package com.zappix.store

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: StoreViewModel by viewModels()

    private lateinit var adapter: AppAdapter
    private lateinit var appsRecycler: RecyclerView
    private lateinit var loading: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var sectionTitle: TextView
    private lateinit var detailName: TextView
    private lateinit var detailDescription: TextView
    private lateinit var detailType: TextView
    private lateinit var installButton: Button
    private lateinit var freeTab: TextView
    private lateinit var subscriptionTab: TextView
    private lateinit var adultTab: TextView

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
        detailName = findViewById(R.id.detailName)
        detailDescription = findViewById(R.id.detailDescription)
        detailType = findViewById(R.id.detailType)
        installButton = findViewById(R.id.installButton)
        freeTab = findViewById(R.id.freeTab)
        subscriptionTab = findViewById(R.id.subscriptionTab)
        adultTab = findViewById(R.id.adultTab)

        adapter = AppAdapter(
            onFocused = { app ->
                focusedApp = app
                renderDetails(app)
            },
            onClicked = { app ->
                focusedApp = app
                renderDetails(app)
                installButton.requestFocus()
            }
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

        installButton.setOnClickListener {
            val app = focusedApp ?: return@setOnClickListener
            installButton.isEnabled = false
            installButton.text = "Downloading 0%"
            errorText.visibility = View.GONE
            lifecycleScope.launch {
                installer.downloadAndOpenInstaller(app) { progress ->
                    runOnUiThread { installButton.text = "Downloading $progress%" }
                }.onFailure { e ->
                    runOnUiThread {
                        errorText.text = e.message ?: "Download failed"
                        errorText.visibility = View.VISIBLE
                    }
                }
                installButton.isEnabled = true
                installButton.text = "Install"
            }
        }

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

        if (apps.isNotEmpty()) {
            focusedApp = apps.first()
            renderDetails(apps.first())
        } else {
            focusedApp = null
            detailName.text = "No apps yet"
            detailDescription.text = "This category is currently empty."
            detailType.text = ""
            installButton.isEnabled = false
        }
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

    private fun renderDetails(app: StoreApp) {
        detailName.text = app.name
        detailDescription.text = app.description.ifBlank { "Ready to install from Zappix." }
        detailType.text = when (app.type) {
            AppType.FREE -> "FREE"
            AppType.SUBSCRIPTION -> app.priceLabel ?: "SUBSCRIPTION"
            AppType.ADULT -> app.priceLabel ?: "18+"
        }
        installButton.isEnabled = true
    }
}
