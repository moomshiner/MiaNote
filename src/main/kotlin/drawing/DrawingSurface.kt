// drawing/DrawingSurface.kt — zoomable centered canvas, manual clipped rendering (no clamping),
// invisible OS cursor only inside canvas, stable cursor while zooming

import androidx.compose.runtime.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.awt.Point
import java.awt.Toolkit
import javax.imageio.ImageIO
import kotlin.math.abs

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawingSurface(
    modifier: Modifier = Modifier,
    canvasColor: Color,
    outsideColor: Color,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    zoom: Float,
    pan: Offset,
    spacePressed: Boolean,
    tool: Tool,
    pencilColor: Color,
    pencilWidthPx: Float,
    eraserWidthPx: Float,
    strokes: List<StrokePath>,
    onBeginStroke: (List<StrokePath>) -> Unit,
    onCommitStroke: (List<StrokePath>) -> Unit,
    onCancelStroke: () -> Unit,
    onPan: (Offset) -> Unit
) {
    var current by remember { mutableStateOf<StrokePath?>(null) }
    var hoverPosCanvas by remember { mutableStateOf<Offset?>(null) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var lastScreenPos by remember { mutableStateOf<Offset?>(null) }
    var insideCanvas by remember { mutableStateOf(false) }
    var leftPressed by remember { mutableStateOf(false) }
    var middlePressed by remember { mutableStateOf(false) }

    // Keep latest values without restarting pointerInput when they change
    val strokesState by rememberUpdatedState(strokes)
    val beginStroke by rememberUpdatedState(onBeginStroke)
    val commitStroke by rememberUpdatedState(onCommitStroke)
    val cancelStroke by rememberUpdatedState(onCancelStroke)

    fun toolColor() = if (tool == Tool.ERASER) canvasColor else pencilColor
    fun toolWidth() = if (tool == Tool.ERASER) eraserWidthPx else pencilWidthPx

    val outlineWidthPx = with(LocalDensity.current) { 1.dp.toPx() }
    val cursorColor =
        if (tool == Tool.ERASER) Color(0xFF444444).copy(alpha = 0.9f)
        else Color(0xFF111111).copy(alpha = 0.9f)

    fun loadCursor(res: String): PointerIcon {
        val url = object {}.javaClass.getResource("/$res")
        val img = ImageIO.read(url)
        val awtCursor = Toolkit.getDefaultToolkit().createCustomCursor(img, Point(0, 0), res)
        return PointerIcon(awtCursor)
    }
    val penCursor = remember { loadCursor("pencil.png") }
    val handOpenCursor = remember { loadCursor("handopen.png") }
    val handGrabCursor = remember { loadCursor("handgrab.png") }
    var pointerIcon by remember { mutableStateOf(penCursor) }

    fun topLeftInScreen(z: Float = zoom): Offset {
        val vw = viewport.width.toFloat()
        val vh = viewport.height.toFloat()
        val sw = canvasWidthPx * z
        val sh = canvasHeightPx * z
        return Offset((vw - sw) / 2f + pan.x, (vh - sh) / 2f + pan.y)
    }
    fun screenToCanvas(p: Offset, z: Float = zoom): Offset {
        val tl = topLeftInScreen(z)
        return Offset((p.x - tl.x) / z, (p.y - tl.y) / z)
    }
    fun canvasToScreen(p: Offset, z: Float = zoom): Offset {
        val tl = topLeftInScreen(z)
        return Offset(tl.x + p.x * z, tl.y + p.y * z)
    }
    fun isInsideCanvas(cp: Offset): Boolean =
        cp.x >= 0f && cp.y >= 0f && cp.x <= canvasWidthPx && cp.y <= canvasHeightPx

    // Keep cursor stable under mouse while zoom changes
    LaunchedEffect(zoom, viewport, canvasWidthPx, canvasHeightPx, pan) {
        lastScreenPos?.let { sp ->
            val cp = screenToCanvas(sp, zoom)
            val inside = isInsideCanvas(cp)
            insideCanvas = inside
            hoverPosCanvas = if (inside && !spacePressed && !middlePressed) cp else null
        }
    }

    LaunchedEffect(spacePressed, leftPressed, middlePressed, insideCanvas) {
        pointerIcon = when {
            !insideCanvas -> PointerIcon.Default
            spacePressed && leftPressed -> handGrabCursor
            spacePressed || middlePressed -> handOpenCursor
            else -> penCursor
        }
    }

    Box(
        modifier
            .background(outsideColor)
            .onSizeChanged { viewport = it }
            // Cursor icon depends on whether pointer is within canvas bounds
            .pointerHoverIcon(pointerIcon, overrideDescendants = true)

            // Track pointer (store screen pos, and canvas pos only when inside)
            .onPointerEvent(PointerEventType.Move) { e ->
                val p = e.changes.firstOrNull()?.position ?: return@onPointerEvent
                lastScreenPos = p
                val cp = screenToCanvas(p)
                insideCanvas = isInsideCanvas(cp)
                hoverPosCanvas = if (insideCanvas && !spacePressed && !middlePressed) cp else null
            }
            .onPointerEvent(PointerEventType.Press) { e ->
                leftPressed = e.buttons.isPrimaryPressed
                middlePressed = e.buttons.isTertiaryPressed
                val p = e.changes.firstOrNull()?.position ?: return@onPointerEvent
                lastScreenPos = p
                val cp = screenToCanvas(p)
                insideCanvas = isInsideCanvas(cp)
                hoverPosCanvas = if (insideCanvas && !spacePressed && !middlePressed) cp else null
            }
            .onPointerEvent(PointerEventType.Release) { e ->
                leftPressed = e.buttons.isPrimaryPressed
                middlePressed = e.buttons.isTertiaryPressed
                val p = e.changes.firstOrNull()?.position
                if (p != null) {
                    lastScreenPos = p
                    val cp = screenToCanvas(p)
                    insideCanvas = isInsideCanvas(cp)
                    hoverPosCanvas = if (insideCanvas && !spacePressed && !middlePressed) cp else null
                }
            }
            .onPointerEvent(PointerEventType.Exit) {
                hoverPosCanvas = null
                lastScreenPos = null
                insideCanvas = false
            }

            // TAP-TO-DOT (disabled while panning)
            .pointerInput("tap", tool, pencilWidthPx, eraserWidthPx, strokes, viewport, zoom, canvasWidthPx, canvasHeightPx, pan, spacePressed, middlePressed) {              detectTapGestures(
                    onTap = { p ->
                        if (spacePressed || middlePressed) return@detectTapGestures
                        lastScreenPos = p
                        val cp = screenToCanvas(p)
                        if (!isInsideCanvas(cp)) return@detectTapGestures
                        beginStroke(strokesState)
                        val dot = StrokePath(points = listOf(cp), color = toolColor(), widthPx = toolWidth())
                        commitStroke(strokesState + dot)
                        hoverPosCanvas = cp
                        current = null
                    }
                )
            }

            // DRAG STROKES or PAN (start must be inside for drawing)
            .pointerInput("drag", tool, pencilWidthPx, eraserWidthPx, strokes, viewport, zoom, canvasWidthPx, canvasHeightPx, pan, spacePressed, middlePressed) {
                var startedInside = false
                var panOffset = Offset.Zero
                detectDragGestures(
                    onDragStart = { start ->
                        lastScreenPos = start
                        val cp = screenToCanvas(start)
                        val panMode = spacePressed || middlePressed
                        panOffset = pan
                        if (panMode) {
                            hoverPosCanvas = null
                        } else {
                            startedInside = isInsideCanvas(cp)
                            if (startedInside) {
                                onBeginStroke(strokes)
                                current = StrokePath(points = listOf(cp), color = toolColor(), widthPx = toolWidth())
                                hoverPosCanvas = cp
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        lastScreenPos = change.position
                        if (spacePressed || middlePressed) {
                            panOffset += dragAmount
                            onPan(panOffset)
                        } else if (startedInside) {
                            val cp = screenToCanvas(change.position)
                            current = current?.copy(points = current!!.points + cp)
                            val inside = isInsideCanvas(cp)
                            hoverPosCanvas = if (inside) cp else null
                        }
                    },
                    onDragEnd = {
                        if (spacePressed || middlePressed) {
                            // panning end
                        } else {
                            if (startedInside) {
                                current?.let { stroke -> onCommitStroke(strokes + stroke) } ?: onCancelStroke()
                            } else onCancelStroke()
                        }
                        current = null
                        startedInside = false
                    },
                    onDragCancel = {
                        current = null
                        startedInside = false
                    }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = canvasWidthPx * zoom
            val sh = canvasHeightPx * zoom
            val tl = topLeftInScreen()
            val br = Offset(tl.x + sw, tl.y + sh)

            // Canvas rect (white)
            drawRect(
                color = canvasColor,
                topLeft = tl,
                size = Size(sw, sh)
            )

            // --- Manual line clipping (Cohen–Sutherland) for segments ---
            fun outCode(p: Offset, left: Float, top: Float, right: Float, bottom: Float): Int {
                var code = 0
                if (p.x < left) code = code or 0x1       // LEFT
                if (p.x > right) code = code or 0x2      // RIGHT
                if (p.y < top) code = code or 0x4        // TOP
                if (p.y > bottom) code = code or 0x8     // BOTTOM
                return code
            }
            fun clipSegmentToRect(a: Offset, b: Offset, left: Float, top: Float, right: Float, bottom: Float): Pair<Offset, Offset>? {
                var x0 = a.x; var y0 = a.y
                var x1 = b.x; var y1 = b.y
                var code0 = outCode(Offset(x0, y0), left, top, right, bottom)
                var code1 = outCode(Offset(x1, y1), left, top, right, bottom)

                while (true) {
                    if ((code0 or code1) == 0) { // both inside
                        return Pair(Offset(x0, y0), Offset(x1, y1))
                    } else if ((code0 and code1) != 0) { // trivially outside
                        return null
                    } else {
                        val outCode = if (code0 != 0) code0 else code1
                        val dx = x1 - x0
                        val dy = y1 - y0

                        // Guard against division by zero
                        if (outCode and 0x4 != 0) { // TOP
                            if (abs(dy) < 1e-6f) return null
                            val x = x0 + dx * (top - y0) / dy
                            val y = top
                            if (outCode == code0) { x0 = x; y0 = y; code0 = outCode(Offset(x0, y0), left, top, right, bottom) }
                            else { x1 = x; y1 = y; code1 = outCode(Offset(x1, y1), left, top, right, bottom) }
                        } else if (outCode and 0x8 != 0) { // BOTTOM
                            if (abs(dy) < 1e-6f) return null
                            val x = x0 + dx * (bottom - y0) / dy
                            val y = bottom
                            if (outCode == code0) { x0 = x; y0 = y; code0 = outCode(Offset(x0, y0), left, top, right, bottom) }
                            else { x1 = x; y1 = y; code1 = outCode(Offset(x1, y1), left, top, right, bottom) }
                        } else if (outCode and 0x2 != 0) { // RIGHT
                            if (abs(dx) < 1e-6f) return null
                            val y = y0 + dy * (right - x0) / dx
                            val x = right
                            if (outCode == code0) { x0 = x; y0 = y; code0 = outCode(Offset(x0, y0), left, top, right, bottom) }
                            else { x1 = x; y1 = y; code1 = outCode(Offset(x1, y1), left, top, right, bottom) }
                        } else if (outCode and 0x1 != 0) { // LEFT
                            if (abs(dx) < 1e-6f) return null
                            val y = y0 + dy * (left - x0) / dx
                            val x = left
                            if (outCode == code0) { x0 = x; y0 = y; code0 = outCode(Offset(x0, y0), left, top, right, bottom) }
                            else { x1 = x; y1 = y; code1 = outCode(Offset(x1, y1), left, top, right, bottom) }
                        }
                    }
                }
            }
            // ----------------------------------------------------------------

            // Draw strokes mapped to screen space (and clipped against canvas rect)
            val left = tl.x; val top = tl.y; val right = br.x; val bottom = br.y

            fun drawStrokeScreen(sp: StrokePath) {
                val pts = sp.points
                if (pts.isEmpty()) return

                val strokeWidth = sp.widthPx * zoom
                val cap = StrokeCap.Round

                if (pts.size == 1) {
                    // Only draw dot if the dot center lies inside the canvas
                    val cScreen = canvasToScreen(pts.first())
                    if (cScreen.x in left..right && cScreen.y in top..bottom) {
                        drawCircle(
                            color = sp.color,
                            radius = (sp.widthPx / 2f) * zoom,
                            center = cScreen
                        )
                    }
                    return
                }

                // Draw each consecutive segment, clipped to the canvas rect
                var prev = pts.first()
                for (i in 1 until pts.size) {
                    val cur = pts[i]
                    val a = canvasToScreen(prev)
                    val b = canvasToScreen(cur)
                    val clipped = clipSegmentToRect(a, b, left, top, right, bottom)
                    if (clipped != null) {
                        drawLine(
                            color = sp.color,
                            start = clipped.first,
                            end = clipped.second,
                            strokeWidth = strokeWidth,
                            cap = cap
                        )
                    }
                    prev = cur
                }
            }

            // Existing strokes + current stroke (clipped)
            strokes.forEach(::drawStrokeScreen)
            current?.let(::drawStrokeScreen)

            // Canvas border
            drawRect(
                color = Color.Black.copy(alpha = 0.12f),
                topLeft = tl,
                size = Size(sw, sh),
                style = Stroke(width = outlineWidthPx)
            )

            // Circular cursor only when inside canvas
            hoverPosCanvas?.let { cp ->
                val center = canvasToScreen(cp)
                drawCircle(
                    color = cursorColor,
                    radius = toolWidth() / 2f * zoom,
                    center = center,
                    style = Stroke(width = outlineWidthPx)
                )
            }
        }
    }
}
