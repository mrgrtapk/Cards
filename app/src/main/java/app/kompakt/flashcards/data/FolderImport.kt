package app.kompakt.flashcards.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import app.kompakt.flashcards.core.DeckParser
import app.kompakt.flashcards.core.IncomingFile
import app.kompakt.flashcards.core.unzipDeckFiles

/**
 * Reads deck files from a folder on the phone that the user picked with the system
 * folder picker (the offline alternative to Wi-Fi sync).
 */
object FolderImport {
    private const val MAX_FILE_BYTES = 20L * 1024 * 1024
    private const val PREFS = "flashcards"
    private const val KEY_TREE = "importTree"

    fun read(context: Context, treeUri: Uri): List<IncomingFile> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val out = mutableListOf<IncomingFile>()

        fun walk(dir: DocumentFile, prefix: String, depth: Int) {
            if (depth > 16) return
            for (f in dir.listFiles()) {
                val name = f.name ?: continue
                if (name.startsWith(".")) continue
                if (f.isDirectory) {
                    walk(f, "$prefix$name/", depth + 1)
                } else if (DeckParser.isSupported(name) && f.length() <= MAX_FILE_BYTES) {
                    val text = context.contentResolver.openInputStream(f.uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: continue
                    out += IncomingFile(prefix + name, text)
                }
            }
        }
        walk(root, "", 0)
        return out
    }

    /**
     * Files picked one by one: deck files are read as they are, and a .zip is opened up so
     * its folders become folders. Anything else is skipped.
     */
    fun readFiles(context: Context, uris: List<Uri>): List<IncomingFile> = uris.flatMap { uri ->
        val name = displayName(context, uri) ?: return@flatMap emptyList()
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes()
        } ?: return@flatMap emptyList()
        when {
            name.endsWith(".zip", ignoreCase = true) -> unzipDeckFiles(bytes)
            DeckParser.isSupported(name) && bytes.size <= MAX_FILE_BYTES ->
                listOf(IncomingFile(name, bytes.toString(Charsets.UTF_8)))
            else -> emptyList()
        }
    }

    fun folderName(context: Context, treeUri: Uri): String =
        DocumentFile.fromTreeUri(context, treeUri)?.name ?: "folder"

    /** The file name the user chose in the system save dialog. */
    fun displayName(context: Context, uri: Uri): String? =
        runCatching { DocumentFile.fromSingleUri(context, uri)?.name }.getOrNull()

    fun rememberFolder(context: Context, treeUri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TREE, treeUri.toString()).apply()
    }

    fun lastFolder(context: Context): Uri? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TREE, null)?.let(Uri::parse)
}
