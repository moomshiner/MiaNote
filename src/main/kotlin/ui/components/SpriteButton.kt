import androidx.compose.runtime.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.DpSize

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SpriteButton(
    resName: String,                      // "pencil.png", "eraser.png", "exit.png"
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    highlightColor: Color = Color(0xFF00AEEF),
    onClick: () -> Unit
) {
    val img: ImageBitmap = remember { useResource(resName) { loadImageBitmap(it) } }
    val pm = remember(img) { img.toPixelMap() }
    val sizeDp = with(LocalDensity.current) { DpSize(img.width.toDp(), img.height.toDp()) }

    var hoverOpaque by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val interactions = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(sizeDp) // native size; change to .size(40.dp) if you want fixed
            .onPointerEvent(PointerEventType.Move) { e ->
                val p = e.changes.first().position
                val x = p.x.toInt(); val y = p.y.toInt()
                hoverOpaque = x in 0 until img.width &&
                        y in 0 until img.height &&
                        pm[x, y].alpha > 0.05f
            }
            .onPointerEvent(PointerEventType.Exit) { hoverOpaque = false }
            .onPointerEvent(PointerEventType.Press) { pressed = hoverOpaque }
            .onPointerEvent(PointerEventType.Release) { pressed = false }
            .clickable(
                enabled = hoverOpaque,
                interactionSource = interactions,
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.TopStart
    ) {
        Image(bitmap = img, contentDescription = resName.removeSuffix(".png"))
        if (selected || hoverOpaque || pressed) {
            Image(
                bitmap = img,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                colorFilter = ColorFilter.tint(
                    when {
                        pressed -> highlightColor.copy(alpha = 0.60f)
                        selected -> highlightColor.copy(alpha = 0.35f)
                        else -> highlightColor.copy(alpha = 0.45f)
                    },
                    BlendMode.SrcIn
                )
            )
        }
    }
}