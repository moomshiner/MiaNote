// App.kt — entry point with exclusive fullscreen + tools + undo + zoomable canvas

import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget

import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.Timer
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseWheelListener
import kotlin.math.max
import kotlin.math.min

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Mianote",
        undecorated = true,
        resizable = false
    ) {
        // Exclusive fullscreen (handles 125% DPI)
        DisposableEffect(Unit) {
            fun applyExclusive(frame: JFrame) {
                val device = frame.graphicsConfiguration.device
                if (device.isFullScreenSupported) {
                    device.fullScreenWindow = frame
                } else {
                    val b = frame.graphicsConfiguration.bounds
                    frame.setLocation(b.x, b.y)
                    frame.setSize(b.width, b.height)
                    frame.isAlwaysOnTop = true
                }
            }
            SwingUtilities.invokeLater {
                val frame = window as JFrame
                applyExclusive(frame)
                Timer(180) { applyExclusive(frame) }.apply { isRepeats = false; start() }
                val listener = object : ComponentAdapter() {
                    override fun componentResized(e: ComponentEvent) = applyExclusive(frame)
                    override fun componentMoved(e: ComponentEvent) = applyExclusive(frame)
                }
                frame.addComponentListener(listener)

                // cleanup
                (onDispose {
                    frame.removeComponentListener(listener)
                    val dev = frame.graphicsConfiguration.device
                    if (dev.fullScreenWindow === frame) dev.fullScreenWindow = null
                })
            }
            onDispose { }
        }

        // ---- App state ----
        var tool by remember { mutableStateOf(Tool.PENCIL) }
        val canvasColor = Color.White
        val outsideColor = Color(0xFFEEEEEE) // light gray outside canvas
        val pencilColor = Color.Black
        var pencilWidthPx by remember { mutableStateOf(4f) }
        var eraserWidthPx by remember { mutableStateOf(10f) }

        // Strokes + undo
        var strokes by remember { mutableStateOf(listOf<StrokePath>()) }
        val undo = remember { UndoManager<List<StrokePath>>(256) }
        var isDrawing by remember { mutableStateOf(false) }

        // Canvas size defaults to current screen resolution (set after frame is ready)
        var canvasWidthPx by remember { mutableStateOf(1920f) }
        var canvasHeightPx by remember { mutableStateOf(1080f) }

        // Zoom state (+ wheel listener on the window)
        var zoom by remember { mutableStateOf(1f) }
        val minZoom = 0.2f
        val maxZoom = 8f
        DisposableEffect(Unit) {
            val frame = window as JFrame
            // Use the actual display bounds we’re on
            val b = frame.graphicsConfiguration.bounds
            canvasWidthPx = b.width.toFloat()
            canvasHeightPx = b.height.toFloat()

            val wheel = MouseWheelListener { e ->
                val step = 1.1f
                zoom = if (e.preciseWheelRotation < 0) {
                    min(zoom * step, maxZoom)
                } else {
                    max(zoom / step, minZoom)
                }
            }
            frame.addMouseWheelListener(wheel)
            onDispose { frame.removeMouseWheelListener(wheel) }
        }

        // Focus so key events always arrive here
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E1E))
                .focusRequester(focusRequester)
                .focusTarget()
                .onPreviewKeyEvent { ev ->
                    if (!isDrawing &&
                        ev.type == KeyEventType.KeyDown &&
                        ev.isCtrlPressed &&
                        ev.key == Key.Z
                    ) {
                        undo.pop()?.let { prev -> strokes = prev }
                        true
                    } else false
                }
        ) {
            // Zoomable canvas area (centered, with gray outside)
            DrawingSurface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize(),
                canvasColor = canvasColor,
                outsideColor = outsideColor,
                canvasWidthPx = canvasWidthPx,
                canvasHeightPx = canvasHeightPx,
                zoom = zoom,
                tool = tool,
                pencilColor = pencilColor,
                pencilWidthPx = pencilWidthPx,
                eraserWidthPx = eraserWidthPx,
                strokes = strokes,
                onBeginStroke = { prev ->
                    undo.push(prev)
                    isDrawing = true
                },
                onCommitStroke = { updated ->
                    strokes = updated
                    isDrawing = false
                },
                onCancelStroke = { isDrawing = false }
            )

            // Exit (top-left)
            ExitButton(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
            ) { exitApplication() }

            // Brush size controls (top-right)
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SpriteButton(resName = "plus.png") {
                    if (tool == Tool.PENCIL) {
                        pencilWidthPx = min(pencilWidthPx + 1f, 10f)
                    } else {
                        eraserWidthPx = min(eraserWidthPx + 1f, 10f)
                    }
                    focusRequester.requestFocus()
                }
                SpriteButton(resName = "minus.png") {
                    if (tool == Tool.PENCIL) {
                        pencilWidthPx = max(pencilWidthPx - 1f, 1f)
                    } else {
                        eraserWidthPx = max(eraserWidthPx - 1f, 1f)
                    }
                    focusRequester.requestFocus()
                }
            }

            // Tools (right side)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SpriteButton(
                    resName = "pencil.png",
                    selected = tool == Tool.PENCIL
                ) {
                    tool = Tool.PENCIL
                    focusRequester.requestFocus()
                }

                SpriteButton(
                    resName = "eraser.png",
                    selected = tool == Tool.ERASER
                ) {
                    tool = Tool.ERASER
                    focusRequester.requestFocus()
                }
            }
        }
    }
}
