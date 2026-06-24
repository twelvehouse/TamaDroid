package com.tamadroid.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamadroid.R
import com.tamadroid.data.AppPrefs
import com.tamadroid.service.TamaRuntime
import java.io.File

/**
 * Full-screen editor: pan/pinch the picked image freely behind the live device (LCD +
 * icons) so the user can compose the play screen, then Confirm to save the transform.
 * The device overlay has no pointer handling, so all gestures drive the image.
 */
@Composable
fun PlayBgEditor(onDone: () -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val bmp = remember {
        BitmapFactory.decodeFile(File(ctx.filesDir, AppPrefs.PLAY_BG_IMAGE_FILE).absolutePath)?.asImageBitmap()
    }
    val saved = AppPrefs.playImage(ctx)
    var offX by remember { mutableFloatStateOf(saved?.offsetX ?: 0f) }
    var offY by remember { mutableFloatStateOf(saved?.offsetY ?: 0f) }
    var scale by remember { mutableFloatStateOf(saved?.scale ?: 1f) }

    val flow = TamaRuntime.frame
    val frame = if (flow != null) flow.collectAsStateWithLifecycle().value else null

    // Animated alignment guide (blue <-> red) at the device-frame edge.
    val transition = rememberInfiniteTransition(label = "guide")
    val t by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "t"
    )
    val guide = lerp(Color(0xFF2196F3), Color(0xFFF44336), t)

    Box(
        modifier = Modifier.fillMaxSize().background(Color(AppPrefs.playBgColor(ctx)))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    offX += pan.x; offY += pan.y; scale = (scale * zoom).coerceIn(0.1f, 10f)
                }
            }
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = "background",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer(
                    translationX = offX, translationY = offY, scaleX = scale, scaleY = scale
                )
            )
        }

        // Live device overlay (no pointerInput -> gestures pass through to the image).
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (frame != null) {
                TamaScreen(
                    frame.fb, frame.icons,
                    bgRes = null,                 // image mode: no frame, just LCD + icons
                    effect = AppPrefs.effect(ctx),
                    dotColor = AppPrefs.lcdDot(ctx),
                    allIcons = true,              // show every icon so positions are clear
                    guideColor = guide,           // animated frame-edge guide
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Drag to move, pinch to scale", color = Color.White)
            Box(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onCancel, modifier = Modifier.align(Alignment.CenterStart)) { Text("Cancel") }
                Button(
                    onClick = { AppPrefs.setPlayImage(ctx, AppPrefs.PlayImage(offX, offY, scale)); onDone() },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) { Text("Confirm") }
            }
        }
    }
}
