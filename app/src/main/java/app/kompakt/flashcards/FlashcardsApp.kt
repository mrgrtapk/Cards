package app.kompakt.flashcards

import android.app.Application
import app.kompakt.flashcards.data.LibraryStore
import app.kompakt.flashcards.data.SettingsStore
import java.io.File

class FlashcardsApp : Application() {
    val store: LibraryStore by lazy { LibraryStore(File(filesDir, "library.json")) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
}
