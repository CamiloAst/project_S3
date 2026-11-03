package uq.seminario.projects3.avatar

import android.content.Context

/**
 * Guarda el acompañante elegido en SharedPreferences.
 *
 * A diferencia del sessionId, este archivo sí entra en las copias de seguridad: al restaurar
 * la app en otro teléfono el usuario conserva a su acompañante y no tiene que volver a elegir.
 */
object AvatarPreferences {

    private const val PREFS_NAME = "care_avatar"
    private const val KEY_AVATAR = "avatar"

    /** Acompañante guardado, o null si el usuario todavía no ha elegido. */
    fun load(context: Context): AssistantAvatar? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AssistantAvatar.fromStoredName(prefs.getString(KEY_AVATAR, null))
    }

    fun save(context: Context, avatar: AssistantAvatar) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_AVATAR, avatar.name)
            .apply()
    }
}
