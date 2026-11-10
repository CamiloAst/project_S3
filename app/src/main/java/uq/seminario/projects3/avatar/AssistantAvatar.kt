package uq.seminario.projects3.avatar

import androidx.annotation.DrawableRes
import uq.seminario.projects3.R
import uq.seminario.projects3.audio.AudioEngine

/**
 * Acompañantes que el usuario puede elegir. El elegido es quien "habla" con el usuario:
 * su imagen aparece en todas las pantallas de la conversación y su voz lee las respuestas.
 *
 * `name` es el valor que se guarda en preferencias (ver [AvatarPreferences]): no se deben
 * renombrar las constantes sin migrar el valor guardado.
 * `apiValue` viaja como `companion` en POST /api/chat: el backend elige con él la voz neural
 * (Gonzalo / Salomé) y el género con el que el asistente habla de sí.
 * `localTtsPitch` es el tono del TTS local de respaldo: Android no indica el género de sus
 * voces, así que para el compañero se baja el tono de la voz española disponible.
 * `imageRes` es la imagen base (saludo), que también sirve de gesto neutral; las demás son los
 * gestos de [AvatarExpression] (ver [imageFor]).
 */
enum class AssistantAvatar(
    @param:DrawableRes val imageRes: Int,
    @param:DrawableRes private val happyImageRes: Int,
    @param:DrawableRes private val empatheticImageRes: Int,
    @param:DrawableRes private val thoughtfulImageRes: Int,
    @param:DrawableRes private val talkingImageRes: Int,
    val label: String,
    val imageDescription: String,
    val apiValue: String,
    val localTtsPitch: Float
) {
    MALE(
        imageRes = R.drawable.avatar_companero,
        happyImageRes = R.drawable.avatar_companero_alegre,
        empatheticImageRes = R.drawable.avatar_companero_triste,
        thoughtfulImageRes = R.drawable.avatar_companero_pensativo,
        talkingImageRes = R.drawable.avatar_companero_hablando,
        label = "Compañero",
        imageDescription = "Hombre de cabello corto con gafas verdes que saluda con la mano",
        apiValue = "male",
        localTtsPitch = 0.8f
    ),
    FEMALE(
        imageRes = R.drawable.avatar_companera,
        happyImageRes = R.drawable.avatar_companera_alegre,
        empatheticImageRes = R.drawable.avatar_companera_triste,
        thoughtfulImageRes = R.drawable.avatar_companera_pensativa,
        talkingImageRes = R.drawable.avatar_companera_hablando,
        label = "Compañera",
        imageDescription = "Mujer de cabello largo con gafas verdes que saluda con la mano",
        apiValue = "female",
        localTtsPitch = AudioEngine.DEFAULT_WARM_PITCH
    );

    /** Nombre con posesivo para los textos de pantalla: "Tu compañero" / "Tu compañera". */
    val title: String get() = "Tu ${label.lowercase()}"

    /** Imagen del acompañante con el gesto indicado. */
    @DrawableRes
    fun imageFor(expression: AvatarExpression): Int = when (expression) {
        AvatarExpression.NEUTRAL -> imageRes
        AvatarExpression.HAPPY -> happyImageRes
        AvatarExpression.EMPATHETIC -> empatheticImageRes
        AvatarExpression.THOUGHTFUL -> thoughtfulImageRes
        AvatarExpression.TALKING -> talkingImageRes
    }

    companion object {
        // Si aún no hay elección, se usa el femenino porque la voz neural del backend
        // (es-CO-SalomeNeural) es femenina
        val DEFAULT = FEMALE

        /** Convierte el valor guardado en preferencias; null si no hay elección o no es válido. */
        fun fromStoredName(storedName: String?): AssistantAvatar? =
            entries.firstOrNull { it.name == storedName }
    }
}
