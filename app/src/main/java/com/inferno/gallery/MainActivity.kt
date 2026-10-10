package com.inferno.gallery

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.inferno.gallery.data.db.DatabaseProvider
import com.inferno.gallery.ui.NavigationGraph
import com.inferno.gallery.ui.SettingsViewModel
import com.inferno.gallery.ui.ThemeMode
import com.inferno.gallery.ui.theme.PhotonGalleryTheme
import com.inferno.gallery.ui.theme.contrastPresetToDouble
import com.materialkolor.PaletteStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface StartupState {
    data class Checking(val message: String) : StartupState
    data class Failed(val title: String, val detail: String) : StartupState
    data object Ready : StartupState
}

class MainActivity : FragmentActivity() {

    private var startupState by mutableStateOf<StartupState>(
        StartupState.Checking("Starting Pitchcraft…")
    )
    private var settingsViewModel: SettingsViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Never let the system splash hide a startup exception.
        splashScreen.setKeepOnScreenCondition { false }

        tryEnablePeakRefreshRate()
        enableEdgeToEdge()

        setContent {
            when (val state = startupState) {
                is StartupState.Checking -> StartupCheckingScreen(state.message)
                is StartupState.Failed -> StartupFailedScreen(
                    title = state.title,
                    detail = state.detail,
                    onRetry = { runStartupChecks() }
                )
                StartupState.Ready -> {
                    val vm = settingsViewModel
                    if (vm == null) {
                        StartupFailedScreen(
                            title = "Settings failed to initialize",
                            detail = "Pitchcraft reached the UI but SettingsViewModel was unavailable.",
                            onRetry = { runStartupChecks() }
                        )
                    } else {
                        PitchcraftContent(vm)
                    }
                }
            }
        }

        runStartupChecks()
    }

    private fun runStartupChecks() {
        startupState = StartupState.Checking("Checking local gallery database…")

        lifecycleScope.launch {
            val infrastructure = withContext(Dispatchers.IO) {
                runCatching {
                    // Open Room explicitly after the first UI frame. This makes database
                    // migration/open errors visible instead of killing the launcher.
                    val db = DatabaseProvider.getDatabase(applicationContext)
                    val raw = db.openHelper.writableDatabase
                    raw.query("PRAGMA quick_check").use { cursor ->
                        if (!cursor.moveToFirst()) {
                            error("Database quick check returned no result")
                        }
                        val result = cursor.getString(0)
                        if (!result.equals("ok", ignoreCase = true)) {
                            error("Database quick check failed: $result")
                        }
                    }

                    // Verify WorkManager can be resolved before any worker is scheduled.
                    WorkManager.getInstance(applicationContext)
                    Unit
                }
            }

            if (infrastructure.isFailure) {
                val error = infrastructure.exceptionOrNull()
                startupState = StartupState.Failed(
                    title = "Local startup check failed",
                    detail = buildErrorText(error)
                )
                return@launch
            }

            startupState = StartupState.Checking("Loading gallery settings…")

            val vmResult = runCatching {
                ViewModelProvider(this@MainActivity)[SettingsViewModel::class.java]
            }
            if (vmResult.isFailure) {
                startupState = StartupState.Failed(
                    title = "Settings startup failed",
                    detail = buildErrorText(vmResult.exceptionOrNull())
                )
                return@launch
            }

            settingsViewModel = vmResult.getOrThrow()

            // Background jobs are deliberately scheduled only after the app has a
            // healthy database, WorkManager and ViewModel.
            runCatching {
                (application as? GalleryApplication)?.scheduleBackgroundWork()
            }.onFailure {
                android.util.Log.e("MainActivity", "Background jobs could not be scheduled", it)
            }

            startupState = StartupState.Ready
        }
    }

    private fun buildErrorText(error: Throwable?): String {
        if (error == null) return "Unknown startup error"
        val root = generateSequence(error) { it.cause }.last()
        return buildString {
            append(error.javaClass.name)
            error.message?.takeIf { it.isNotBlank() }?.let {
                append("\n")
                append(it)
            }
            if (root !== error) {
                append("\n\nRoot cause: ")
                append(root.javaClass.name)
                root.message?.takeIf { it.isNotBlank() }?.let {
                    append("\n")
                    append(it)
                }
            }
        }
    }

    private fun tryEnablePeakRefreshRate() {
        try {
            val peakRefreshRate = display?.supportedModes
                ?.map { it.refreshRate }
                ?.maxOrNull() ?: 60f
            if (peakRefreshRate > 60f) {
                val layoutParams = window.attributes
                runCatching {
                    val minField = layoutParams.javaClass
                        .getField("preferredMinDisplayRefreshRate")
                    val maxField = layoutParams.javaClass
                        .getField("preferredMaxDisplayRefreshRate")
                    minField.set(layoutParams, peakRefreshRate)
                    maxField.set(layoutParams, peakRefreshRate)
                    window.attributes = layoutParams
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Peak refresh rate setup skipped", e)
        }
    }

    @Composable
    private fun PitchcraftContent(settingsViewModel: SettingsViewModel) {
        val isLoading by settingsViewModel.isLoading.collectAsState()
        val useFullScreen by settingsViewModel.useFullScreen.collectAsState()
        val themeMode by settingsViewModel.themeMode.collectAsState()
        val useMaterialYou by settingsViewModel.useMaterialYou.collectAsState()
        val useAmoledBlack by settingsViewModel.useAmoledBlack.collectAsState()
        val appSeedColor by settingsViewModel.appSeedColor.collectAsState()
        val themePaletteStyleStr by settingsViewModel.themePaletteStyle.collectAsState()
        val themeContrastLevel by settingsViewModel.themeContrastLevel.collectAsState()
        val invertThemeColors by settingsViewModel.invertThemeColors.collectAsState()
        val useSystemFont by settingsViewModel.useSystemFont.collectAsState()
        val secureRecentsEnabled by settingsViewModel.secureRecentsEnabled.collectAsState()
        val hapticsEnabled by settingsViewModel.hapticsEnabled.collectAsState()
        val hapticsStrength by settingsViewModel.hapticsStrength.collectAsState()
        val secondaryColorOverride by settingsViewModel.secondaryColorOverride.collectAsState()
        val tertiaryColorOverride by settingsViewModel.tertiaryColorOverride.collectAsState()
        val animateThemeTransitions by settingsViewModel.animateThemeTransitions.collectAsState()

        com.inferno.gallery.ui.utils.PremiumHapticsManager.enabled = hapticsEnabled
        com.inferno.gallery.ui.utils.PremiumHapticsManager.strength = hapticsStrength

        val themePaletteStyle = runCatching {
            PaletteStyle.valueOf(themePaletteStyleStr)
        }.getOrDefault(PaletteStyle.TonalSpot)

        val isSystemDark = isSystemInDarkTheme()
        val isDark = when (themeMode) {
            ThemeMode.SYSTEM -> isSystemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

        LaunchedEffect(isDark, useFullScreen) {
            val style = if (isDark) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                )
            }
            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)

            val insetsController =
                WindowCompat.getInsetsController(window, window.decorView)
            if (useFullScreen) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        LaunchedEffect(secureRecentsEnabled) {
            if (secureRecentsEnabled) {
                window.setFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SECURE,
                    android.view.WindowManager.LayoutParams.FLAG_SECURE
                )
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
        }

        PhotonGalleryTheme(
            darkTheme = isDark,
            dynamicColor = useMaterialYou,
            useAmoledBlack = useAmoledBlack,
            appSeedColor = appSeedColor,
            paletteStyle = themePaletteStyle,
            contrastLevel = themeContrastLevel.toDouble(),
            invertColors = invertThemeColors,
            useSystemFont = useSystemFont,
            secondaryColorOverride = secondaryColorOverride,
            tertiaryColorOverride = tertiaryColorOverride,
            animateTransitions = animateThemeTransitions
        ) {
            CompositionLocalProvider(
                androidx.compose.foundation.LocalOverscrollFactory provides null
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                ) {
                    NavigationGraph(
                        isLoading = isLoading,
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun StartupCheckingScreen(message: String) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF7F9FF)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = Color(0xFFE7EFFF),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF0A6EFF))
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "Pitchcraft",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF101318)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF677080)
                )
            }
        }
    }
}

@Composable
private fun StartupFailedScreen(
    title: String,
    detail: String,
    onRetry: () -> Unit
) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF7F9FF)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Pitchcraft startup diagnostics",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0A6EFF)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = Color(0xFFFFFFFF),
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 1.dp
                ) {
                    Text(
                        detail,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF333842)
                    )
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Retry startup")
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Your original photos are not stored inside Pitchcraft and are not changed by this diagnostic screen.",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF677080)
                )
            }
        }
    }
}
