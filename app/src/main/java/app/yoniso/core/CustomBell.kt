package app.yoniso.core

/**
 * A bell the user added from their own files. Its sound is a copy kept in the app's private storage,
 * under [fileName]. Settings refer to it by [id], like a bundled bell.
 */
data class CustomBell(val fileName: String, val name: String) {
    val id: String get() = ID_PREFIX + fileName

    companion object {
        const val ID_PREFIX = "custom_"

        /** File names are random UUIDs, so a stored ID can never point outside the bells folder. */
        private val FILE_NAME = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

        /** The file name in a custom bell's [id], or null if [id] isn't a well-formed custom bell ID. */
        fun fileNameOf(id: String): String? =
            id.removePrefix(ID_PREFIX).takeIf { id.startsWith(ID_PREFIX) && FILE_NAME.matches(it) }

        fun isValidFileName(fileName: String): Boolean = FILE_NAME.matches(fileName)
    }
}

/** Why a sound file can't be a bell. */
enum class CustomBellProblem {
    TOO_LARGE,
    TOO_LONG,

    /** Not a sound Android can play, or empty. */
    UNREADABLE,
}

/** What a custom bell may be, and how its name is kept tidy. */
object CustomBellRules {
    const val MAX_BYTES = 5L * 1024 * 1024
    const val MAX_DURATION_MILLIS = 60_000L
    const val MAX_NAME_LENGTH = 40
    const val FALLBACK_NAME = "My bell"

    /** The first problem with a file of [sizeBytes] whose sound lasts [durationMillis] (null if unknown). */
    fun check(sizeBytes: Long, durationMillis: Long?): CustomBellProblem? = when {
        sizeBytes > MAX_BYTES -> CustomBellProblem.TOO_LARGE
        sizeBytes <= 0 || durationMillis == null || durationMillis <= 0 -> CustomBellProblem.UNREADABLE
        durationMillis > MAX_DURATION_MILLIS -> CustomBellProblem.TOO_LONG
        else -> null
    }

    /** A name from a file's display name: without its extension or underscores, e.g. "temple_gong.mp3" → "temple gong". */
    fun defaultName(displayName: String?): String {
        val base = displayName?.substringBeforeLast('.')?.replace('_', ' ').orEmpty()
        return cleanName(base) ?: FALLBACK_NAME
    }

    /** [input] on one line, trimmed and cut to [MAX_NAME_LENGTH], or null if nothing is left. */
    fun cleanName(input: String): String? {
        val oneLine = input.map { if (it.isISOControl()) ' ' else it }.joinToString("")
        val collapsed = oneLine.trim().replace(Regex("\\s+"), " ")
        return collapsed.take(MAX_NAME_LENGTH).trimEnd().ifEmpty { null }
    }

    /**
     * The custom bells as stored: one per line, "fileName<TAB>name". Names never hold tabs or line
     * breaks ([cleanName] turns them into spaces). Malformed lines and repeated files are dropped.
     */
    fun encode(bells: List<CustomBell>): String =
        bells.joinToString("\n") { "${it.fileName}\t${cleanName(it.name) ?: FALLBACK_NAME}" }

    fun decode(stored: String?): List<CustomBell> {
        if (stored.isNullOrEmpty()) return emptyList()
        return stored.lineSequence()
            .mapNotNull { line ->
                val fileName = line.substringBefore('\t', missingDelimiterValue = "")
                if (!CustomBell.isValidFileName(fileName)) return@mapNotNull null
                CustomBell(fileName, cleanName(line.substringAfter('\t')) ?: FALLBACK_NAME)
            }
            .distinctBy { it.fileName }
            .toList()
    }
}
