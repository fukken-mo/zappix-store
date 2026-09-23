package com.zappix.store

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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

                    StoreSection(
                        title = "Free Apps",
                        subtitle = "Install and start using",
                        apps = free,
                        onOpen = { selected = it }
                    )

                    Spacer(Modifier.height(38.dp))

                    StoreSection(
                        title = "Subscription Apps",
                        subtitle = "Premium services and memberships",
                        apps = subscription,
                        onOpen = { selected = it }
                    )

                    if (adult.isNotEmpty()) {
                        Spacer(Modifier.height(38.dp))

                        StoreSection(
                            title = "Adult Apps",
                            subtitle = "18+ apps",
                            apps = adult,
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
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 42.dp,
                    top = 18.dp,
                    bottom = 18.dp
                )
            ) {
                items(apps, key = { it.id }) { app ->
                    AppCard(app, onClick = { onOpen(app) })
                }
            }
        }
    }
}

@Composable
private fun AppCard(app: StoreApp, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (focused) 1.07f else 1f,
        animationSpec = tween(150),
        label = "cardScale"
    )
    val lift by animateDpAsState(
        targetValue = if (focused) 8.dp else 0.dp,
        animationSpec = tween(150),
        label = "cardLift"
    )
    val focusAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(140),
        label = "focusAlpha"
    )

    val shape = RoundedCornerShape(28.dp)

    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = shape,
        modifier = Modifier
            .size(210.dp)
            .zIndex(if (focused) 10f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = -lift.toPx()
            }
            .shadow(
                elevation = if (focused) 24.dp else 6.dp,
                shape = shape,
                ambientColor = if (focused) ElectricBlue else Color.Black,
                spotColor = if (focused) Blue else Color.Black
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Color(0xFF0A111D))
        ) {
            AsyncImage(
                model = app.iconUrl,
                contentDescription = app.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color(0xCC050912)
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .alpha(focusAlpha)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Blue.copy(alpha = 0.34f),
                                ElectricBlue.copy(alpha = 0.24f),
                                Violet.copy(alpha = 0.18f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 15.dp)
            ) {
                Text(
                    app.name,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    when (app.type) {
                        AppType.FREE -> "FREE"
                        AppType.SUBSCRIPTION -> app.priceLabel ?: "SUBSCRIPTION"
                        AppType.ADULT -> app.priceLabel ?: "18+"
                    },
                    color = when (app.type) {
                        AppType.FREE -> Color(0xFF86E8FF)
                        AppType.SUBSCRIPTION -> Color(0xFFC2B5FF)
                        AppType.ADULT -> Color(0xFFFF8A9A)
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
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
