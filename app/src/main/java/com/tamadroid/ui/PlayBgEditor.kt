package com.tamadroid.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamadroid.data.AppPrefs
import com.tamadroid.service.TamaRuntime
import java.io.File
import kotlin.math.roundToInt

/**
 * Full-screen layout editor. Two modes share one WYSIWYG preview ([PlayArea], identical to
 * the live screen): "Image" pans/pinches the background picture and sizes the device screen,
 * "Buttons" drags each button so they line up with the picture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayBgEditor(onDone: () -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val bmp = remember {
        BitmapFactory.decodeFile(File(ctx.filesDir, AppPrefs.PLAY_BG_IMAGE_FILE).absolutePath)?.asImageBitmap()
    }
    val hasImage = bmp != null

    val saved = AppPrefs.playImage(ctx)
    var offX by remember { mutableFloatStateOf(saved?.offsetX ?: 0f) }
    var offY by remember { mutableFloatStateOf(saved?.offsetY ?: 0f) }
    var scale by remember { mutableFloatStateOf(saved?.scale ?: 1f) }

    val positions = remember {
        AppPrefs.playButtonPositions(ctx).map { Offset(it.first, it.second) }.toMutableStateList()
    }

    var screenWidth by remember { mutableFloatStateOf(AppPrefs.screenWidthFraction(ctx)) }

    var mode by remember { mutableStateOf(PlayEdit.FIT) }

    val flow = TamaRuntime.frame
    val frame = if (flow != null) flow.collectAsStateWithLifecycle().value else null

    val transition = rememberInfiniteTransition(label = "guide")
    val t by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "t"
    )
    val guide = lerp(Color(0xFF2196F3), Color(0xFFF44336), t)

    val bgRes = if (hasImage) null else backgroundDrawable(AppPrefs.background(ctx))

    Box(modifier = Modifier.fillMaxSize()) {
        PlayArea(
            frame = frame,
            bgRes = bgRes,
            effect = AppPrefs.effect(ctx),
            dotColor = AppPrefs.lcdDot(ctx),
            iconColor = AppPrefs.effectiveIconColor(ctx),
            packed = AppPrefs.lcdPacked(ctx),
            bgColor = AppPrefs.playBgColor(ctx, dark),
            playBmp = bmp,
            playImage = if (hasImage) AppPrefs.PlayImage(offX, offY, scale) else null,
            buttonColor = AppPrefs.playButtonColor(ctx, dark),
            buttonAlpha = AppPrefs.playButtonAlpha(ctx),
            buttonPositions = positions,
            screenWidthFraction = screenWidth,
            mode = mode,
            guideColor = if (mode == PlayEdit.FIT) guide else null,
            onImageTransform = { pan, zoom ->
                offX += pan.x; offY += pan.y; scale = (scale * zoom).coerceIn(0.1f, 10f)
            },
            onButtonDrag = { i, d ->
                val cur = positions[i]
                positions[i] = Offset((cur.x + d.x).coerceIn(0f, 1f), (cur.y + d.y).coerceIn(0f, 1f))
            },
        )

        // Mode toggle (top) — full width available here, so the labels never wrap.
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp)
        ) {
            val modes = listOf(PlayEdit.FIT to (if (hasImage) "Image" else "Screen"), PlayEdit.BUTTONS to "Buttons")
            modes.forEachIndexed { i, (m, label) ->
                SegmentedButton(
                    selected = mode == m,
                    onClick = { mode = m },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
                    icon = {}
                ) { Text(label) }
            }
        }

        // Hint + actions (bottom): Cancel | Reset | Confirm — short labels that always fit.
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val hint = when {
                mode == PlayEdit.BUTTONS -> "Drag each button to position it"
                hasImage -> "Drag to move, pinch to scale the image"
                else -> "Adjust the screen size"
            }
            Text(
                hint,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            if (mode == PlayEdit.FIT) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Screen", color = Color.White)
                    Slider(
                        value = screenWidth,
                        onValueChange = { screenWidth = it },
                        valueRange = AppPrefs.MIN_SCREEN_WIDTH_FRACTION..AppPrefs.MAX_SCREEN_WIDTH_FRACTION,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${(screenWidth * 100).roundToInt()}%",
                        color = Color.White,
                        modifier = Modifier.widthIn(min = 44.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onCancel) { Text("Cancel") }
                Spacer(Modifier.weight(1f))
                // Reset restores the current mode's layout to its defaults.
                FilledTonalButton(
                    onClick = {
                        if (mode == PlayEdit.FIT) {
                            offX = 0f; offY = 0f; scale = 1f; screenWidth = AppPrefs.MAX_SCREEN_WIDTH_FRACTION
                        } else {
                            AppPrefs.DEFAULT_BTN_POS.forEachIndexed { i, p -> positions[i] = Offset(p.first, p.second) }
                        }
                    }
                ) { Text("Reset") }
                Button(
                    onClick = {
                        if (hasImage) AppPrefs.setPlayImage(ctx, AppPrefs.PlayImage(offX, offY, scale))
                        AppPrefs.setPlayButtonPositions(ctx, positions.map { it.x to it.y })
                        AppPrefs.setScreenWidthFraction(ctx, screenWidth)
                        onDone()
                    }
                ) { Text("Confirm") }
            }
        }
    }
}
