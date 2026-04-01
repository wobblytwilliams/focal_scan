package au.edu.cqu.focalapp.ui

import androidx.compose.ui.graphics.Color
import au.edu.cqu.focalapp.domain.model.AnimalColor

data class AnimalColorPalette(
    val containerColor: Color,
    val activeContainerColor: Color,
    val borderColor: Color,
    val selectionColor: Color,
    val previewColor: Color
)

fun AnimalColor.palette(): AnimalColorPalette {
    return when (this) {
        AnimalColor.BLUE -> AnimalColorPalette(
            containerColor = Color(0xFFE3F2FD),
            activeContainerColor = Color(0xFFBBDEFB),
            borderColor = Color(0xFF1E88E5),
            selectionColor = Color(0xFF90CAF9),
            previewColor = Color(0xFFD6EAFE)
        )

        AnimalColor.GREEN -> AnimalColorPalette(
            containerColor = Color(0xFFE8F5E9),
            activeContainerColor = Color(0xFFC8E6C9),
            borderColor = Color(0xFF43A047),
            selectionColor = Color(0xFFA5D6A7),
            previewColor = Color(0xFFDDF2DE)
        )

        AnimalColor.YELLOW -> AnimalColorPalette(
            containerColor = Color(0xFFFFF8E1),
            activeContainerColor = Color(0xFFFFECB3),
            borderColor = Color(0xFFF9A825),
            selectionColor = Color(0xFFFFE082),
            previewColor = Color(0xFFFFF0C7)
        )
    }
}
