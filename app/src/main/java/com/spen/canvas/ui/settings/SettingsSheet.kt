package com.spen.canvas.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spen.canvas.model.AppSettings
import com.spen.canvas.model.BackgroundType
import com.spen.canvas.model.CanvasStyle
import com.spen.canvas.model.ShapeSensitivity
import com.spen.canvas.model.SmoothingLevel
import com.spen.canvas.model.ThemeMode
import com.spen.canvas.ui.theme.AppColors

@Composable
fun SettingsSheetContent(
    settings: AppSettings,
    backgroundType: BackgroundType,
    colors: AppColors,
    onBackground: (BackgroundType) -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings & appearance", style = MaterialTheme.typography.headlineSmall, color = colors.onPanel, fontWeight = FontWeight.SemiBold)

        SettingsSectionLabel("Writing", colors)
        Text("Stroke smoothing", style = MaterialTheme.typography.bodyLarge, color = colors.onPanel)
        ChipRow(
            options = listOf(
                SmoothingLevel.OFF to "Off",
                SmoothingLevel.STANDARD to "Standard",
                SmoothingLevel.EXTRA to "Extra"
            ),
            selected = settings.strokeSmoothing,
            colors = colors
        ) { v -> onUpdate { it.copy(strokeSmoothing = v) } }
        SettingToggle("Pressure sensitivity", "Vary stroke width with tip pressure", settings.pressureSensitivity, colors) { v -> onUpdate { it.copy(pressureSensitivity = v) } }

        SettingsSectionLabel("S Pen", colors)
        SettingToggle("Side button erases", "Hold the S Pen button to erase, release to resume", settings.sidePenButtonErases, colors) { v -> onUpdate { it.copy(sidePenButtonErases = v) } }
        SettingSlider("Eraser size", "${settings.tempEraserSize.toInt()} px", settings.tempEraserSize, 20f..120f, colors) { v -> onUpdate { it.copy(tempEraserSize = v) } }

        SettingsSectionLabel("Shapes", colors)
        SettingToggle("Snap shapes on hold", "Draw a rough shape, pause at the end to clean it up", settings.shapeSnapOnHold, colors) { v -> onUpdate { it.copy(shapeSnapOnHold = v) } }
        Text("Recognition sensitivity", style = MaterialTheme.typography.bodyLarge, color = colors.onPanel)
        ChipRow(
            options = listOf(
                ShapeSensitivity.LOW to "Low",
                ShapeSensitivity.MEDIUM to "Medium",
                ShapeSensitivity.HIGH to "High"
            ),
            selected = settings.shapeSensitivity,
            colors = colors
        ) { v -> onUpdate { it.copy(shapeSensitivity = v) } }

        SettingsSectionLabel("Canvas Surface & Grid", colors)
        Text("Surface background", style = MaterialTheme.typography.bodyLarge, color = colors.onPanel)
        ChipRow(
            options = listOf(
                CanvasStyle.CHARCOAL to "Charcoal",
                CanvasStyle.WHITE to "White",
                CanvasStyle.PAPER to "Warm Paper",
                CanvasStyle.OLED to "AMOLED Black"
            ),
            selected = settings.canvasStyle,
            colors = colors
        ) { style -> onUpdate { it.copy(canvasStyle = style) } }
        Text("Grid pattern", style = MaterialTheme.typography.bodyLarge, color = colors.onPanel)
        ChipRow(
            options = BackgroundType.entries.map { it to it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
            selected = backgroundType,
            colors = colors,
            onSelect = onBackground
        )

        SettingsSectionLabel("App Chrome Theme", colors)
        Text("Theme preset", style = MaterialTheme.typography.bodyLarge, color = colors.onPanel)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ChipRow(
                options = listOf(
                    ThemeMode.SYSTEM to "System",
                    ThemeMode.LIGHT to "Light",
                    ThemeMode.DARK to "Dark"
                ),
                selected = settings.themeMode,
                colors = colors
            ) { mode -> onUpdate { it.copy(themeMode = mode) } }
            ChipRow(
                options = listOf(
                    ThemeMode.WARM_PAPER to "Warm Paper",
                    ThemeMode.TRUE_BLACK to "True Black",
                    ThemeMode.SOFT_GRAY to "Soft Gray"
                ),
                selected = settings.themeMode,
                colors = colors
            ) { mode -> onUpdate { it.copy(themeMode = mode) } }
        }
        SettingSlider("Toolbar transparency", "${((1f - settings.toolbarOpacity) * 100).toInt()}%", settings.toolbarOpacity, 0.45f..1f, colors) { v -> onUpdate { it.copy(toolbarOpacity = v) } }

        SettingsSectionLabel("Ergonomics", colors)
        SettingToggle("Draw with finger", "Off = one finger pans the canvas (pen-first)", settings.drawWithFinger, colors) { v -> onUpdate { it.copy(drawWithFinger = v) } }
        SettingToggle("Left-handed dock", "Mirror the tool dock layout", settings.leftHanded, colors) { v -> onUpdate { it.copy(leftHanded = v) } }
        SettingToggle("Haptic feedback", "Subtle tick on tool changes", settings.hapticFeedback, colors) { v -> onUpdate { it.copy(hapticFeedback = v) } }
    }
}

@Composable
private fun SettingSlider(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    colors: AppColors,
    onChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onPanel)
            Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = colors.onPanelMuted)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SettingsSectionLabel(text: String, colors: AppColors) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.onPanelMuted, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun <T> ChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    colors: AppColors,
    onSelect: (T) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    colors: AppColors,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onPanel)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.onPanelMuted)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
