package com.zappix.store

import android.content.Context
import android.graphics.RectF
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.View.MeasureSpec
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * Lays the real XML out on the 960x540dp TV canvas (what both 720p and 1080p TVs report) and
 * checks the overscan-safe area (5%: 48dp left/right, 27dp top/bottom), card focus clipping and
 * the Details action row in their worst-case states.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvLayoutTest {
    private lateinit var context: Context
    private val density: Float get() = context.resources.displayMetrics.density
    private val tolerance = 0.5f

    @Before
    fun setUp() {
        context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Zappix)
    }

    private fun dp(value: Float) = value * density

    private fun safeArea() = RectF(dp(48f), dp(27f), dp(960f - 48f), dp(540f - 27f))

    private fun layoutScreen(root: View) {
        val width = dp(960f).roundToInt()
        val height = dp(540f).roundToInt()
        root.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, width, height)
    }

    /** Bounds of [view] in [root]'s coordinates. */
    private fun boundsIn(root: View, view: View): RectF {
        var x = 0f
        var y = 0f
        var current: View = view
        while (current !== root) {
            x += current.left
            y += current.top
            val parent = current.parent as View
            x -= parent.scrollX
            y -= parent.scrollY
            current = parent
        }
        return RectF(x, y, x + view.width, y + view.height)
    }

    private fun assertInside(name: String, outer: RectF, inner: RectF) {
        assertTrue(
            "$name $inner is outside $outer",
            inner.left >= outer.left - tolerance && inner.top >= outer.top - tolerance &&
                inner.right <= outer.right + tolerance && inner.bottom <= outer.bottom + tolerance
        )
    }

    private fun assertNoOverlap(name: String, views: List<RectF>) {
        views.zipWithNext().forEach { (a, b) ->
            assertTrue("$name: $a overlaps $b", a.right <= b.left + tolerance)
        }
    }

    @Test
    fun homeFitsSafeAreaInWorstCase() {
        val root = LayoutInflater.from(context).inflate(R.layout.activity_main, null)
        listOf(R.id.adultTab, R.id.toolsTab, R.id.updatesTab).forEach {
            root.findViewById<View>(it).visibility = View.VISIBLE
        }
        root.findViewById<TextView>(R.id.updatesTab).text = "Updates · 99"
        root.findViewById<TextView>(R.id.refreshButton).text = "Refreshing…"
        root.findViewById<TextView>(R.id.sectionTitle).text = "Subscription Apps"
        root.findViewById<TextView>(R.id.errorText).apply {
            visibility = View.VISIBLE
            text = "Can't reach the server. Check the TV's internet connection and try again."
        }
        layoutScreen(root)

        val safe = safeArea()
        val ids = mapOf(
            "header" to R.id.header,
            "refresh" to R.id.refreshButton,
            "navRow" to R.id.navRow,
            "free" to R.id.freeTab,
            "subscription" to R.id.subscriptionTab,
            "adult" to R.id.adultTab,
            "tools" to R.id.toolsTab,
            "updates" to R.id.updatesTab,
            "sectionTitle" to R.id.sectionTitle,
            "errorText" to R.id.errorText
        )
        ids.forEach { (name, id) -> assertInside(name, safe, boundsIn(root, root.findViewById(id))) }

        // The row intentionally runs edge to edge horizontally; vertically it must stay inside the safe area.
        val rowBounds = boundsIn(root, root.findViewById(R.id.rowContainer))
        assertTrue("row top above safe area", rowBounds.top >= safe.top - tolerance)
        assertTrue("row bottom below safe area", rowBounds.bottom <= safe.bottom + tolerance)

        val pills = listOf(R.id.freeTab, R.id.subscriptionTab, R.id.adultTab, R.id.toolsTab, R.id.updatesTab)
            .map { boundsIn(root, root.findViewById(it)) }
        assertNoOverlap("nav pills", pills)

        // A focused card (scaled and lifted) must stay inside the row and above the bottom safe edge.
        val row = boundsIn(root, root.findViewById(R.id.rowContainer))
        val recycler = root.findViewById<View>(R.id.appsRecycler)
        val cardHeight = context.resources.getDimension(R.dimen.zx_card_height)
        val cardTop = row.top + recycler.paddingTop
        val grow = cardHeight * (AppAdapter.FOCUS_SCALE - 1f) / 2f
        val lift = dp(AppAdapter.FOCUS_LIFT_DP)
        assertTrue("focused card top clipped", cardTop - grow - lift >= row.top - tolerance)
        assertTrue("focused card bottom outside row", cardTop + cardHeight + grow <= row.bottom + tolerance)
        assertTrue("focused card below safe area", cardTop + cardHeight + grow <= safe.bottom + tolerance)

        // First card starts on the same safe edge as the tabs.
        assertEquals(pills.first().left, row.left + recycler.paddingStart, 1f)
    }

    @Test
    fun cardFocusNeverClipsOrOverlapsNeighbours() {
        assertTrue("card focus scale must be <= 1.03", AppAdapter.FOCUS_SCALE <= 1.03f)
        val parent = FrameLayout(context)
        val card = LayoutInflater.from(context).inflate(R.layout.item_app_card, parent, false)
        val res = context.resources
        val width = res.getDimensionPixelSize(R.dimen.zx_card_width)
        val height = res.getDimensionPixelSize(R.dimen.zx_card_height)
        card.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
        card.layout(0, 0, width, height)
        val cardBounds = RectF(0f, 0f, width.toFloat(), height.toFloat())

        // Focus ring, artwork, badge, name and subtitle are all drawn inside the card itself.
        listOf(R.id.focusFrame, R.id.appArtwork, R.id.installedBadge, R.id.appName, R.id.appSubtitle).forEach { id ->
            assertInside("card child $id", cardBounds, boundsIn(card, card.findViewById(id)))
        }
        // Text must sit on the tile face, above the baked depth shadow.
        val face = RectF(0f, 0f, width.toFloat(), height - res.getDimension(R.dimen.zx_card_depth))
        assertInside("card subtitle on face", face, boundsIn(card, card.findViewById(R.id.appSubtitle)))
        // Installed/Update badge must draw above the artwork (a higher Z would hide it).
        val badge = card.findViewById<View>(R.id.installedBadge)
        val artwork = card.findViewById<View>(R.id.appArtwork)
        assertTrue("badge is hidden under the artwork", badge.z >= artwork.z)

        val growV = height * (AppAdapter.FOCUS_SCALE - 1f) / 2f
        val growH = width * (AppAdapter.FOCUS_SCALE - 1f) / 2f
        assertTrue("scale+lift exceed row top padding",
            growV + dp(AppAdapter.FOCUS_LIFT_DP) <= res.getDimension(R.dimen.zx_row_pad_top))
        assertTrue("scale exceeds row bottom padding", growV <= res.getDimension(R.dimen.zx_row_pad_bottom))
        assertTrue("focused card would touch its neighbour", growH < res.getDimension(R.dimen.zx_card_gap))
    }

    @Test
    fun detailsFitSafeAreaInWorstCase() {
        val root = LayoutInflater.from(context).inflate(R.layout.activity_main, null)
        root.findViewById<View>(R.id.mainContent).visibility = View.INVISIBLE
        root.findViewById<View>(R.id.detailsOverlay).visibility = View.VISIBLE
        root.findViewById<TextView>(R.id.detailNameFull).text = "An Extremely Long Application Name For Testing Layout"
        root.findViewById<TextView>(R.id.detailTypeFull).text = "SUBSCRIPTION · \$12.99 / MONTH · INTRO PRICE"
        root.findViewById<TextView>(R.id.detailMetaFull).text = "Version 12.4.1-beta.7  •  58.2 MB"
        root.findViewById<TextView>(R.id.detailDescriptionFull).text = "This description is deliberately long. ".repeat(20)
        root.findViewById<View>(R.id.detailScreenshotsRow).visibility = View.VISIBLE
        root.findViewById<TextView>(R.id.detailErrorFull).apply {
            visibility = View.VISIBLE
            text = "Wrong app blocked: this app should be com.example.app but the server sent com.other.app. ".repeat(3)
        }
        root.findViewById<View>(R.id.detailProgressGroup).visibility = View.VISIBLE
        root.findViewById<TextView>(R.id.detailProgressLabel).text = "Downloading · 100% · 512.0 MB of 512.0 MB"
        root.findViewById<Button>(R.id.installFullButton).text = "Downloading 100%"
        root.findViewById<View>(R.id.uninstallFullButton).visibility = View.VISIBLE
        layoutScreen(root)

        val safe = safeArea()
        val ids = mapOf(
            "artwork" to R.id.detailArtworkTile,
            "column" to R.id.detailColumn,
            "name" to R.id.detailNameFull,
            "description" to R.id.detailDescriptionFull,
            "screenshots" to R.id.detailScreenshotsRow,
            "error" to R.id.detailErrorFull,
            "progress" to R.id.detailProgressGroup,
            "buttons" to R.id.detailButtons,
            "install" to R.id.installFullButton,
            "uninstall" to R.id.uninstallFullButton,
            "back" to R.id.backFullButton
        )
        ids.forEach { (name, id) -> assertInside(name, safe, boundsIn(root, root.findViewById(id))) }

        val buttons = listOf(R.id.installFullButton, R.id.uninstallFullButton, R.id.backFullButton)
            .map { boundsIn(root, root.findViewById(it)) }
        assertNoOverlap("detail buttons", buttons)
        buttons.forEach { assertTrue("button has no height", it.height() >= dp(48f)) }

        // Every text that must stay on one line does.
        listOf(R.id.installFullButton, R.id.uninstallFullButton, R.id.backFullButton, R.id.detailNameFull)
            .forEach { assertEquals("view $it wrapped", 1, root.findViewById<TextView>(it).lineCount) }
    }

    @Test
    fun detailButtonsNeverLetFocusEscapeVertically() {
        val root = LayoutInflater.from(context).inflate(R.layout.activity_main, null)
        listOf(R.id.installFullButton, R.id.uninstallFullButton, R.id.backFullButton).forEach { id ->
            val button = root.findViewById<View>(id)
            assertEquals(id, button.nextFocusUpId)
            assertEquals(id, button.nextFocusDownId)
        }
        assertEquals(R.id.backFullButton, root.findViewById<View>(R.id.backFullButton).nextFocusRightId)
        assertEquals(R.id.installFullButton, root.findViewById<View>(R.id.installFullButton).nextFocusLeftId)
    }

    @Test
    fun allVectorIconsAndDesignDrawablesInflate() {
        val drawables = listOf(
            R.drawable.ic_nav_free, R.drawable.ic_nav_subscription, R.drawable.ic_nav_adult,
            R.drawable.ic_nav_tools, R.drawable.ic_nav_updates, R.drawable.ic_refresh,
            R.drawable.ic_action_install, R.drawable.ic_action_update, R.drawable.ic_action_open,
            R.drawable.ic_action_done, R.drawable.ic_action_uninstall, R.drawable.ic_action_back,
            R.drawable.ic_featured, R.drawable.bg_nav_pill, R.drawable.bg_refresh,
            R.drawable.bg_btn_primary, R.drawable.bg_btn_secondary, R.drawable.bg_btn_destructive,
            R.drawable.bg_card_focus, R.drawable.bg_progress, R.drawable.bg_dialog,
            R.drawable.bg_dialog_button_primary, R.drawable.bg_dialog_button_secondary,
            R.drawable.bg_featured_card, R.drawable.bg_details_backdrop, R.drawable.bg_zappix,
            R.drawable.bg_card_surface, R.drawable.bg_card_art_sheen, R.drawable.bg_details_art_tile,
            R.drawable.bg_logo_tile, R.drawable.bg_logo_image, R.drawable.bg_header_panel,
            R.drawable.bg_section_accent
        )
        drawables.forEach { id ->
            val drawable = ContextCompat.getDrawable(context, id)
            assertNotNull("drawable $id failed to load", drawable)
        }
        val progress = LayoutInflater.from(ContextThemeWrapper(context, R.style.Theme_Zappix_Dialog))
            .inflate(R.layout.dialog_progress, null)
        assertNotNull(progress.findViewById(R.id.dialogProgress))
    }
}
