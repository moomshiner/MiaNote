import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun ExitButton(
    modifier: Modifier = Modifier,
    highlightColor: Color = Color(0xFF00AEEF),
    onClick: () -> Unit
) {
    SpriteButton(
        resName = "exit.png",
        modifier = modifier,
        highlightColor = highlightColor,
        onClick = onClick
    )
}