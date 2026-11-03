package uq.seminario.projects3.network

import android.util.Log
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import uq.seminario.projects3.BuildConfig
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
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
 * Respuesta ya validada de POST /api/chat que consume el ViewModel.
 */
data class ChatResponse(
    val sessionId: String,
    val data: ChatData
)

/**
 * Datos multimodales sincronizados del Modelo CARE devueltos por el backend,
 * incluyendo síntesis de voz neural en Base64 y reloj maestro de sincronía (audio_duration_ms).
 * Los textos nunca son nulos: los campos ausentes llegan como "" y el ViewModel aplica sus
 * valores por defecto.
 */
data class ChatData(
    val spoken_text: String,
    val screen_text: String,
    val user_emotion: String,
    val suggested_action: String,
    val audio_base64: String? = null,
    val audio_duration_ms: Long? = null
)

/**
 * JSON crudo del backend. Gson ignora la nulabilidad de Kotlin y deja en `null` cualquier
 * campo ausente, por eso todos los campos son opcionales y se validan en [toChatResponse].
 */
data class ChatResponseDto(
    @SerializedName("success")
    val success: Boolean? = null,
    @SerializedName("sessionId")
    val sessionId: String? = null,
    @SerializedName("data")
    val data: ChatDataDto? = null
)

data class ChatDataDto(
    @SerializedName("spoken_text")
    val spokenText: String? = null,
    @SerializedName("screen_text")
    val screenText: String? = null,
    @SerializedName("user_emotion")
    val userEmotion: String? = null,
    @SerializedName("suggested_action")
    val suggestedAction: String? = null,
    @SerializedName("audio_base64")
    val audioBase64: String? = null,
    @SerializedName("audio_duration_ms")
    val audioDurationMs: Long? = null
)

/**
 * Convierte la respuesta cruda en [ChatResponse] o devuelve `null` si no es utilizable
 * (success distinto de true, sin `data` o sin ningún texto que mostrar ni leer).
 */
fun ChatResponseDto.toChatResponse(requestSessionId: String): ChatResponse? {
    if (success != true) return null
    val raw = data ?: return null
    val spoken = raw.spokenText?.trim().orEmpty()
    val screen = raw.screenText?.trim().orEmpty()
    if (spoken.isEmpty() && screen.isEmpty()) return null

    return ChatResponse(
        sessionId = sessionId ?: requestSessionId,
        data = ChatData(
            spoken_text = spoken,
            screen_text = screen,
            user_emotion = raw.userEmotion?.trim().orEmpty(),
            suggested_action = raw.suggestedAction?.trim().orEmpty(),
            audio_base64 = raw.audioBase64?.takeIf { it.isNotBlank() },
            audio_duration_ms = raw.audioDurationMs
        )
    )
}

/**
 * Contrato Retrofit para la comunicación con el backend local en Node.js.
 */
interface ChatApiService {
    @POST("api/chat")
    suspend fun sendMessage(@Body request: ChatRequest): ChatResponseDto
}

/**
 * Cliente Retrofit singleton.
 *
 * Tiempos de espera: el backend puede tardar hasta ~16 s en el peor caso (Gemini 8 s +
 * Edge TTS ~8 s), por lo que la lectura espera 25 s y la llamada completa 30 s. Si se
 * suben GEMINI_TIMEOUT_MS o los límites de ttsService.js, revisar estos valores: si la app
 * corta antes, usa su respaldo local mientras el backend ya guardó otra respuesta.
 */
object ChatApiClient {

    /**
     * URL base por tipo de build (`care.apiBaseUrl.debug` / `care.apiBaseUrl.release` en
     * local.properties). Por defecto en debug: el host desde el emulador (10.0.2.2:3000).
     */
    private val BASE_URL = BuildConfig.CARE_API_BASE_URL.let { if (it.endsWith("/")) it else "$it/" }

    /** Token compartido con el backend (`API_ACCESS_TOKEN`); vacío = no se envía. */
    private val API_TOKEN = BuildConfig.CARE_API_TOKEN

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
        // El cuerpo contiene conversaciones de salud: solo se registra en debug
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        redactHeader("Authorization")
    }

    private val authInterceptor = Interceptor { chain ->
        val request = if (API_TOKEN.isNotBlank()) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $API_TOKEN")
                .build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ChatApiService = retrofit.create(ChatApiService::class.java)

    /**
     * Envía el mensaje transcrito por el adulto mayor hacia POST {BASE_URL}api/chat.
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
            val response = apiService.sendMessage(request).toChatResponse(sessionId)
            if (response != null) {
                Result.success(response)
            } else {
                Result.failure(IllegalStateException("Respuesta del servidor incompleta o con success!=true"))
            }
        } catch (e: CancellationException) {
            // La cancelación (el usuario interrumpió) no debe tratarse como fallo de red
            throw e
        } catch (e: HttpException) {
            if (e.code() == 401) {
                // Configuración, no red: sin este aviso la app parecería simplemente "sin conexión"
                Log.e(LOG_TAG, "El backend rechazó el token (401): revisa que care.apiToken coincida con API_ACCESS_TOKEN.")
            }
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
