package com.spen.canvas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.spen.canvas.model.ThemeMode
import com.spen.canvas.ui.CanvasScreen
import com.spen.canvas.ui.DrawingViewModel
import com.spen.canvas.ui.home.NoteHomeScreen
import com.spen.canvas.ui.theme.resolveAppColors

class MainActivity : ComponentActivity() {

    private val viewModel: DrawingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by viewModel.settings.collectAsState()
            val currentNoteId by viewModel.currentNoteId.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val colors = resolveAppColors(settings, systemDark)

            val colorScheme = if (colors.isDark) {
                darkColorScheme(
                    primary = colors.accent,
                    secondary = colors.accent,
                    surface = colors.panel
                )
            } else {
                lightColorScheme(
                    primary = colors.accent,
                    secondary = colors.accent,
                    surface = colors.panel
                )
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(settings.canvasColor)
                ) {
                    AnimatedContent(
                        targetState = currentNoteId,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "ScreenTransition"
                    ) { noteId ->
                        if (noteId == null) {
                            NoteHomeScreen(viewModel = viewModel)
                        } else {
                            CanvasScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

