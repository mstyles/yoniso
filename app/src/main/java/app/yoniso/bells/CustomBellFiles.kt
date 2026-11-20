package app.yoniso.bells

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import app.yoniso.core.CustomBell
import app.yoniso.core.CustomBellProblem
import app.yoniso.core.CustomBellRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * The sound files of custom bells, kept in `filesDir/bells/`. A picked file is copied there, so it
 * keeps working if the original is moved or deleted, and no lasting access to it is needed. Files
 * here are never backed up or transferred (see `data_extraction_rules.xml`).
 */
internal class CustomBellFiles(context: Context) {
    private val app = context.applicationContext
    private val dir = File(app.filesDir, DIR)

    sealed interface ImportResult {
        /** Copied and checked. Not in the bell list until it's named and saved. */
        data class Imported(val fileName: String, val suggestedName: String) : ImportResult
        data class Refused(val problem: CustomBellProblem) : ImportResult
    }

    /** The file of the custom bell [id], or null if [id] isn't a custom bell or its file is gone. */
    fun fileOf(id: String): File? {
        val fileName = CustomBell.fileNameOf(id) ?: return null
        return File(dir, fileName).takeIf { it.isFile }
    }

    /** Copies the sound at [uri] in and checks it can be a bell. A refused file isn't kept. */
    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val (displayName, size) = describe(uri)
        if (size != null && size > CustomBellRules.MAX_BYTES) {
            return@withContext ImportResult.Refused(CustomBellProblem.TOO_LARGE)
        }
        dir.mkdirs()
        val fileName = UUID.randomUUID().toString()
        val file = File(dir, fileName)
        val copied = try {
            copy(uri, file)
        } catch (e: IOException) {
            Log.w(TAG, "Could not copy the bell", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not read the bell", e)
            null
        }
        val problem = when (copied) {
            null -> CustomBellProblem.UNREADABLE
            else -> CustomBellRules.check(copied, durationOf(file))
        }
        if (problem != null) {
            file.delete()
            ImportResult.Refused(problem)
        } else {
            ImportResult.Imported(fileName, CustomBellRules.defaultName(displayName))
        }
    }

    /** Deletes a custom bell's file. Safe to call for a file that's already gone. */
    suspend fun delete(fileName: String) = withContext(Dispatchers.IO) {
        if (CustomBell.isValidFileName(fileName)) File(dir, fileName).delete()
    }

    /** Deletes files no bell refers to, e.g. one imported but never saved because the app was closed. */
    suspend fun deleteUnused(bells: List<CustomBell>, keep: Set<String>) = withContext(Dispatchers.IO) {
        val used = bells.map { it.fileName }.toSet() + keep
        dir.listFiles()?.filter { it.name !in used }?.forEach { it.delete() }
    }

    private fun describe(uri: Uri): Pair<String?, Long?> = try {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null to null
                val name = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    .takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getString)
                val size = cursor.getColumnIndex(OpenableColumns.SIZE)
                    .takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong)
                name to size
            } ?: (null to null)
    } catch (e: Exception) {
        Log.w(TAG, "Could not describe the bell file", e)
        null to null
    }

    /**
     * Copies [uri] to [file] and returns its size. Stops a byte past the limit, since the reported
     * size can be missing or wrong, so the size returned is then over [CustomBellRules.MAX_BYTES].
     */
    private fun copy(uri: Uri, file: File): Long? {
        val input = app.contentResolver.openInputStream(uri) ?: return null
        var total = 0L
        input.use { source ->
            file.outputStream().use { target ->
                val buffer = ByteArray(64 * 1024)
                while (total <= CustomBellRules.MAX_BYTES) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    target.write(buffer, 0, read)
                    total += read
                }
            }
        }
        return total
    }

    /** The sound's length, or null if Android can't read or play it. */
    private fun durationOf(file: File): Long? {
        val duration = try {
            MediaMetadataRetriever().run {
                try {
                    setDataSource(file.path)
                    if (extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != "yes") return null
                    extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                } finally {
                    release()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the bell's length", e)
            return null
        }
        // Reading the length isn't proof it plays: prepare a player too.
        val player = MediaPlayer()
        return try {
            player.setAudioAttributes(BellPlayer.ATTRIBUTES)
            player.setDataSource(file.path)
            player.prepare()
            duration
        } catch (e: Exception) {
            Log.w(TAG, "The bell won't play", e)
            null
        } finally {
            player.release()
        }
    }

    private companion object {
        const val TAG = "Yoniso"
        const val DIR = "bells"
    }
}
