package uq.seminario.projects3.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uq.seminario.projects3.images.ContextImage
import uq.seminario.projects3.ui.theme.ContextImageBorder
import uq.seminario.projects3.ui.theme.ContextImageSurface
import uq.seminario.projects3.ui.theme.ProjectS3Theme

/**
 * Ilustración del banco local que acompaña la respuesta (Modelo CARE).
 *
 * Complementariedad: muestra el tema de la conversación (la música, la cita médica...) junto a
 * la voz y al resumen corto, sin repetirlos. Es apoyo visual: no se puede tocar ni lleva
 * información que no esté en la voz o el texto (Asignación).
 * Equivalencia: TalkBack lee "Imagen: <descripción>", así quien no la ve recibe lo mismo.
 * La ilustración conserva su proporción (3:2) y se centra; el fondo de la tarjeta rellena el
 * resto en pantallas anchas.
 */
@Composable
fun ContextImageCard(
    image: ContextImage,
    modifier: Modifier = Modifier,
    imageHeight: Dp = 150.dp,
    containerColor: Color = ContextImageSurface,
    borderColor: Color = ContextImageBorder
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Imagen: ${image.description}" },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.5.dp, borderColor)
    ) {
        Image(
            painter = painterResource(image.imageRes),
            // La descripción ya está en la tarjeta: así TalkBack la anuncia una sola vez
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(imageHeight)
                .padding(vertical = 8.dp)
        )
    }
}

@Preview(name = "ContextImageCard", showBackground = true, widthDp = 390)
@Composable
private fun ContextImageCardPreview() {
    ProjectS3Theme {
        ContextImageCard(image = ContextImage.MUSICA)
    }
}
