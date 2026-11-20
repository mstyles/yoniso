package app.yoniso.bells

import android.content.Context
import android.net.Uri
import app.yoniso.core.CustomBell
import app.yoniso.data.BellRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Adding, renaming and deleting custom bells, for the bells screen. Changes run on [BellAlarms.scope],
 * so they finish even if the screen closes. None of them moves the next ring.
 */
internal class CustomBellLibrary(context: Context, private val repository: BellRepository) {
    private val files = CustomBellFiles(context)

    /** Imported files not yet saved or discarded, which the clean-up must leave alone. */
    private val pending = ConcurrentHashMap.newKeySet<String>()

    val bells: Flow<List<CustomBell>> = repository.customBells

    /**
     * Copies and checks the file at [uri]. A file that passes is kept apart until [save] or [discard].
     * Files left over from an earlier import that was never saved are cleared out first.
     */
    suspend fun import(uri: Uri): CustomBellFiles.ImportResult {
        files.deleteUnused(repository.customBells(), keep = pending)
        return files.import(uri).also {
            if (it is CustomBellFiles.ImportResult.Imported) pending += it.fileName
        }
    }

    /** Adds an imported file under [name] and makes it the bell that rings. */
    fun save(fileName: String, name: String) {
        BellAlarms.scope.launch {
            repository.addCustomBell(CustomBell(fileName, name))
            pending -= fileName
        }
    }

    /** Drops an imported file the user chose not to keep. */
    fun discard(fileName: String) {
        BellAlarms.scope.launch {
            files.delete(fileName)
            pending -= fileName
        }
    }

    fun rename(bell: CustomBell, name: String) {
        BellAlarms.scope.launch { repository.renameCustomBell(bell.fileName, name) }
    }

    /** Deletes the bell and its file. If it was in use, the Plum Village bell takes over. */
    fun delete(bell: CustomBell) {
        BellAlarms.scope.launch {
            repository.removeCustomBell(bell.fileName)
            files.delete(bell.fileName)
        }
    }
}
