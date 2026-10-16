package uq.seminario.projects3.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Modelo de datos del esquema estructurado CARE devuelto por el backend Node.js / Gemini.
 */
data class CareResponseData(
    val spokenText: String,
    val screenText: String,
    val userEmotion: String,
    val suggestedAction: String
)

/**
 * Cliente HTTP ligero y nativo para comunicar la app Android con el backend Node.js.
 * Utiliza HttpURLConnection y org.json nativo de Android (cero dependencias externas adicionales).
 */
object ChatApiClient {

    /**
     * NOTA DE CONEXIÓN:
     * - Si ejecutas en el EMULADOR de Android Studio: Usa "http://10.0.2.2:3000/api/chat"
     *   (10.0.2.2 es la IP virtual que apunta al localhost de la máquina host).
     * - Si ejecutas en un CELULAR FÍSICO conectado por USB o Wi-Fi: Reemplaza 10.0.2.2 por la
     *   IP local de tu PC (ej: "http://192.168.1.50:3000/api/chat").
     */
    var baseUrl: String = "http://10.0.2.2:3000/api/chat"

    suspend fun sendChatMessage(
        message: String,
        sessionId: String = "android_senior_session"
    ): Result<CareResponseData> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(baseUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 7000
                readTimeout = 12000
                doOutput = true
                doInput = true
            }

            val requestJson = JSONObject().apply {
                put("message", message)
                put("sessionId", sessionId)
            }

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            val statusCode = connection.responseCode
            val inputStream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseBody = BufferedReader(InputStreamReader(inputStream, "UTF-8")).use {
                it.readText()
            }

            if (statusCode in 200..299) {
                val rootJson = JSONObject(responseBody)
                val dataObj = rootJson.getJSONObject("data")
                val responseData = CareResponseData(
                    spokenText = dataObj.optString("spoken_text", ""),
                    screenText = dataObj.optString("screen_text", ""),
                    userEmotion = dataObj.optString("user_emotion", "neutro"),
                    suggestedAction = dataObj.optString("suggested_action", "continue")
                )
                Result.success(responseData)
            } else {
                Result.failure(Exception("Error HTTP $statusCode: $responseBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }
}
