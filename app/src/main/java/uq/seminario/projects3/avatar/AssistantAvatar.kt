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
 */
enum class AssistantAvatar(
    @param:DrawableRes val imageRes: Int,
    val label: String,
    val imageDescription: String,
    val apiValue: String,
    val localTtsPitch: Float
) {
    MALE(
        imageRes = R.drawable.avatar_companero,
        label = "Compañero",
        imageDescription = "Hombre de cabello corto con gafas verdes que saluda con la mano",
        apiValue = "male",
        localTtsPitch = 0.8f
    ),
    FEMALE(
        imageRes = R.drawable.avatar_companera,
        label = "Compañera",
        imageDescription = "Mujer de cabello largo con gafas verdes que saluda con la mano",
        apiValue = "female",
        localTtsPitch = AudioEngine.DEFAULT_WARM_PITCH
    );

    /** Nombre con posesivo para los textos de pantalla: "Tu compañero" / "Tu compañera". */
    val title: String get() = "Tu ${label.lowercase()}"

    companion object {
        // Si aún no hay elección, se usa el femenino porque la voz neural del backend
        // (es-CO-SalomeNeural) es femenina
        val DEFAULT = FEMALE

        /** Convierte el valor guardado en preferencias; null si no hay elección o no es válido. */
        fun fromStoredName(storedName: String?): AssistantAvatar? =
            entries.firstOrNull { it.name == storedName }
    }
}
