package com.tamadroid.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamadroid.R
import com.tamadroid.core.TamaCore
import com.tamadroid.data.AppPrefs
import com.tamadroid.service.TamaRuntime
import com.tamadroid.widget.WidgetPrefs
import com.tamadroid.widget.WidgetRenderer
import com.tamadroid.widget.WidgetTheme
import com.tamadroid.widget.WidgetThemes

private val TABS = listOf("App", "LCD", "Widget", "Game")

@Composable
fun SettingsScreen(onBack: () -> Unit, onChangeRom: () -> Unit, onEditImage: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }
        TabRow(selectedTabIndex = tab) {
            TABS.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (tab) {
                0 -> PlaySection(onEditImage)
                1 -> LcdSection()
                2 -> WidgetSection()
                else -> {
                    GameSection()
                    HorizontalDivider()
                    RomSection(onChangeRom)
                    HorizontalDivider()
                    ResetSection(onResetComplete = onBack)
                }
            }
        }
    }
}

@Composable
private fun PlaySection(onEditImage: () -> Unit) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    var bgColor by remember { mutableStateOf(AppPrefs.playBgColor(ctx, dark)) }
    var btnColor by remember { mutableStateOf(AppPrefs.playButtonColor(ctx, dark)) }
    var frameBg by remember { mutableStateOf(AppPrefs.background(ctx)) }
    var hasImage by remember { mutableStateOf(AppPrefs.hasPlayImage(ctx)) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.File(ctx.filesDir, AppPrefs.PLAY_BG_IMAGE_FILE).outputStream().use { input.copyTo(it) }
                }
            }.onSuccess { hasImage = true; onEditImage() }
        }
    }

    Text("Device frame", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AppPrefs.Background.entries.forEach { b ->
            FilterChip(
                selected = frameBg == b,
                onClick = { frameBg = b; AppPrefs.setBackground(ctx, b) },
                label = { Text(if (b == AppPrefs.Background.BASALT) "Color" else "Mono") }
            )
        }
        Button(onClick = {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }) { Text(if (hasImage) "Image…" else "Image") }
    }
    if (hasImage) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEditImage) { Text("Edit position") }
            OutlinedButton(onClick = { AppPrefs.clearPlayImage(ctx); hasImage = false }) { Text("Remove image") }
        }
        Text("A custom image hides the device frame automatically.",
            style = MaterialTheme.typography.bodySmall)
    }

    ColorSwatchButton("Background color", bgColor) { bgColor = it; AppPrefs.setPlayBgColor(ctx, it) }
    ColorSwatchButton("Button color", btnColor) { btnColor = it; AppPrefs.setPlayButtonColor(ctx, it) }
}

@Composable
private fun GameSection() {
    val ctx = LocalContext.current
    var speed by remember { mutableStateOf(AppPrefs.gameSpeed(ctx)) }
    var vibrate by remember { mutableStateOf(AppPrefs.vibrate(ctx)) }
    Text("Game speed: ${speed}x", style = MaterialTheme.typography.titleMedium)
    Slider(
        value = speed.toFloat(),
        onValueChange = { speed = it.toInt() },
        onValueChangeFinished = { AppPrefs.setGameSpeed(ctx, speed); TamaRuntime.setSpeed(speed) },
        valueRange = AppPrefs.MIN_SPEED.toFloat()..AppPrefs.MAX_SPEED.toFloat(),
        steps = (AppPrefs.MAX_SPEED - AppPrefs.MIN_SPEED - 1)
    )
    Text("1x = real time. Higher = the pet lives faster.", style = MaterialTheme.typography.bodySmall)

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Vibration", style = MaterialTheme.typography.titleMedium)
        Switch(checked = vibrate, onCheckedChange = { vibrate = it; AppPrefs.setVibrate(ctx, it) })
    }
    Text("Short haptic feedback when you press A/B/C.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ResetSection(onResetComplete: () -> Unit) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    Text("Reset", style = MaterialTheme.typography.titleMedium)
    Text("Restores all settings to their defaults. Your pet and ROM are kept.")
    OutlinedButton(onClick = { confirm = true }) { Text("Reset settings") }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Reset settings?") },
            text = { Text("All settings go back to defaults. Your pet and ROM are NOT affected. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    AppPrefs.resetSettings(ctx)
                    WidgetPrefs.resetAll(ctx)
                    TamaRuntime.setSpeed(AppPrefs.gameSpeed(ctx))
                    TamaRuntime.setVsync(AppPrefs.inAppVsync(ctx))
                    confirm = false
                    onResetComplete()
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun LcdSection() {
    val ctx = LocalContext.current
    var glow by remember { mutableStateOf(AppPrefs.effect(ctx) == LcdEffect.GLOW) }
    var vsync by remember { mutableStateOf(AppPrefs.inAppVsync(ctx)) }
    var theme by remember { mutableStateOf(AppPrefs.lcdTheme(ctx)) }
    var custom by remember { mutableStateOf(AppPrefs.lcdCustomPresets(ctx)) }

    val flow = TamaRuntime.frame
    val frame = if (flow != null) flow.collectAsStateWithLifecycle().value else null
    val bgRes = backgroundDrawable(AppPrefs.background(ctx))
    if (frame != null) {
        Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            TamaScreen(
                frame.fb, frame.icons, bgRes,
                if (glow) LcdEffect.GLOW else LcdEffect.NONE,
                dotColor = theme.dot,
                modifier = Modifier.fillMaxHeight()
            )
        }
    }

    GlowToggle(glow) { glow = it; AppPrefs.setEffect(ctx, if (it) LcdEffect.GLOW else LcdEffect.NONE) }
    VsyncToggle(vsync) { vsync = it; AppPrefs.setInAppVsync(ctx, it); TamaRuntime.setVsync(it) }

    ThemeControls(
        theme = theme,
        showBgOpacity = false,   // in-app frame is the background image; only dot color applies
        custom = custom,
        onApply = { theme = it; AppPrefs.setLcdDot(ctx, it.dot) },
        onSaveCustom = { AppPrefs.addLcdCustomPreset(ctx, it); custom = AppPrefs.lcdCustomPresets(ctx) },
        onRemoveCustom = { AppPrefs.removeLcdCustomPreset(ctx, it); custom = AppPrefs.lcdCustomPresets(ctx) },
    )
}

@Composable
private fun WidgetSection() {
    val ctx = LocalContext.current
    var theme by remember { mutableStateOf(WidgetPrefs.theme(ctx)) }
    var glow by remember { mutableStateOf(WidgetPrefs.effect(ctx) == LcdEffect.GLOW) }
    var customName by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf(WidgetPrefs.customPresets(ctx)) }

    fun apply(t: WidgetTheme) { theme = t; WidgetPrefs.setTheme(ctx, t) }
    val fx = if (glow) LcdEffect.GLOW else LcdEffect.NONE

    val flow = TamaRuntime.frame
    val liveFrame = if (flow != null) flow.collectAsStateWithLifecycle().value else null
    val sampleFb = liveFrame?.fb ?: ByteArray(TamaCore.FRAME_BYTES)
    val preview = remember(theme, fx, sampleFb.contentHashCode()) {
        WidgetRenderer.render(theme, fx, sampleFb).asImageBitmap()
    }
    Image(preview, "preview", modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp))

    GlowToggle(glow) { glow = it; WidgetPrefs.setEffect(ctx, if (it) LcdEffect.GLOW else LcdEffect.NONE) }

    ThemeControls(
        theme = theme,
        showBgOpacity = true,
        custom = custom,
        onApply = ::apply,
        onSaveCustom = { WidgetPrefs.addCustomPreset(ctx, it); custom = WidgetPrefs.customPresets(ctx) },
        onRemoveCustom = { WidgetPrefs.removeCustomPreset(ctx, it); custom = WidgetPrefs.customPresets(ctx) },
    )
}

/**
 * Shared theme editor for both the widget and the in-app LCD. [showBgOpacity] hides the
 * background color + opacity controls for the LCD (the in-app frame is the background
 * image, so only the dot color is themeable). Presets apply bg+dot; the LCD ignores bg.
 */
@Composable
private fun ThemeControls(
    theme: WidgetTheme,
    showBgOpacity: Boolean,
    custom: List<WidgetTheme>,
    onApply: (WidgetTheme) -> Unit,
    onSaveCustom: (WidgetTheme) -> Unit,
    onRemoveCustom: (String) -> Unit,
) {
    var customName by remember { mutableStateOf("") }

    Text("Presets", style = MaterialTheme.typography.titleMedium)
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        // Custom presets first (★ + bold border, long-press to delete), then built-ins.
        custom.forEach { p ->
            PresetChip(p.name, isCustom = true,
                onApply = { onApply(theme.copy(bg = p.bg, dot = p.dot)) },
                onDelete = { onRemoveCustom(p.name) })
        }
        WidgetThemes.presets.forEach { p ->
            PresetChip(p.name, isCustom = false,
                onApply = { onApply(theme.copy(bg = p.bg, dot = p.dot)) },
                onDelete = {})
        }
    }

    if (showBgOpacity) {
        Text("Background opacity: ${theme.alpha}", style = MaterialTheme.typography.titleMedium)
        Slider(theme.alpha.toFloat(), { onApply(theme.copy(alpha = it.toInt())) }, valueRange = 0f..255f)
        ColorSwatchButton("Background color", theme.bg) { onApply(theme.copy(bg = it)) }
    }
    ColorSwatchButton("Dot color", theme.dot) { onApply(theme.copy(dot = it)) }

    Text("Save as custom preset", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(customName, { customName = it }, label = { Text("Name") }, modifier = Modifier.weight(1f))
        Button(enabled = customName.isNotBlank(), onClick = {
            onSaveCustom(theme.copy(name = customName.trim())); customName = ""
        }) { Text("Save") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetChip(label: String, isCustom: Boolean, onApply: () -> Unit, onDelete: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val border = if (isCustom)
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    else
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    Row(
        modifier = Modifier
            .clip(shape)
            .border(border, shape)
            .combinedClickable(onClick = onApply, onLongClick = { if (isCustom) onDelete() })
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isCustom) Text("★ ", color = MaterialTheme.colorScheme.primary)
        Text(label)
    }
}

@Composable
private fun RomSection(onChangeRom: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Text("ROM", style = MaterialTheme.typography.titleMedium)
    Text("Replacing the ROM starts a NEW pet and erases the current save.")
    OutlinedButton(onClick = { confirm = true }) { Text("Change ROM…") }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Change ROM?") },
            text = { Text("This erases the current pet and save data. Continue?") },
            confirmButton = { TextButton(onClick = { confirm = false; onChangeRom() }) { Text("Continue") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun GlowToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Glow effect", style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun VsyncToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    var showTip by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Vsync", style = MaterialTheme.typography.titleMedium)
        Box(
            modifier = Modifier.size(22.dp).clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable { showTip = !showTip },
            contentAlignment = Alignment.Center
        ) { Text("?", style = MaterialTheme.typography.labelMedium) }
        Switch(checked = checked, onCheckedChange = onChange)
    }
    if (showTip) {
        Text(
            "Enables Vsync, like the widget. This removes flicker, but loses the authentic " +
                "screen-refresh behavior of the real device.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
