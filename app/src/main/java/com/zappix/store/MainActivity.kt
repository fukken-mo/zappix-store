package com.zappix.store

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class StoreSection { FREE, SUBSCRIPTION, ADULT, TOOLS, UPDATES }

class MainActivity : ComponentActivity() {
    private val vm: StoreViewModel by viewModels()

    private lateinit var adapter: AppAdapter
    private lateinit var rowLayoutManager: TvRowLayoutManager
    private lateinit var appsRecycler: RecyclerView
    private lateinit var loading: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var emptyText: TextView
    private lateinit var sectionTitle: TextView
    private lateinit var refreshButton: TextView
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
    private lateinit var installer: ApkInstaller

    private val tabs: List<Pair<TextView, StoreSection>> by lazy {
        listOf(
            freeTab to StoreSection.FREE,
            subscriptionTab to StoreSection.SUBSCRIPTION,
            adultTab to StoreSection.ADULT,
            toolsTab to StoreSection.TOOLS,
            updatesTab to StoreSection.UPDATES
        )
    }

    // Catalog / install state
    private var allApps: List<StoreApp> = emptyList()
    private var installedVersions: Map<String, Long> = emptyMap()
    private var installStatesJob: Job? = null

    // Row / focus state
    private var currentSection = StoreSection.FREE
    private var displayedSection: StoreSection? = null
    private var lastFocusedIndex = 0
    private var initialFocusDone = false

    /** The app shown in Details. The Details buttons act on this app and nothing else. */
    private var detailApp: StoreApp? = null

    // Download / install state
    private class ActiveDownload(val appId: Int, val name: String, val job: Job)
    private var activeDownload: ActiveDownload? = null
    private var downloadProgressText: String? = null
    private var pendingInstall: PreparedApk? = null

    private sealed class PermissionTarget {
        data class App(val appId: Int) : PermissionTarget()
        object SelfUpdate : PermissionTarget()
    }
    private var pendingPermission: PermissionTarget? = null

    private var updateDialog: AlertDialog? = null

    private val detailsOpen: Boolean get() = detailsOverlay.visibility == View.VISIBLE

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            when {
                detailsOpen -> closeFullDetails()
                currentSection != StoreSection.FREE -> {
                    showSection(StoreSection.FREE)
                    freeTab.requestFocus()
                }
                else -> {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
    }

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshInstallStates()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        installer = ApkInstaller(this)
        appsRecycler = findViewById(R.id.appsRecycler)
        loading = findViewById(R.id.loading)
        errorText = findViewById(R.id.errorText)
        emptyText = findViewById(R.id.emptyText)
        sectionTitle = findViewById(R.id.sectionTitle)
        refreshButton = findViewById(R.id.refreshButton)
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
        onBackPressedDispatcher.addCallback(this, backCallback)

        detailArtworkFull.clipToOutline = true

        adapter = AppAdapter(
            onFocused = { _, position -> lastFocusedIndex = position },
            onClicked = { app -> openFullDetails(app) },
            onUpPressed = { tabFor(currentSection).requestFocus() }
        )
        rowLayoutManager = TvRowLayoutManager(this) { target -> focusCard(target) }

        appsRecycler.apply {
            layoutManager = rowLayoutManager
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

        setupTabs()
        refreshButton.setOnClickListener { vm.refresh() }
        refreshButton.nextFocusUpId = R.id.refreshButton
        refreshButton.nextFocusRightId = R.id.refreshButton
        addFocusScale(refreshButton, 1.06f)

        installFullButton.setOnClickListener { onPrimaryDetailAction() }
        uninstallFullButton.setOnClickListener { detailApp?.let { uninstallInstalledApp(it) } }
        backFullButton.setOnClickListener { closeFullDetails() }
        listOf(installFullButton, uninstallFullButton, backFullButton).forEach { addFocusScale(it, 1.05f) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.state.collect { render(it) } }
                launch { vm.update.collect { maybeShowUpdateDialog() } }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageChangeReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onStop() {
        try {
            unregisterReceiver(packageChangeReceiver)
        } catch (_: IllegalArgumentException) {
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        backCallback.isEnabled = true
        refreshInstallStates()

        pendingInstall?.let { prepared ->
            pendingInstall = null
            launchInstaller(prepared)
            return
        }

        pendingPermission?.let { target ->
            pendingPermission = null
            if (installer.canInstallPackages()) {
                when (target) {
                    is PermissionTarget.App -> detailApp?.takeIf { it.id == target.appId }?.let { startInstall(it) }
                    PermissionTarget.SelfUpdate -> vm.update.value?.let { startSelfUpdate(it) }
                }
                return
            }
            val message = "Zappix is not allowed to install apps yet. Turn on \"Allow from this source\" for Zappix and try again."
            if (target is PermissionTarget.App && detailsOpen) showDetailError(message) else toast(message)
        }

        if (activeDownload == null) installer.cleanup()
        maybeShowUpdateDialog()
    }

    // ---------------------------------------------------------------------------------------
    // Catalog rendering
    // ---------------------------------------------------------------------------------------

    private fun render(state: StoreUiState) {
        errorText.text = state.error.orEmpty()
        errorText.visibility = if (state.error != null) View.VISIBLE else View.GONE
        if (state.apps !== allApps) {
            allApps = state.apps
            refreshInstallStates()
        }
        updateLoadingAndEmpty()
        if (state.loaded) maybeShowUpdateDialog()
    }

    private fun updateLoadingAndEmpty() {
        val state = vm.state.value
        val empty = adapter.itemCount == 0
        loading.visibility = if (state.loading && empty) View.VISIBLE else View.GONE
        emptyText.visibility = if (state.loaded && !state.loading && empty) View.VISIBLE else View.GONE
    }

    /** Reads installed versions off the main thread, then updates tabs, cards and Details. */
    private fun refreshInstallStates() {
        val apps = allApps
        installStatesJob?.cancel()
        installStatesJob = lifecycleScope.launch {
            installedVersions = withContext(Dispatchers.Default) {
                apps.mapNotNull { it.packageName }
                    .distinct()
                    .mapNotNull { pkg -> packageManager.installedPackageInfo(pkg)?.let { pkg to it.versionCodeCompat } }
                    .toMap()
            }
            applyCatalogState()
        }
    }

    private fun applyCatalogState() {
        refreshDynamicTabs()
        val section = if (tabFor(currentSection).visibility == View.VISIBLE) currentSection else StoreSection.FREE
        showSection(section)
        detailApp?.let { shown ->
            detailApp = allApps.firstOrNull { it.id == shown.id } ?: shown
            renderDetailButtons()
        }
    }

    private fun installState(app: StoreApp): InstallState {
        val installed = app.packageName?.let { installedVersions[it] } ?: return InstallState.NOT_INSTALLED
        val remote = app.versionCode
        return if (remote != null && remote > installed) InstallState.UPDATE else InstallState.INSTALLED
    }

    /** Fresh single-package check for Details, so a button never acts on stale state. */
    private fun liveInstallState(app: StoreApp): InstallState {
        val pkg = app.packageName ?: return InstallState.NOT_INSTALLED
        val installed = packageManager.installedPackageInfo(pkg)?.versionCodeCompat ?: return InstallState.NOT_INSTALLED
        val remote = app.versionCode
        return if (remote != null && remote > installed) InstallState.UPDATE else InstallState.INSTALLED
    }

    private fun itemsFor(section: StoreSection): List<AppItem> {
        val apps = when (section) {
            StoreSection.FREE -> allApps.filter { it.type == AppType.FREE }
            StoreSection.SUBSCRIPTION -> allApps.filter { it.type == AppType.SUBSCRIPTION }
            StoreSection.ADULT -> allApps.filter { it.type == AppType.ADULT }
            StoreSection.TOOLS -> allApps.filter { it.type == AppType.TOOLS }
            StoreSection.UPDATES -> allApps.filter { installState(it) == InstallState.UPDATE }
        }
        return apps.map { AppItem(it, installState(it)) }
    }

    /**
     * Shows [section]. The row only scrolls back to the start when the section actually changes;
     * plain refreshes (returning from the installer, a package change, a catalog reload) keep the
     * scroll position and the focused card.
     */
    private fun showSection(section: StoreSection, focusFirst: Boolean = false) {
        val changed = section != displayedSection
        if (changed && appsRecycler.hasFocus()) tabFor(section).requestFocus()
        currentSection = section
        displayedSection = section
        if (changed) lastFocusedIndex = 0
        sectionTitle.text = when (section) {
            StoreSection.FREE -> "Free Apps"
            StoreSection.SUBSCRIPTION -> "Subscription Apps"
            StoreSection.ADULT -> "Adult Apps"
            StoreSection.TOOLS -> "Tools"
            StoreSection.UPDATES -> "Updates Available"
        }
        updateTabs()
        val items = itemsFor(section)
        adapter.submitList(items) {
            if (changed) appsRecycler.scrollToPosition(0)
            updateLoadingAndEmpty()
            ensureInitialFocus()
            if (focusFirst && items.isNotEmpty()) focusCard(0)
        }
    }

    /** First launch: put focus on the content instead of the Refresh button. */
    private fun ensureInitialFocus() {
        if (initialFocusDone || !vm.state.value.loaded || detailsOpen) return
        initialFocusDone = true
        val current = currentFocus
        if (current != null && current !== refreshButton) return
        if (adapter.itemCount > 0) focusCard(0) else tabFor(currentSection).requestFocus()
    }

    /** Focuses the card at [index], scrolling to it first if it is not laid out. */
    private fun focusCard(index: Int, holdFocusOn: View? = null): Boolean {
        if (index !in 0 until adapter.itemCount) return false
        rowLayoutManager.findViewByPosition(index)?.let { if (it.requestFocus()) return true }
        holdFocusOn?.requestFocus()
        appsRecycler.scrollToPosition(index)
        appsRecycler.post { rowLayoutManager.findViewByPosition(index)?.requestFocus() }
        return true
    }

    // ---------------------------------------------------------------------------------------
    // Tabs
    // ---------------------------------------------------------------------------------------

    private fun setupTabs() {
        tabs.forEach { (tab, section) ->
            tab.setOnClickListener { selectSection(section) }
            tab.nextFocusUpId = R.id.refreshButton
            tab.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                    focusRow()
                } else {
                    false
                }
            }
            tab.setOnFocusChangeListener { view, focused ->
                view.animate().cancel()
                view.animate().scaleX(if (focused) 1.06f else 1f).scaleY(if (focused) 1.06f else 1f)
                    .setDuration(120L).start()
                updateTabs()
            }
        }
    }

    private fun selectSection(section: StoreSection) {
        if (section == StoreSection.ADULT && !vm.adultConfirmed) {
            confirmAdult()
            return
        }
        showSection(section)
    }

    private fun confirmAdult() {
        val dialog = AlertDialog.Builder(this, R.style.Theme_Zappix_Dialog)
            .setTitle("Adult content (18+)")
            .setMessage("This section contains apps for adults only. Are you 18 or older?")
            .setPositiveButton("I'm 18+") { _, _ ->
                vm.adultConfirmed = true
                showSection(StoreSection.ADULT)
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener { dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.requestFocus() }
        showSafely(dialog)
    }

    /** Tab -> DOWN goes to the last focused card of the row (or the first one). */
    private fun focusRow(): Boolean {
        val count = adapter.itemCount
        if (count == 0) return true
        focusCard(lastFocusedIndex.coerceIn(0, count - 1))
        return true
    }

    private fun tabFor(section: StoreSection): TextView = tabs.first { it.second == section }.first

    private fun updateTabs() {
        tabs.forEach { (view, section) ->
            val selected = section == currentSection
            view.isSelected = selected
            view.alpha = if (selected || view.isFocused) 1f else 0.72f
        }
        refreshButton.nextFocusDownId = tabFor(currentSection).id
    }

    private fun refreshDynamicTabs() {
        setTabVisible(adultTab, allApps.any { it.type == AppType.ADULT })
        setTabVisible(toolsTab, allApps.any { it.type == AppType.TOOLS })
        val updateCount = allApps.count { installState(it) == InstallState.UPDATE }
        setTabVisible(updatesTab, updateCount > 0)
        updatesTab.text = if (updateCount > 0) "Updates ($updateCount)" else "Updates"

        // First/last visible tab: LEFT/RIGHT stay on the tab row instead of jumping to Refresh.
        val visible = tabs.map { it.first }.filter { it.visibility == View.VISIBLE }
        visible.forEach {
            it.nextFocusLeftId = View.NO_ID
            it.nextFocusRightId = View.NO_ID
        }
        visible.firstOrNull()?.let { it.nextFocusLeftId = it.id }
        visible.lastOrNull()?.let { it.nextFocusRightId = it.id }
    }

    private fun setTabVisible(tab: TextView, visible: Boolean) {
        if (!visible && tab.hasFocus()) freeTab.requestFocus()
        tab.visibility = if (visible) View.VISIBLE else View.GONE
    }

    // ---------------------------------------------------------------------------------------
    // Details
    // ---------------------------------------------------------------------------------------

    private fun openFullDetails(app: StoreApp) {
        detailApp = app
        detailArtworkFull.load(app.iconUrl.ifBlank { null }) {
            crossfade(false)
            allowHardware(true)
            size(620, 620)
        }
        detailNameFull.text = app.name
        detailDescriptionFull.text = app.description.ifBlank {
            when (liveInstallState(app)) {
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
        renderDetailButtons()
        // Show the overlay and move focus into it before hiding the row, so focus is never lost.
        detailsOverlay.visibility = View.VISIBLE
        detailsOverlay.bringToFront()
        installFullButton.requestFocus()
        mainContent.visibility = View.INVISIBLE
    }

    private fun closeFullDetails() {
        if (!detailsOpen) return
        val returnId = detailApp?.id
        detailApp = null
        mainContent.visibility = View.VISIBLE
        val items = adapter.currentList
        val index = items.indexOfFirst { it.app.id == returnId }.takeIf { it >= 0 }
            ?: lastFocusedIndex.coerceAtMost(items.size - 1)
        val tab = tabFor(currentSection)
        if (index < 0 || !focusCard(index, holdFocusOn = tab)) tab.requestFocus()
        detailsOverlay.visibility = View.GONE
        maybeShowUpdateDialog()
    }

    private fun isSelf(app: StoreApp) = app.packageName == packageName

    private fun renderDetailButtons() {
        val app = detailApp ?: return
        val state = liveInstallState(app)
        val downloading = activeDownload?.appId == app.id
        val showUninstall = state != InstallState.NOT_INSTALLED && !isSelf(app) && !downloading

        if (!showUninstall && uninstallFullButton.hasFocus()) installFullButton.requestFocus()
        uninstallFullButton.visibility = if (showUninstall) View.VISIBLE else View.GONE

        if (showUninstall) {
            installFullButton.nextFocusRightId = R.id.uninstallFullButton
            uninstallFullButton.nextFocusLeftId = R.id.installFullButton
            uninstallFullButton.nextFocusRightId = R.id.backFullButton
            backFullButton.nextFocusLeftId = R.id.uninstallFullButton
        } else {
            installFullButton.nextFocusRightId = R.id.backFullButton
            backFullButton.nextFocusLeftId = R.id.installFullButton
        }

        installFullButton.isActivated = downloading
        installFullButton.text = when {
            downloading -> downloadProgressText ?: "Downloading…"
            isSelf(app) && state == InstallState.INSTALLED -> "Up to date"
            state == InstallState.INSTALLED -> "Open"
            state == InstallState.UPDATE -> "Update"
            else -> "Install"
        }
    }

    private fun onPrimaryDetailAction() {
        val app = detailApp ?: return
        activeDownload?.let { active ->
            toast(if (active.appId == app.id) "${app.name} is already downloading…" else "Please wait: ${active.name} is still downloading.")
            return
        }
        when (liveInstallState(app)) {
            InstallState.INSTALLED -> if (isSelf(app)) toast("Zappix is up to date.") else openInstalledApp(app)
            InstallState.NOT_INSTALLED, InstallState.UPDATE -> startInstall(app)
        }
    }

    private fun showDetailError(message: String) {
        detailErrorFull.text = message
        detailErrorFull.visibility = View.VISIBLE
    }

    // ---------------------------------------------------------------------------------------
    // Install / open / uninstall
    // ---------------------------------------------------------------------------------------

    private fun startInstall(app: StoreApp) {
        if (activeDownload != null) return
        val pkg = app.packageName
        if (!isValidPackageName(pkg)) {
            showDetailError("${app.name} has no valid package name in the Zappix catalog, so it can't be installed safely.")
            return
        }
        val duplicate = allApps.firstOrNull { it.id != app.id && it.packageName == pkg }
        if (duplicate != null) {
            showDetailError("Install blocked: ${app.name} and ${duplicate.name} are listed with the same Android package. Please report this to support.")
            return
        }
        if (!installer.canInstallPackages()) {
            requestInstallPermission(PermissionTarget.App(app.id))
            return
        }

        detailErrorFull.visibility = View.GONE
        pendingInstall = null
        installer.cleanup()
        val request = ApkDownloadRequest(
            appId = app.id,
            displayName = app.name,
            url = app.downloadUrl,
            expectedPackage = pkg!!,
            minVersionCode = app.versionCode,
            sha256 = app.sha256
        )
        downloadProgressText = "Downloading…"
        val job = lifecycleScope.launch {
            try {
                val prepared = installer.download(request) { done, total ->
                    val text = "Downloading ${formatProgress(done, total)}"
                    runOnUiThread {
                        if (activeDownload?.appId == app.id) {
                            downloadProgressText = text
                            if (detailApp?.id == app.id) installFullButton.text = text
                        }
                    }
                }
                finishDownload(app.id)
                launchOrDefer(prepared)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                finishDownload(app.id)
                val message = e.userMessage("Download failed. Please try again.")
                if (detailApp?.id == app.id) showDetailError(message) else toast(message)
            } finally {
                finishDownload(app.id)
            }
        }
        activeDownload = ActiveDownload(app.id, app.name, job)
        renderDetailButtons()
    }

    private fun finishDownload(appId: Int) {
        if (activeDownload?.appId != appId) return
        activeDownload = null
        downloadProgressText = null
        renderDetailButtons()
    }

    private fun formatProgress(done: Long, total: Long): String =
        if (total > 0) "${(done * 100 / total).coerceIn(0, 100)}%" else "${done / (1024 * 1024)} MB"

    /** Android 10+ blocks activity starts from the background, so wait until Zappix is visible. */
    private fun launchOrDefer(prepared: PreparedApk) {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            launchInstaller(prepared)
        } else {
            pendingInstall = prepared
        }
    }

    private fun launchInstaller(prepared: PreparedApk) {
        if (!installer.launchInstaller(this, prepared)) {
            val message = "This TV has no app installer available."
            if (detailsOpen) showDetailError(message) else toast(message)
        }
    }

    private fun requestInstallPermission(target: PermissionTarget) {
        pendingPermission = target
        if (tryStart(installer.unknownSourcesIntent())) {
            toast("Turn on \"Allow from this source\" for Zappix, then press Back.")
        } else {
            pendingPermission = null
            val message = "Allow Zappix to install apps: Settings > Apps > Special app access > Install unknown apps > Zappix."
            if (target is PermissionTarget.App && detailsOpen) showDetailError(message) else toast(message)
        }
    }

    private fun openInstalledApp(app: StoreApp) {
        val pkg = app.packageName
        if (pkg.isNullOrEmpty()) {
            showDetailError("Package name unavailable.")
            return
        }
        // TV-only apps only declare LEANBACK_LAUNCHER, which getLaunchIntentForPackage ignores.
        val launchIntent = packageManager.getLeanbackLaunchIntentForPackage(pkg)
            ?: packageManager.getLaunchIntentForPackage(pkg)
        if (launchIntent != null && tryStart(launchIntent)) {
            detailErrorFull.visibility = View.GONE
        } else {
            showDetailError("This app cannot be opened from Zappix.")
        }
    }

    private fun uninstallInstalledApp(app: StoreApp) {
        val pkg = app.packageName
        if (!isValidPackageName(pkg) || pkg == packageName) {
            showDetailError("This app can't be uninstalled from Zappix.")
            return
        }
        detailErrorFull.visibility = View.GONE
        val uri = Uri.parse("package:$pkg")
        if (tryStart(Intent(Intent.ACTION_DELETE, uri))) return
        if (tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))) return
        showDetailError("This TV does not provide an uninstall screen.")
    }

    private fun tryStart(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    // ---------------------------------------------------------------------------------------
    // Zappix self-update
    // ---------------------------------------------------------------------------------------

    private fun maybeShowUpdateDialog() {
        val info = vm.update.value ?: return
        if (vm.updateDismissed || updateDialog?.isShowing == true) return
        if (activeDownload != null || pendingInstall != null || pendingPermission != null) return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val state = vm.state.value
        if (!state.loaded && state.error == null) return // wait until the store has loaded
        if (!info.required && detailsOpen) return

        val builder = AlertDialog.Builder(this, R.style.Theme_Zappix_Dialog)
            .setTitle("Zappix Update Available")
            .setMessage(
                buildString {
                    append("Version ")
                    append(info.versionName.ifBlank { info.versionCode.toString() })
                    append(" is available.")
                    if (info.required) append(" This update is required.")
                    if (info.message.isNotBlank()) {
                        append("\n\n")
                        append(info.message)
                    }
                }
            )
            .setPositiveButton("Update") { _, _ -> startSelfUpdate(info) }
            .setCancelable(false)
        if (info.required) {
            builder.setNeutralButton("Exit") { _, _ -> finish() }
        } else {
            builder.setNegativeButton("Later") { _, _ -> vm.updateDismissed = true }
        }
        val dialog = builder.create()
        dialog.setOnShowListener {
            val initial = if (info.required) DialogInterface.BUTTON_POSITIVE else DialogInterface.BUTTON_NEGATIVE
            dialog.getButton(initial)?.requestFocus()
        }
        updateDialog = dialog
        showSafely(dialog)
    }

    private fun startSelfUpdate(info: ZappixUpdateInfo) {
        activeDownload?.let { active ->
            if (active.appId != SELF_ID) toast("Please wait: ${active.name} is still downloading.")
            return
        }
        if (!installer.canInstallPackages()) {
            requestInstallPermission(PermissionTarget.SelfUpdate)
            return
        }
        pendingInstall = null
        installer.cleanup()

        val progressBuilder = AlertDialog.Builder(this, R.style.Theme_Zappix_Dialog)
            .setTitle("Updating Zappix")
            .setMessage("Downloading…")
            .setCancelable(false)
        if (!info.required) {
            progressBuilder.setNegativeButton("Cancel") { _, _ ->
                vm.updateDismissed = true
                activeDownload?.takeIf { it.appId == SELF_ID }?.job?.cancel()
            }
        }
        val progress = progressBuilder.create()
        showSafely(progress)

        val request = ApkDownloadRequest(
            appId = SELF_ID,
            displayName = "Zappix",
            url = info.apkUrl,
            expectedPackage = packageName,
            // Never hand the installer an APK that isn't newer than this build (prevents update loops).
            minVersionCode = maxOf(info.versionCode, BuildConfig.VERSION_CODE + 1L),
            sha256 = info.sha256
        )
        val job = lifecycleScope.launch {
            try {
                val prepared = installer.download(request) { done, total ->
                    val text = "Downloading… ${formatProgress(done, total)}"
                    runOnUiThread { progress.setMessage(text) }
                }
                dismissSafely(progress)
                finishDownload(SELF_ID)
                if (!info.required) vm.updateDismissed = true
                launchOrDefer(prepared)
            } catch (e: CancellationException) {
                dismissSafely(progress)
                throw e
            } catch (e: Throwable) {
                dismissSafely(progress)
                finishDownload(SELF_ID)
                showSelfUpdateFailed(info, e)
            } finally {
                finishDownload(SELF_ID)
            }
        }
        activeDownload = ActiveDownload(SELF_ID, "Zappix update", job)
    }

    private fun showSelfUpdateFailed(info: ZappixUpdateInfo, error: Throwable) {
        val message = error.userMessage("Unable to download the Zappix update.")
        val builder = AlertDialog.Builder(this, R.style.Theme_Zappix_Dialog)
            .setTitle("Update Failed")
            .setMessage(message)
            .setCancelable(false)
        if (error is ApkRejectedException) {
            // Retrying the same file can't succeed; don't trap the user in a loop.
            vm.updateDismissed = true
            builder.setPositiveButton("OK", null)
        } else {
            builder.setPositiveButton("Retry") { _, _ -> startSelfUpdate(info) }
            if (info.required) {
                builder.setNeutralButton("Exit") { _, _ -> finish() }
            } else {
                builder.setNegativeButton("Later") { _, _ -> vm.updateDismissed = true }
            }
        }
        val dialog = builder.create()
        updateDialog = dialog
        showSafely(dialog)
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private fun addFocusScale(view: View, scale: Float) {
        view.setOnFocusChangeListener { v, focused ->
            v.animate().cancel()
            v.animate().scaleX(if (focused) scale else 1f).scaleY(if (focused) scale else 1f)
                .setDuration(120L).start()
        }
    }

    private fun showSafely(dialog: AlertDialog) {
        if (isFinishing || isDestroyed) return
        try {
            dialog.show()
        } catch (_: Exception) {
        }
    }

    private fun dismissSafely(dialog: AlertDialog) {
        try {
            if (dialog.isShowing) dialog.dismiss()
        } catch (_: Exception) {
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val SELF_ID = -1000
    }
}
