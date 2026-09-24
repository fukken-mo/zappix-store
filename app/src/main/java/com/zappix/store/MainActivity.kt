package com.zappix.store

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private val Bg = Color(0xFF05070D)
private val SurfaceDark = Color(0xFF0C1220)
private val SurfaceSoft = Color(0xFF111A2B)
private val Blue = Color(0xFF18C8FF)
private val ElectricBlue = Color(0xFF2F6BFF)
private val Violet = Color(0xFF7A5CFF)
private val TextPrimary = Color(0xFFF4F7FB)
private val TextSecondary = Color(0xFF95A3B7)
private val TextMuted = Color(0xFF66758A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Bg,
                    surface = SurfaceDark,
                    primary = Blue
                )
            ) {
                ZappixStore()
            }
        }
    }
}

@Composable
private fun PremiumBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0x332F6BFF),
                        Color(0x140B2A52),
                        Color.Transparent
                    ),
                    radius = 1000f
                )
            )
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF07101F),
                        Bg,
                        Color(0xFF03050A)
                    )
                )
            ),
        content = content
    )
}

@Composable
private fun ZappixStore(vm: StoreViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<StoreApp?>(null) }

    if (selected != null) {
        AppDetails(app = selected!!, onBack = { selected = null })
        return
    }

    PremiumBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 62.dp, vertical = 30.dp)
        ) {
            Header(onRefresh = vm::refresh)
            Spacer(Modifier.height(30.dp))

            when {
                state.loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Blue)
                }

                state.error != null -> ErrorState(state.error!!, vm::refresh)

                else -> {
                    val free = state.apps.filter { it.type == AppType.FREE }
                    val subscription = state.apps.filter { it.type == AppType.SUBSCRIPTION }
                    val adult = state.apps.filter { it.type == AppType.ADULT }

                    val freeFocus = remember(free.map { it.id }) {
                        List(free.size) { FocusRequester() }
                    }
                    val subscriptionFocus = remember(subscription.map { it.id }) {
                        List(subscription.size) { FocusRequester() }
                    }
                    val adultFocus = remember(adult.map { it.id }) {
                        List(adult.size) { FocusRequester() }
                    }

                    fun targetAt(list: List<FocusRequester>, index: Int): FocusRequester? =
                        list.getOrNull(index.coerceAtMost((list.size - 1).coerceAtLeast(0)))

                    StoreSection(
                        title = "Free Apps",
                        subtitle = "Install and start using",
                        apps = free,
                        focusRequesters = freeFocus,
                        upTargets = emptyList(),
                        downTargets = free.indices.map { index ->
                            when {
                                subscriptionFocus.isNotEmpty() -> targetAt(subscriptionFocus, index)
                                adultFocus.isNotEmpty() -> targetAt(adultFocus, index)
                                else -> null
                            }
                        },
                        onOpen = { selected = it }
                    )

                    Spacer(Modifier.height(38.dp))

                    StoreSection(
                        title = "Subscription Apps",
                        subtitle = "Premium services and memberships",
                        apps = subscription,
                        focusRequesters = subscriptionFocus,
                        upTargets = subscription.indices.map { index ->
                            if (freeFocus.isNotEmpty()) targetAt(freeFocus, index) else null
                        },
                        downTargets = subscription.indices.map { index ->
                            if (adultFocus.isNotEmpty()) targetAt(adultFocus, index) else null
                        },
                        onOpen = { selected = it }
                    )

                    if (adult.isNotEmpty()) {
                        Spacer(Modifier.height(38.dp))

                        StoreSection(
                            title = "Adult Apps",
                            subtitle = "18+ apps",
                            apps = adult,
                            focusRequesters = adultFocus,
                            upTargets = adult.indices.map { index ->
                                when {
                                    subscriptionFocus.isNotEmpty() -> targetAt(subscriptionFocus, index)
                                    freeFocus.isNotEmpty() -> targetAt(freeFocus, index)
                                    else -> null
                                }
                            },
                            downTargets = emptyList(),
                            onOpen = { selected = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(onRefresh: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .shadow(18.dp, RoundedCornerShape(18.dp), ambientColor = Blue, spotColor = Blue)
                .clip(RoundedCornerShape(18.dp))
        ) {
            Image(
                painter = painterResource(R.drawable.zappix_icon),
                contentDescription = "Zappix",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(Modifier.width(16.dp))

        Column {
            Text(
                "Zappix",
                color = TextPrimary,
                fontSize = 31.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.2.sp
            )
            Text(
                "Your apps. One place.",
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.35.sp
            )
        }

        Spacer(Modifier.weight(1f))
        FocusButton("Refresh", onClick = onRefresh, compact = true)
    }
}

@Composable
private fun StoreSection(
    title: String,
    subtitle: String,
    apps: List<StoreApp>,
    focusRequesters: List<FocusRequester>,
    upTargets: List<FocusRequester?>,
    downTargets: List<FocusRequester?>,
    onOpen: (StoreApp) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Column {
                Text(
                    title,
                    color = TextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    letterSpacing = 0.15.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    color = TextSecondary,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp
                )
            }

            if (apps.isNotEmpty()) {
                Spacer(Modifier.width(14.dp))
                Surface(
                    color = Color.White.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        apps.size.toString(),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (apps.isEmpty()) {
            Surface(
                color = Color.White.copy(alpha = 0.025f),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.width(410.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 17.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(TextMuted)
                    )
                    Spacer(Modifier.width(11.dp))
                    Text(
                        "No apps in this section yet",
                        color = TextMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    end = 54.dp,
                    top = 30.dp,
                    bottom = 30.dp
                )
            ) {
                items(apps.size, key = { apps[it].id }) { index ->
                    val app = apps[index]
                    AppCard(
                        app = app,
                        focusRequester = focusRequesters[index],
                        upTarget = upTargets.getOrNull(index),
                        downTarget = downTargets.getOrNull(index),
                        onClick = { onOpen(app) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppCard(
    app: StoreApp,
    focusRequester: FocusRequester,
    upTarget: FocusRequester?,
    downTarget: FocusRequester?,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (focused) 1.10f else 1f,
        animationSpec = tween(145),
        label = "cardScale"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(130),
        label = "cardGlow"
    )
    val veilAlpha by animateFloatAsState(
        targetValue = if (focused) 0.12f else 0f,
        animationSpec = tween(120),
        label = "cardVeil"
    )

    val cardShape = RoundedCornerShape(24.dp)

    // The outer slot NEVER changes size. This keeps the LazyRow completely
    // stationary while only the artwork is transformed for the 3D focus effect.
    Column(
        modifier = Modifier
            .width(244.dp)
            .height(276.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(230.dp)
                .zIndex(if (focused) 10f else 0f),
            contentAlignment = Alignment.Center
        ) {
            // Soft light bloom sits inside reserved space, so it cannot be cut off
            // and never changes row measurement.
            Box(
                modifier = Modifier
                    .size(214.dp)
                    .alpha(glowAlpha)
                    .shadow(
                        elevation = 34.dp,
                        shape = RoundedCornerShape(30.dp),
                        ambientColor = ElectricBlue,
                        spotColor = Violet,
                        clip = false
                    )
                    .clip(RoundedCornerShape(30.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Blue.copy(alpha = 0.32f),
                                ElectricBlue.copy(alpha = 0.20f),
                                Violet.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Surface(
                onClick = onClick,
                color = Color(0xFF08101C),
                shape = cardShape,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (focused) Blue.copy(alpha = 0.42f)
                    else Color.White.copy(alpha = 0.06f)
                ),
                modifier = Modifier
                    .size(202.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationY = if (focused) -4.dp.toPx() else 0f
                    }
                    .shadow(
                        elevation = if (focused) 18.dp else 5.dp,
                        shape = cardShape,
                        ambientColor = if (focused) ElectricBlue.copy(alpha = 0.55f) else Color.Black,
                        spotColor = if (focused) Violet.copy(alpha = 0.45f) else Color.Black,
                        clip = false
                    )
                    .focusRequester(focusRequester)
                    .focusProperties {
                        upTarget?.let { up = it }
                        downTarget?.let { down = it }
                    }
                    .onFocusChanged { focused = it.isFocused }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(cardShape)
                        .background(Color(0xFF08101C))
                ) {
                    AsyncImage(
                        model = app.iconUrl,
                        contentDescription = app.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Premium glass veil, intentionally subtle so artwork stays clear.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .alpha(veilAlpha)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Blue.copy(alpha = 0.70f),
                                        ElectricBlue.copy(alpha = 0.42f),
                                        Violet.copy(alpha = 0.30f)
                                    )
                                )
                            )
                    )

                    // Small highlight for depth instead of a heavy outline.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .alpha(if (focused) 0.22f else 0.08f)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.28f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = app.name,
            color = if (focused) Color.White else Color(0xFFD8E0EC),
            fontWeight = if (focused) FontWeight.ExtraBold else FontWeight.SemiBold,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(214.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun AppDetails(app: StoreApp, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val installer = remember { ApkInstaller(context.applicationContext) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)

    PremiumBackground {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 72.dp, vertical = 54.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(290.dp)
                    .shadow(34.dp, RoundedCornerShape(42.dp), ambientColor = Blue, spotColor = ElectricBlue)
                    .clip(RoundedCornerShape(42.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Blue.copy(alpha = 0.20f),
                                ElectricBlue.copy(alpha = 0.12f),
                                Violet.copy(alpha = 0.14f)
                            )
                        )
                    )
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = app.iconUrl,
                    contentDescription = app.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(34.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(Modifier.width(64.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    "APP DETAILS",
                    color = Blue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    app.name,
                    color = TextPrimary,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp
                )

                Spacer(Modifier.height(12.dp))

                Surface(
                    color = when (app.type) {
                        AppType.FREE -> Blue.copy(alpha = 0.14f)
                        AppType.SUBSCRIPTION -> Violet.copy(alpha = 0.16f)
                        AppType.ADULT -> Color(0xFFFF4D67).copy(alpha = 0.16f)
                    },
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        when (app.type) {
                            AppType.FREE -> "FREE"
                            AppType.SUBSCRIPTION -> app.priceLabel ?: "SUBSCRIPTION"
                            AppType.ADULT -> app.priceLabel ?: "18+"
                        },
                        color = when (app.type) {
                            AppType.FREE -> Color(0xFF7DE3FF)
                            AppType.SUBSCRIPTION -> Color(0xFFBEAEFF)
                            AppType.ADULT -> Color(0xFFFF8A9A)
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
                    )
                }

                Spacer(Modifier.height(28.dp))

                Text(
                    app.description,
                    color = Color(0xFFD3DAE6),
                    fontSize = 18.sp,
                    lineHeight = 28.sp,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(36.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FocusButton(
                        text = if (downloading) "Downloading $progress%" else "Download / Install",
                        enabled = !downloading,
                        onClick = {
                            downloading = true
                            error = null
                            scope.launch {
                                installer.downloadAndOpenInstaller(app) { progress = it }
                                    .onFailure { error = it.message ?: "Download failed" }
                                downloading = false
                            }
                        }
                    )
                    FocusButton("Back", onClick = onBack, secondary = true)
                }

                if (error != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(error!!, color = Color(0xFFFF8C8C), fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun FocusButton(
    text: String,
    enabled: Boolean = true,
    secondary: Boolean = false,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        if (focused) 1.06f else 1f,
        animationSpec = tween(130),
        label = "buttonScale"
    )

    val bg by animateColorAsState(
        when {
            !enabled -> Color(0xFF273044)
            focused -> if (secondary) Color(0xFF4E43B7) else Color(0xFF087ED3)
            else -> if (secondary) Color(0xFF171E2D) else Color(0xFF0B4168)
        },
        animationSpec = tween(130),
        label = "buttonColor"
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = bg),
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                if (focused) 18.dp else 0.dp,
                RoundedCornerShape(14.dp),
                ambientColor = if (secondary) Violet else Blue,
                spotColor = if (secondary) Violet else Blue
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(
            horizontal = if (compact) 20.dp else 28.dp,
            vertical = if (compact) 11.dp else 15.dp
        )
    ) {
        Text(
            text,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontSize = if (compact) 14.sp else 16.sp
        )
    }
}

@Composable
private fun ErrorState(message: String, retry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            color = SurfaceSoft.copy(alpha = 0.88f),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 42.dp, vertical = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Unable to load Zappix",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(10.dp))
                Text(message, color = TextSecondary, fontSize = 15.sp)
                Spacer(Modifier.height(22.dp))
                FocusButton("Try Again", onClick = retry)
            }
        }
    }
}
