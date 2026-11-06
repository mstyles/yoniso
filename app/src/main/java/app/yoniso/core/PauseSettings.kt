package app.yoniso.core

/** How long the breathing pause lasts before Continue and Exit appear. */
enum class PauseDuration(val seconds: Int) {
    FIVE(5),
    TEN(10),
    FIFTEEN(15);

    val millis: Long get() = seconds * 1_000L

    companion object {
        val DEFAULT = TEN

        /** The option lasting [seconds], or [DEFAULT] if there is none (e.g. a stale stored value). */
        fun fromSeconds(seconds: Int?): PauseDuration = entries.firstOrNull { it.seconds == seconds } ?: DEFAULT
    }
}

/** How long after a pause is shown before the same app can be paused again. */
enum class CooldownDuration(val minutes: Int) {
    NONE(0),
    ONE(1),
    FIVE(5),
    FIFTEEN(15),
    THIRTY(30);

    val millis: Long get() = minutes * 60_000L

    companion object {
        val DEFAULT = FIVE

        /** The option lasting [minutes], or [DEFAULT] if there is none (e.g. a stale stored value). */
        fun fromMinutes(minutes: Int?): CooldownDuration = entries.firstOrNull { it.minutes == minutes } ?: DEFAULT
    }
}

/** The user's settings, as far as the pause logic needs them. */
data class PauseSettings(
    val enabled: Boolean = true,
    val selectedPackages: Set<String> = emptySet(),
    val pauseDuration: PauseDuration = PauseDuration.DEFAULT,
    val cooldownDuration: CooldownDuration = CooldownDuration.DEFAULT,
)
