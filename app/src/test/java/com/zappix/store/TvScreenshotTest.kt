package com.zappix.store

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.View.MeasureSpec
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Renders the real home and Details XML on the 960x540dp TV canvas to JPEGs (CI preview only).
 * Runs only when ZAPPIX_SCREENSHOTS=1; the CI workflow prints the images into its log.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvScreenshotTest {

    private val names = listOf("StreamBox Pro", "Cinema Plus", "Kids Planet", "Sports Live", "Music Wave", "News 24")
    private val colors = listOf(0xFF1E88E5, 0xFFE53935, 0xFF43A047, 0xFFFB8C00, 0xFF8E24AA, 0xFF00ACC1)

    private fun fakeArt(index: Int): BitmapDrawable {
        val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(colors[index % colors.size].toInt())
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 120f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        c.drawText(names[index % names.size].take(1), 128f, 170f, p)
        return BitmapDrawable(null, bmp)
    }

    private fun app(i: Int, type: AppType = AppType.FREE) = StoreApp(
        id = i + 1, name = names[i % names.size], description = "", iconUrl = "", downloadUrl = "https://x/a.apk",
        packageName = "com.example.app$i", versionName = "2.4.1", versionCode = 10, type = type,
        priceLabel = if (type == AppType.SUBSCRIPTION) "\$4.99 / month" else null
    )

    private fun save(name: String, root: View, width: Int, height: Int) {
        val full = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(full)
        ContextCompat.getDrawable(root.context, R.drawable.bg_zappix)!!.apply { setBounds(0, 0, width, height); draw(canvas) }
        root.draw(canvas)
        val half = Bitmap.createScaledBitmap(full, width / 2, height / 2, true)
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.jpg").outputStream().use { half.compress(Bitmap.CompressFormat.JPEG, 82, it) }
    }

    private fun settle() = ShadowLooper.idleMainLooper(600, TimeUnit.MILLISECONDS)

    @Test
    fun renderHomeAndDetails() {
        assumeTrue(System.getenv("ZAPPIX_SCREENSHOTS") == "1")
        val activity = Robolectric.buildActivity(Activity::class.java).create().start().resume().get()
        activity.setTheme(R.style.Theme_Zappix)
        activity.setContentView(R.layout.activity_main)
        val root = activity.findViewById<View>(android.R.id.content)
        val width = activity.resources.displayMetrics.widthPixels
        val height = activity.resources.displayMetrics.heightPixels

        listOf(R.id.adultTab, R.id.toolsTab, R.id.updatesTab).forEach { activity.findViewById<View>(it).visibility = View.VISIBLE }
        activity.findViewById<TextView>(R.id.updatesTab).text = "Updates · 2"
        activity.findViewById<View>(R.id.loading).visibility = View.GONE
        activity.findViewById<View>(R.id.freeTab).isSelected = true

        val recycler = activity.findViewById<RecyclerView>(R.id.appsRecycler)
        val adapter = AppAdapter(onFocused = { _, _ -> }, onClicked = {}, onUpPressed = { false })
        recycler.layoutManager = LinearLayoutManager(activity, RecyclerView.HORIZONTAL, false)
        recycler.adapter = adapter
        adapter.submitList(
            (0 until 6).map { i ->
                AppItem(app(i), when (i) { 1 -> InstallState.INSTALLED; 3 -> InstallState.UPDATE; else -> InstallState.NOT_INSTALLED })
            }
        )

        fun layoutAll() {
            root.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
        }
        fun paintArt() {
            for (i in 0 until recycler.childCount) {
                val child = recycler.getChildAt(i)
                val pos = recycler.getChildAdapterPosition(child)
                child.findViewById<ImageView>(R.id.appArtwork).setImageDrawable(fakeArt(pos))
            }
        }

        settle(); layoutAll(); settle(); layoutAll(); paintArt()
        recycler.getChildAt(1)?.requestFocusFromTouch()
        settle(); layoutAll(); paintArt()
        save("home_card_focused", root, width, height)

        activity.findViewById<View>(R.id.subscriptionTab).requestFocusFromTouch()
        settle(); layoutAll(); paintArt()
        save("home_tab_focused", root, width, height)

        activity.findViewById<View>(R.id.refreshButton).requestFocusFromTouch()
        settle(); layoutAll(); paintArt()
        save("home_refresh_focused", root, width, height)

        // Details: worst-ish realistic state (installed app with an update downloading).
        activity.findViewById<View>(R.id.mainContent).visibility = View.INVISIBLE
        activity.findViewById<View>(R.id.detailsOverlay).visibility = View.VISIBLE
        activity.findViewById<ImageView>(R.id.detailArtworkFull).setImageDrawable(fakeArt(0))
        activity.findViewById<TextView>(R.id.detailNameFull).text = "StreamBox Pro"
        activity.findViewById<TextView>(R.id.detailTypeFull).text = "\$4.99 / month"
        activity.findViewById<TextView>(R.id.detailMetaFull).text = "Version 2.4.1  •  38.6 MB"
        activity.findViewById<TextView>(R.id.detailDescriptionFull).text =
            "Live channels, movies and series in one place. Fast zapping, a full TV guide and profiles for the whole family, built for the big screen."
        activity.findViewById<View>(R.id.detailScreenshotsRow).visibility = View.VISIBLE
        listOf(R.id.detailScreenshot1, R.id.detailScreenshot2, R.id.detailScreenshot3).forEachIndexed { i, id ->
            activity.findViewById<ImageView>(id).setImageDrawable(fakeArt(i + 2))
        }
        activity.findViewById<View>(R.id.uninstallFullButton).visibility = View.VISIBLE
        activity.findViewById<Button>(R.id.installFullButton).apply { text = "Update"; requestFocusFromTouch() }
        settle(); layoutAll()
        save("details_primary_focused", root, width, height)

        activity.findViewById<View>(R.id.uninstallFullButton).requestFocusFromTouch()
        settle(); layoutAll()
        save("details_uninstall_focused", root, width, height)

        activity.findViewById<View>(R.id.detailProgressGroup).visibility = View.VISIBLE
        activity.findViewById<View>(R.id.uninstallFullButton).visibility = View.GONE
        activity.findViewById<android.widget.ProgressBar>(R.id.detailProgress).apply { isIndeterminate = false; progress = 42 }
        activity.findViewById<TextView>(R.id.detailProgressLabel).text = "Downloading · 42% · 16.2 MB of 38.6 MB"
        activity.findViewById<Button>(R.id.installFullButton).apply { text = "Downloading 42%"; isActivated = true; requestFocusFromTouch() }
        settle(); layoutAll()
        save("details_downloading", root, width, height)
    }
}
