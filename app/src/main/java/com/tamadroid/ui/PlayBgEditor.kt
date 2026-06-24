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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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

/**
 * Full-screen layout editor. Two modes share one WYSIWYG preview ([PlayArea], identical to
 * the live screen): "Move image" pans/pinches the background picture behind the device,
 * "Move buttons" drags each button so they line up with the picture. Confirm saves
 * both the image transform and the button positions.
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

    var mode by remember { mutableStateOf(if (hasImage) PlayEdit.IMAGE else PlayEdit.BUTTONS) }

    val flow = TamaRuntime.frame
    val frame = if (flow != null) flow.collectAsStateWithLifecycle().value else null

    // Animated alignment guide (blue <-> red) shown only while moving the image.
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
            packed = AppPrefs.lcdPacked(ctx),
            bgColor = AppPrefs.playBgColor(ctx, dark),
            playBmp = bmp,
            playImage = if (hasImage) AppPrefs.PlayImage(offX, offY, scale) else null,
            buttonColor = AppPrefs.playButtonColor(ctx, dark),
            buttonAlpha = AppPrefs.playButtonAlpha(ctx),
            buttonPositions = positions,
            mode = mode,
            guideColor = if (mode == PlayEdit.IMAGE && hasImage) guide else null,
            onImageTransform = { pan, zoom ->
                offX += pan.x; offY += pan.y; scale = (scale * zoom).coerceIn(0.1f, 10f)
            },
            onButtonDrag = { i, d ->
                val cur = positions[i]
                positions[i] = Offset((cur.x + d.x).coerceIn(0f, 1f), (cur.y + d.y).coerceIn(0f, 1f))
            },
        )

        // Reset (top) — restores the current mode's layout to its defaults.
        FilledTonalButton(
            onClick = {
                if (mode == PlayEdit.IMAGE) {
                    offX = 0f; offY = 0f; scale = 1f
                } else {
                    AppPrefs.DEFAULT_BTN_POS.forEachIndexed { i, p -> positions[i] = Offset(p.first, p.second) }
                }
            },
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp)
        ) { Text(if (mode == PlayEdit.IMAGE) "Reset image" else "Reset buttons") }

        // Hint + actions (bottom): Cancel | mode toggle | Confirm, so the toggle is always
        // obvious between the two actions (a transparent unselected chip up top was easy to miss).
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val hint = if (mode == PlayEdit.IMAGE) "Drag to move, pinch to scale" else "Drag each button to position it"
            Text(
                hint,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onCancel) { Text("Cancel") }
                SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                    SegmentedButton(
                        selected = mode == PlayEdit.IMAGE,
                        enabled = hasImage,
                        onClick = { mode = PlayEdit.IMAGE },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {}
                    ) { Text("Image") }
                    SegmentedButton(
                        selected = mode == PlayEdit.BUTTONS,
                        onClick = { mode = PlayEdit.BUTTONS },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {}
                    ) { Text("Buttons") }
                }
                Button(
                    onClick = {
                        if (hasImage) AppPrefs.setPlayImage(ctx, AppPrefs.PlayImage(offX, offY, scale))
                        AppPrefs.setPlayButtonPositions(ctx, positions.map { it.x to it.y })
                        onDone()
                    }
                ) { Text("Confirm") }
            }
        }
    }
}
