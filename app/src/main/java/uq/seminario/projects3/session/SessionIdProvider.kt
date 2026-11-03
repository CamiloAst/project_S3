package uq.seminario.projects3.session

import android.content.Context
import java.util.UUID

/**
 * Identificador de sesión único por instalación.
 *
 * Se genera una sola vez (UUID aleatorio) y se guarda en SharedPreferences para que el
 * historial conversacional de cada usuario nunca se mezcle con el de otro dispositivo.
 * El archivo está excluido de las copias de seguridad (ver res/xml/backup_rules.xml y
 * data_extraction_rules.xml) para que dos dispositivos restaurados no compartan sesión.
 */
object SessionIdProvider {

    const val PREFS_NAME = "care_session"
    private const val KEY_SESSION_ID = "session_id"

    @Volatile
    private var cachedSessionId: String? = null

    fun get(context: Context): String {
        cachedSessionId?.let { return it }
        return synchronized(this) {
            cachedSessionId ?: loadOrCreate(context.applicationContext).also { cachedSessionId = it }
        }
    }

    private fun loadOrCreate(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_SESSION_ID, null)?.let { return it }

        // Formato compatible con SESSION_ID_REGEX del backend (/^[\w\-.:]+$/, máx. 128)
        val newId = "android_${UUID.randomUUID()}"
        prefs.edit().putString(KEY_SESSION_ID, newId).apply()
        return newId
    }
}
