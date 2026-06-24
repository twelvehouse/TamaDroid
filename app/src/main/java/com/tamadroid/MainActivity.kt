package com.tamadroid

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamadroid.core.TamaCore
import com.tamadroid.data.AppPrefs
import com.tamadroid.data.RomRepository
import com.tamadroid.data.SaveStore
import com.tamadroid.service.TamaRuntime
import com.tamadroid.service.TamaService
import com.tamadroid.ui.PlayBgEditor
import com.tamadroid.ui.SettingsScreen
import com.tamadroid.ui.TamaScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

@Composable
private fun App() {
    val ctx = LocalContext.current
    val repo = remember { RomRepository(ctx) }
    var hasRom by remember { mutableStateOf(repo.hasRom()) }
    var showSettings by remember { mutableStateOf(false) }
    var replacingRom by remember { mutableStateOf(false) }
    var editingPlayBg by remember { mutableStateOf(false) }

    when {
        // First run: no ROM yet.
        !hasRom -> ImportScreen(repo, onLoaded = { hasRom = true })

        // Replace flow: NON-destructive until a new ROM actually loads. Cancel keeps the
        // current pet untouched.
        replacingRom -> ImportScreen(
            repo,
            onLoaded = {
                TamaService.stop(ctx)
                TamaRuntime.shutdown()
                SaveStore(ctx).clear()      // only now: a new ROM was successfully loaded
                replacingRom = false
            },
            onCancel = { replacingRom = false }
        )

        editingPlayBg -> PlayBgEditor(
            onDone = { editingPlayBg = false; showSettings = true },
            onCancel = { editingPlayBg = false; showSettings = true }
        )

        showSettings -> SettingsScreen(
            onBack = { showSettings = false },
            onChangeRom = { showSettings = false; replacingRom = true },
            onEditImage = { showSettings = false; editingPlayBg = true }
        )

        else -> EmulatorScreen(onOpenSettings = { showSettings = true })
    }
}

@Composable
private fun ImportScreen(repo: RomRepository, onLoaded: () -> Unit, onCancel: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pasteOpen by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }
    var urlOpen by remember { mutableStateOf(false) }
    var urlText by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                repo.importText(text)
            }.onSuccess { error = null; onLoaded() }.onFailure { error = it.message }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("TamaDroid", style = MaterialTheme.typography.headlineMedium)
        Text("Load a P1/P2 ROM (u12_t text: 0xFA2, 0xC87, ...).")
        Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy) { Text("Load from file") }
        Button(onClick = { pasteOpen = !pasteOpen }, enabled = !busy) { Text("Paste text") }
        if (pasteOpen) {
            OutlinedTextField(
                value = pasteText,
                onValueChange = { pasteText = it },
                label = { Text("0xFA2, 0xC87, ...") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = {
                runCatching { repo.importText(pasteText) }
                    .onSuccess { error = null; onLoaded() }
                    .onFailure { error = it.message }
            }) { Text("Load") }
        }
        Button(onClick = { urlOpen = !urlOpen }, enabled = !busy) { Text("Load from URL") }
        if (urlOpen) {
            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                label = { Text("Pastebin raw URL, etc.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(enabled = !busy && urlText.isNotBlank(), onClick = {
                busy = true; error = null
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { repo.importFromUrl(urlText) } }
                        .onSuccess { busy = false; onLoaded() }
                        .onFailure { busy = false; error = it.message ?: "Download failed" }
                }
            }) { Text("Download & load") }
        }
        if (busy) Text("Downloading…")
        error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
        if (onCancel != null) TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

@Composable
private fun EmulatorScreen(onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current

    remember { TamaRuntime.ensureStarted(ctx) }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        TamaService.start(ctx)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> TamaRuntime.setMuted(false)
                Lifecycle.Event.ON_STOP -> { TamaRuntime.setMuted(true); TamaRuntime.requestSave() }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val frame = TamaRuntime.frame?.collectAsStateWithLifecycle()?.value
    // A custom play background image auto-hides the device frame.
    val bgRes = if (AppPrefs.hasPlayImage(ctx)) null
        else com.tamadroid.ui.backgroundDrawable(AppPrefs.background(ctx))
    val effect = AppPrefs.effect(ctx)
    val dotColor = AppPrefs.lcdDot(ctx)
    val playBg = AppPrefs.playBgColor(ctx)
    val buttonColor = AppPrefs.playButtonColor(ctx)
    val playImage = AppPrefs.playImage(ctx)
    val playBmp = remember(playImage) {
        if (playImage != null)
            android.graphics.BitmapFactory.decodeFile(
                java.io.File(ctx.filesDir, AppPrefs.PLAY_BG_IMAGE_FILE).absolutePath
            )?.asImageBitmap()
        else null
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(playBg))) {
        if (playBmp != null && playImage != null) {
            Image(
                bitmap = playBmp,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer(
                    translationX = playImage.offsetX, translationY = playImage.offsetY,
                    scaleX = playImage.scale, scaleY = playImage.scale
                )
            )
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (frame != null) {
                TamaScreen(frame.fb, frame.icons, bgRes, effect, dotColor, modifier = Modifier.fillMaxWidth())
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)
            ) {
                TamaButton(TamaCore.BTN_LEFT, buttonColor)
                TamaButton(TamaCore.BTN_MIDDLE, buttonColor)
                TamaButton(TamaCore.BTN_RIGHT, buttonColor)
            }
        }
        FloatingActionButton(
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings")
        }
    }
}

@Composable
private fun TamaButton(btn: Int, color: Int) {
    // Collect press/release EVENTS (a quick tap toggles the pressed *state* within one
    // frame, so observing state misses it — but every PressInteraction is delivered).
    // No labels: the real device buttons are unlabelled.
    val interaction = remember { MutableInteractionSource() }
    LaunchedEffect(interaction) {
        interaction.interactions.collect { i ->
            when (i) {
                is PressInteraction.Press -> TamaRuntime.press(btn, true)
                is PressInteraction.Release -> TamaRuntime.press(btn, false)
                is PressInteraction.Cancel -> TamaRuntime.press(btn, false)
            }
        }
    }
    Button(
        onClick = {},
        interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(containerColor = Color(color)),
        modifier = Modifier.size(56.dp),
        contentPadding = PaddingValues(0.dp)
    ) {}
}
