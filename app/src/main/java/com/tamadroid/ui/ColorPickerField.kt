package com.tamadroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController

/**
 * A labelled color field: a circular swatch that opens a wheel color picker
 * (skydoves colorpicker-compose) — drag the point on the wheel, adjust brightness.
 */
@Composable
fun ColorSwatchButton(label: String, color: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(color or (0xFF shl 24)))
                .border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                .clickable { open = true }
        )
    }
    if (open) {
        ColorPickerDialog(
            initial = color or (0xFF shl 24),
            onDismiss = { open = false },
            onConfirm = { onPick(it or (0xFF shl 24)); open = false }
        )
    }
}

@Composable
private fun ColorPickerDialog(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val controller = rememberColorPickerController()
    var current by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a color") },
        confirmButton = { TextButton(onClick = { onConfirm(current) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HsvColorPicker(
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    controller = controller,
                    initialColor = Color(initial),
                    onColorChanged = { env -> if (env.color.toArgb() != 0) current = env.color.toArgb() }
                )
                BrightnessSlider(
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    controller = controller
                )
                Box(
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(current or (0xFF shl 24)))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                )
            }
        }
    )
}
