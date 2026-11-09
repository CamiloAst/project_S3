package uq.seminario.projects3.images

import androidx.annotation.DrawableRes
import uq.seminario.projects3.R

/**
 * Banco local de ilustraciones que acompañan las respuestas (Modelo CARE).
 *
 * El backend no envía imágenes: devuelve `image_key` y la app muestra la ilustración
 * guardada en `res/drawable/img_<apiValue>.xml`. Así no se descarga nada y Gemini solo
 * gasta unos pocos tokens en elegir la clave.
 *
 * - Complementariedad: la imagen ilustra el tema de la conversación sin repetir el texto.
 * - Asignación: es apoyo visual; ningún dato ni acción depende solo de ella.
 * - Redundancia: ante una posible emergencia la imagen es siempre [AYUDA_CERCANA].
 * - Equivalencia: `description` es el texto que lee TalkBack, para quien no la ve.
 *
 * `apiValue` debe coincidir con las claves de `backend/services/imageCatalog.js`; ambos lados
 * se prueban contra `backend/test/image-keys.json`. Para cambiar una ilustración basta con
 * reemplazar el recurso `img_<apiValue>` (vector o imagen) manteniendo el nombre.
 */
enum class ContextImage(
    val apiValue: String,
    @param:DrawableRes val imageRes: Int,
    val description: String
) {
    SALUDO("saludo", R.drawable.img_saludo, "Taza de café caliente frente a una ventana con sol"),
    SALUD("salud", R.drawable.img_salud, "Carpeta médica con un corazón"),
    AGENDA("agenda", R.drawable.img_agenda, "Calendario con un día marcado y un reloj"),
    FAMILIA("familia", R.drawable.img_familia, "Familia reunida bajo un corazón"),
    MASCOTAS("mascotas", R.drawable.img_mascotas, "Un perro y un gato sentados juntos"),
    MUSICA("musica", R.drawable.img_musica, "Radio antigua con notas musicales"),
    LECTURA("lectura", R.drawable.img_lectura, "Libro abierto y unas gafas"),
    COMIDA("comida", R.drawable.img_comida, "Plato de sopa caliente con una arepa"),
    NATURALEZA("naturaleza", R.drawable.img_naturaleza, "Sendero entre árboles bajo el sol"),
    ESPIRITUALIDAD("espiritualidad", R.drawable.img_espiritualidad, "Vela encendida con luz cálida"),
    DESCANSO("descanso", R.drawable.img_descanso, "Cama bajo un cielo de noche con luna y estrellas"),
    CLIMA("clima", R.drawable.img_clima, "Sol detrás de una nube con lluvia"),
    RECUERDOS("recuerdos", R.drawable.img_recuerdos, "Fotografías antiguas"),
    ALEGRIA("alegria", R.drawable.img_alegria, "Sol radiante sobre flores de colores"),
    CALMA("calma", R.drawable.img_calma, "Hoja verde dentro de círculos serenos"),
    ACOMPANAMIENTO("acompanamiento", R.drawable.img_acompanamiento, "Dos personas sentadas juntas en una banca"),
    ESCUCHA("escucha", R.drawable.img_escucha, "Dos globos de conversación con un signo de pregunta"),

    /** Reservada para posibles emergencias: nunca se muestra en otra respuesta. */
    AYUDA_CERCANA("ayuda_cercana", R.drawable.img_ayuda_cercana, "Una persona apoya la mano en el hombro de otra para ayudarla");

    companion object {
        val DEFAULT = SALUDO

        /** Imagen de una clave del backend; null si falta o no existe en este banco. */
        fun fromApiValue(value: String?): ContextImage? {
            val key = value?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.apiValue == key }
        }

        /** Imagen por defecto de cada emoción (mismas que `DEFAULT_IMAGE_BY_EMOTION` del backend). */
        fun defaultFor(emotion: String): ContextImage = when (emotion.trim().uppercase()) {
            "POSITIVA" -> ALEGRIA
            "NEGATIVA_TRISTEZA_SOLEDAD" -> ACOMPANAMIENTO
            "NEGATIVA_ANSIEDAD_PREOCUPACION" -> CALMA
            "AMBIGUA" -> ESCUCHA
            else -> DEFAULT
        }

        /**
         * Imagen que muestra la app para una respuesta: en una posible emergencia siempre
         * [AYUDA_CERCANA]; si no, la del backend, o la de la emoción si la clave falta (backend
         * anterior), es desconocida (backend más nuevo) o es la reservada para emergencias.
         */
        fun resolve(apiValue: String?, emotion: String, action: String): ContextImage {
            if (action.trim().lowercase() == "emergency_alert") return AYUDA_CERCANA
            val image = fromApiValue(apiValue)
            return if (image == null || image == AYUDA_CERCANA) defaultFor(emotion) else image
        }
    }
}
