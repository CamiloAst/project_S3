package uq.seminario.projects3.network

import android.util.Log
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * Cuerpo de la petición POST /api/chat enviada al backend Node.js.
 */
data class ChatRequest(
    @SerializedName("sessionId")
    val sessionId: String,
    @SerializedName("message")
    val message: String
)

/**
 * Respuesta envolvente devuelta por el endpoint POST /api/chat.
 */
data class ChatResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("sessionId")
    val sessionId: String,
    @SerializedName("data")
    val data: ChatData
)

/**
 * Datos multimodales sincronizados del Modelo CARE devueltos por el backend,
 * incluyendo síntesis de voz neural en Base64 y reloj maestro de sincronía (audio_duration_ms).
 */
data class ChatData(
    @SerializedName("spoken_text")
    val spoken_text: String,
    @SerializedName("screen_text")
    val screen_text: String,
    @SerializedName("user_emotion")
    val user_emotion: String,
    @SerializedName("suggested_action")
    val suggested_action: String,
    @SerializedName("audio_base64")
    val audio_base64: String? = null,
    @SerializedName("audio_duration_ms")
    val audio_duration_ms: Long? = null
)

/**
 * Contrato Retrofit para la comunicación con el backend local en Node.js.
 */
interface ChatApiService {
    @POST("api/chat")
    suspend fun sendMessage(@Body request: ChatRequest): ChatResponse
}

/**
 * Cliente Retrofit singleton configurado con OkHttpClient (timeouts de 15s)
 * para soportar la latencia del LLM sin lanzar SocketTimeoutException.
 */
object ChatApiClient {

    /**
     * URL base hacia el localhost de la máquina host desde el emulador Android (10.0.2.2:3000).
     */
    private const val BASE_URL = "http://10.0.2.2:3000/"

    private const val LOG_TAG = "ChatApiClient"

    /**
     * Expresión regular que oculta el MP3 en Base64 dentro de los logs HTTP para evitar
     * saturar Logcat con decenas de kilobytes por respuesta, conservando el resto del JSON.
     */
    private val base64AudioRegex = Regex(""""audio_base64"\s*:\s*"([^"]+)"""")

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        val sanitized = base64AudioRegex.replace(message) { match ->
            """"audio_base64":"<MP3 Base64 omitido (${match.groupValues[1].length} chars)>""""
        }
        Log.d(LOG_TAG, sanitized)
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ChatApiService = retrofit.create(ChatApiService::class.java)

    /**
     * Envía el mensaje transcrito por el adulto mayor hacia POST http://10.0.2.2:3000/api/chat.
     * `sessionId` debe ser el identificador único de la instalación (ver SessionIdProvider).
     */
    suspend fun sendChatMessage(
        message: String,
        sessionId: String
    ): Result<ChatResponse> = withContext(Dispatchers.IO) {
        try {
            val request = ChatRequest(
                sessionId = sessionId,
                message = message
            )
            val response = apiService.sendMessage(request)
            if (response.success) {
                Result.success(response)
            } else {
                Result.failure(IllegalStateException("El servidor indicó success=false para la sesión $sessionId"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
