package uq.seminario.projects3.avatar

/**
 * Gestos del acompañante: el avatar representa la emoción de lo que está diciendo.
 *
 * La respuesta del asistente se ajusta a la emoción que Gemini detecta en el usuario
 * (`user_emotion`): ante la alegría responde con alegría; ante la tristeza o la preocupación,
 * con comprensión; ante un mensaje ambiguo, con duda. Por eso el gesto se deduce de
 * `user_emotion` y no hace falta un campo nuevo en la API. Así también funciona con el banco
 * de respaldo del backend y con el respaldo local de la app.
 *
 * Modelo CARE:
 * - Redundancia: el gesto refuerza el tono de la voz, cuya prosodia ya cambia por emoción.
 * - Asignación: es solo apoyo visual; nunca lleva por sí solo un dato ni una acción.
 * - Equivalencia: TalkBack lee el gesto ([spokenLabel]) como estado del avatar.
 */
enum class AvatarExpression(val spokenLabel: String) {
    /** Imagen base (saludo con sonrisa tranquila): inicio, escucha y respuestas `NEUTRA`. */
    NEUTRAL("gesto tranquilo"),

    /** `POSITIVA`. */
    HAPPY("gesto alegre"),

    /** `NEGATIVA_TRISTEZA_SOLEDAD`, `NEGATIVA_ANSIEDAD_PREOCUPACION` y la posible emergencia. */
    EMPATHETIC("gesto comprensivo"),

    /** `AMBIGUA`, la espera de la respuesta ("Pensando") y la reparación ("no te entendí"). */
    THOUGHTFUL("gesto pensativo"),

    /** Pose de hablar: se muestra mientras su voz lee el mensaje. No es una emoción. */
    TALKING("hablando");

    companion object {
        /**
         * Al empezar a hablar el avatar muestra primero su gesto emocional, como reacción a lo
         * que le dijeron, y luego la pose de hablar. Durante mensajes largos el ciclo se repite
         * para que la emoción siga a la vista mientras habla.
         */
        const val MOOD_POSE_MS = 1_600L
        const val TALKING_POSE_MS = 4_400L
        const val SPEAKING_CYCLE_MS = MOOD_POSE_MS + TALKING_POSE_MS

        /** Gesto que corresponde a la emoción del turno (desconocida o vacía → [NEUTRAL]). */
        fun forEmotion(userEmotion: String?): AvatarExpression {
            val normalized = userEmotion?.trim()?.uppercase().orEmpty()
            return when {
                normalized == "POSITIVA" -> HAPPY
                normalized.startsWith("NEGATIVA") -> EMPATHETIC
                normalized == "AMBIGUA" -> THOUGHTFUL
                else -> NEUTRAL
            }
        }

        /**
         * Pose visible mientras la voz suena, `cycleElapsedMs` milisegundos después de empezar
         * el ciclo actual. Con el gesto neutral habla todo el tiempo: su imagen es un saludo y
         * alternarla con la de hablar parecería que saluda una y otra vez.
         */
        fun speakingPose(mood: AvatarExpression, cycleElapsedMs: Long): AvatarExpression =
            if (mood == NEUTRAL || mood == TALKING || cycleElapsedMs % SPEAKING_CYCLE_MS >= MOOD_POSE_MS) {
                TALKING
            } else {
                mood
            }
    }
}
