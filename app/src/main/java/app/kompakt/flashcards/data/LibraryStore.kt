package app.kompakt.flashcards.data

import app.kompakt.flashcards.core.ImportReport
import app.kompakt.flashcards.core.IncomingFile
import app.kompakt.flashcards.core.LibraryCodec
import app.kompakt.flashcards.core.LibraryData
import app.kompakt.flashcards.core.importFiles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Holds the library in memory and saves it to a JSON file in the app's private storage.
 * Saves happen on a background thread; rapid changes (e.g. reviewing cards quickly)
 * are coalesced into one write.
 */
class LibraryStore(private val file: File) {

    private val lock = Any()
    private val _data = MutableStateFlow(load())
    val data: StateFlow<LibraryData> = _data

    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "library-save") }
    private val saveQueued = AtomicBoolean(false)

    fun update(transform: (LibraryData) -> LibraryData) {
        synchronized(lock) { _data.value = transform(_data.value) }
        scheduleSave()
    }

    fun <T> updateAndGet(transform: (LibraryData) -> Pair<LibraryData, T>): T {
        val result: T
        synchronized(lock) {
            val (next, r) = transform(_data.value)
            _data.value = next
            result = r
        }
        scheduleSave()
        return result
    }

    fun importFiles(files: List<IncomingFile>, mirror: Boolean = false, appendOnly: Boolean = false): ImportReport =
        updateAndGet { it.importFiles(files, mirror, appendOnly) }

    private fun load(): LibraryData = try {
        if (file.exists()) LibraryCodec.decode(file.readText()) else LibraryData()
    } catch (e: Exception) {
        // Keep the unreadable file so nothing is lost, and start fresh.
        runCatching { file.copyTo(File(file.parentFile, file.name + ".unreadable"), overwrite = true) }
        LibraryData()
    }

    private fun scheduleSave() {
        if (!saveQueued.compareAndSet(false, true)) return
        writer.execute {
            saveQueued.set(false)
            val snapshot = _data.value
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(LibraryCodec.encode(snapshot))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }
}
