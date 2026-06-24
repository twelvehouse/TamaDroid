package com.tamadroid.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tamadroid.data.AppPrefs
import com.tamadroid.engine.TamaEngine
import com.tamadroid.service.TamaRuntime
import kotlin.math.roundToInt

/** Editing state of the play area. NONE = live game (buttons are pressable). */
enum class PlayEdit { NONE, IMAGE, BUTTONS }

private val BTN_SIZE = 56.dp

/**
 * The shared play composition used by both the live emulator screen and the layout
 * editor, so the editor preview is WYSIWYG with the real screen. It draws (back to front):
 *  - the optional custom background image (pan/scale transform),
 *  - the LCD device screen, vertically centered with a spacer reserving the button-row
 *    height (so the LCD keeps the same position whether buttons are below it or floating),
 *  - the three buttons as an absolute overlay at normalized positions.
 *
 * In [PlayEdit.NONE] the buttons drive the emulator; in [PlayEdit.IMAGE] area gestures
 * pan/zoom the image (buttons are inert references); in [PlayEdit.BUTTONS] each button is
 * draggable and the image is fixed.
 */
@Composable
fun PlayArea(
    frame: TamaEngine.Frame?,
    bgRes: Int?,
    effect: LcdEffect,
    dotColor: Int,
    iconColor: Int? = null,
    packed: Boolean,
    bgColor: Int,
    playBmp: ImageBitmap?,
    playImage: AppPrefs.PlayImage?,
    buttonColor: Int,
    buttonAlpha: Int,
    buttonPositions: List<Offset>,
    mode: PlayEdit = PlayEdit.NONE,
    guideColor: Color? = null,
    onImageTransform: (pan: Offset, zoom: Float) -> Unit = { _, _ -> },
    onButtonDrag: (index: Int, fracDelta: Offset) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    var areaSize by remember { mutableStateOf(IntSize.Zero) }
    val btnHalfPx = with(LocalDensity.current) { (BTN_SIZE / 2).toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(bgColor))
            .onSizeChanged { areaSize = it }
            .then(
                if (mode == PlayEdit.IMAGE)
                    Modifier.pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ -> onImageTransform(pan, zoom) }
                    }
                else Modifier
            )
    ) {
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

        // LCD column. The trailing spacer reserves the old button-row height so the LCD's
        // vertical position is identical whether or not the buttons sit in the column.
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp)
                .then(if (mode == PlayEdit.NONE) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (frame != null) {
                TamaScreen(
                    frame.fb, frame.icons,
                    bgRes = bgRes,
                    effect = effect,
                    dotColor = dotColor,
                    iconColor = iconColor,
                    allIcons = mode != PlayEdit.NONE,   // editor: show every icon for clarity
                    guideColor = guideColor,
                    packed = packed,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(BTN_SIZE))
        }

        // Absolute button overlay (needs the measured pixel size to place by fraction).
        val w = areaSize.width.toFloat()
        val h = areaSize.height.toFloat()
        if (w > 0f && h > 0f) {
            buttonPositions.forEachIndexed { i, pos ->
                val xPx = (pos.x * w - btnHalfPx).roundToInt()
                val yPx = (pos.y * h - btnHalfPx).roundToInt()
                PlayButton(
                    btn = i,
                    color = buttonColor,
                    alpha = buttonAlpha,
                    mode = mode,
                    onDrag = { drag -> onButtonDrag(i, Offset(drag.x / w, drag.y / h)) },
                    modifier = Modifier.offset { IntOffset(xPx, yPx) }
                )
            }
        }
    }
}

@Composable
private fun PlayButton(
    btn: Int,
    color: Int,
    alpha: Int,
    mode: PlayEdit,
    onDrag: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(28.dp)
    val fill = Color(color or (0xFF shl 24)).copy(alpha = alpha / 255f)
    val interaction = remember { MutableInteractionSource() }

    val base = modifier.size(BTN_SIZE).clip(shape).background(fill)
    val styled = when (mode) {
        PlayEdit.NONE -> base
            .indication(interaction, ripple())
            .pointerInput(btn) {
                detectTapGestures(onPress = {
                    val press = PressInteraction.Press(it)
                    interaction.emit(press)
                    buttonTick(ctx); TamaRuntime.press(btn, true)
                    tryAwaitRelease()
                    interaction.emit(PressInteraction.Release(press))
                    TamaRuntime.press(btn, false)
                })
            }
        PlayEdit.BUTTONS -> base
            .border(2.dp, Color.White, shape)
            .pointerInput(btn) {
                detectDragGestures { change, drag -> change.consume(); onDrag(drag) }
            }
        PlayEdit.IMAGE -> base   // inert reference; gestures fall through to the image
    }
    Box(styled)
}

/** Short haptic tick on button press (if enabled in settings). */
internal fun buttonTick(ctx: Context) {
    if (!AppPrefs.vibrate(ctx)) return
    val vib = if (Build.VERSION.SDK_INT >= 31) {
        (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        (ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
    }
    if (!vib.hasVibrator()) return
    vib.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
}
