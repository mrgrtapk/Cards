package app.kompakt.flashcards.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kompakt.flashcards.R
import app.kompakt.flashcards.core.ImportReport
import app.kompakt.flashcards.core.exportZip
import app.kompakt.flashcards.data.FolderImport
import app.kompakt.flashcards.data.LibraryStore
import app.kompakt.flashcards.data.SettingsStore
import com.mudita.mmd.components.text.TextMMD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bringing cards in from files on the phone, and saving them out again. Shared by the
 * public Import page and the personal Sync page.
 */
class Importer internal constructor(
    private val context: Context,
    private val store: LibraryStore,
    private val scope: CoroutineScope,
) {
    /** Called after "Export all cards" saves successfully (to remember the backup date). */
    internal var onExported: () -> Unit = {}
    var report by mutableStateOf<ImportReport?>(null)
        private set
    var time by mutableStateOf<Long?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var exportNote by mutableStateOf<String?>(null)
        private set
    var lastFolder by mutableStateOf(FolderImport.lastFolder(context))
        private set
    val lastFolderName: String? get() = lastFolder?.let { FolderImport.folderName(context, it) }

    internal var pickFiles: () -> Unit = {}
    internal var pickFolder: () -> Unit = {}
    internal var saveExport: () -> Unit = {}

    fun chooseFiles() = pickFiles()
    fun chooseFolder() = pickFolder()
    fun exportAll() {
        exportNote = null
        saveExport()
    }

    /** Records the result of an import. */
    fun received(r: ImportReport) {
        report = r
        time = System.currentTimeMillis()
    }

    internal fun importFiles(uris: List<Uri>) = run("Couldn't read those files.") {
        store.importFiles(FolderImport.readFiles(context, uris))
    }

    fun importFolder(uri: Uri) = run("Couldn't read that folder.") {
        store.importFiles(FolderImport.read(context, uri))
    }

    internal fun rememberFolder(uri: Uri) {
        FolderImport.rememberFolder(context, uri)
        lastFolder = uri
    }

    internal fun writeExport(uri: Uri) {
        scope.launch {
            busy = true
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = store.data.value.exportZip()
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("no stream")
                }.isSuccess
            }
            busy = false
            if (ok) onExported()
            exportNote = if (ok) {
                "Saved all cards to “${FolderImport.displayName(context, uri) ?: "Cards.zip"}”."
            } else {
                "Couldn't save the file."
            }
        }
    }

    private fun run(failure: String, work: () -> ImportReport) {
        scope.launch {
            busy = true
            val r = withContext(Dispatchers.IO) { runCatching(work).getOrNull() }
            busy = false
            received(r ?: ImportReport(warnings = listOf(failure)))
        }
    }
}

@Composable
fun rememberImporter(store: LibraryStore, onExported: () -> Unit = {}): Importer {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importer = remember { Importer(context.applicationContext, store, scope) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) importer.importFiles(uris)
    }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            importer.rememberFolder(uri)
            importer.importFolder(uri)
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) importer.writeExport(uri)
    }
    importer.pickFiles = {
        // Plain text, spreadsheets and zips; "*/*" too, since some file managers label .txt oddly.
        files.launch(arrayOf("text/*", "application/zip", "application/x-zip-compressed", "*/*"))
    }
    importer.pickFolder = { folder.launch(null) }
    importer.onExported = onExported
    importer.saveExport = {
        export.launch("Cards-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.zip")
    }
    return importer
}

/**
 * The public edition's Import page: two clear buttons up top, then short illustrated
 * guides — how to get files onto the phone, how to write cards, and how to keep a backup.
 */
@Composable
fun ImportScreen(store: LibraryStore, settingsStore: SettingsStore, nav: Navigator, showBack: Boolean) {
    val importer = rememberImporter(store, onExported = { settingsStore.markBackedUp() })
    ScreenScaffold(title = "Import", onBack = if (showBack) nav::back else null) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TextMMD(text = "Bring your cards in", fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                TextMMD(
                    text = "Write cards on a computer, copy the files to this device, then pick them here.",
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
            }
            ImportButtons(importer)
            ImportResult(importer)

            GuideHeading("How it works")
            Step(1, "Write your cards", "Use any plain-text app — TextEdit, Notes, Word saved as plain text — or a spreadsheet saved as .csv. See “How to write cards” below.")
            Step(2, "Copy them to this device", "Connect the device to your computer with a USB cable and copy the files over, or email them to yourself and download them on the device.")
            Step(3, "Pick them here", "Tap Choose files (for .txt, .csv, or .zip) or Choose a folder. Each file becomes a deck, and folders become folders.")
            Step(4, "Update any time", "Import a file again after editing it: the deck is updated and your study progress is kept.")
            Tip(
                R.drawable.ic_info,
                "Mudita Kompakt users: turn on USB file transfer in the Kompakt's Settings first. On your computer, you may need a file-transfer app, such as Mudita Center.",
            )

            GuideHeading("How to write cards")
            TextMMD(
                text = "Save each deck as its own plain-text file. The file's name becomes the deck's name.",
                fontSize = 16.sp,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(14.dp))
            FormatGuide()

            GuideHeading("Keep a backup")
            TextMMD(
                text = "Save every deck as one .zip in its folders. Import that .zip on any device to bring everything back.",
                fontSize = 16.sp,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(14.dp))
            importer.exportNote?.let {
                TextMMD(text = it, fontSize = 14.sp, color = MutedText)
                Spacer(Modifier.height(10.dp))
            }
            PrimaryButton("Export all cards", importer::exportAll, enabled = !importer.busy)
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Choose files (filled) and Choose a folder, plus "Import … again" for the last folder. */
@Composable
fun ImportButtons(importer: Importer) {
    PrimaryButton("Choose files", importer::chooseFiles, enabled = !importer.busy)
    Spacer(Modifier.height(6.dp))
    TextMMD(
        text = ".txt  ·  .csv  ·  .zip",
        fontSize = 14.sp,
        color = MutedText,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    SecondaryButton("Choose a folder", importer::chooseFolder, enabled = !importer.busy)
    val again = importer.lastFolder
    val againName = importer.lastFolderName
    if (again != null && againName != null) {
        Spacer(Modifier.height(10.dp))
        SecondaryButton("Import “$againName” again", { importer.importFolder(again) }, enabled = !importer.busy)
    }
}

/** What the last import did, in a quiet outlined box. */
@Composable
fun ImportResult(importer: Importer) {
    val report = importer.report
    if (!importer.busy && report == null) return
    Spacer(Modifier.height(18.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.5.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (importer.busy || report == null) {
            TextMMD(text = "Importing…", fontSize = 17.sp)
            return@Column
        }
        val ok = report.cardsTotal > 0 || report.decksRemoved > 0
        TextMMD(text = if (ok) "Imported ✓" else "Nothing imported", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        TextMMD(
            text = if (ok) report.summary else "No deck files were found. Use .txt, .csv, or a .zip of them.",
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        importer.time?.let {
            TextMMD(text = "at " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)), fontSize = 13.sp, color = MutedText)
        }
        if (report.warnings.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            report.warnings.take(2).forEach { TextMMD(text = it, fontSize = 13.sp, color = MutedText, textAlign = TextAlign.Center) }
            val more = report.warnings.size - 2
            if (more > 0) TextMMD(text = "…and $more more skipped lines.", fontSize = 13.sp, color = MutedText)
        }
    }
}

/** The four ways to write cards, each as a small card with an example. */
@Composable
fun FormatGuide() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GuideCard(
            title = "One line per card",
            sample = "Photosynthesis :: Turning light into food\n- Mitosis :: Cell division\nOsmosis :: Water crossing a membrane",
            caption = "Put :: between the front and the back, all on one line. Bullets (“- ”) are fine.",
        )
        GuideCard(
            title = "Longer cards",
            sample = "Q: What are the three\nbranches of government?\nA: Legislative, executive,\nand judicial.",
            caption = "Start the front with Q: and the back with A:. Leave a blank line between cards.",
        )
        GuideCard(
            title = "From a spreadsheet",
            sample = "Front,Back\ngato,cat\nperro,dog",
            caption = "Save as .csv — column A is the front, column B the back.",
        )
        GuideCard(
            title = "From Anki",
            sample = "File › Export…\nNotes in Plain Text (.txt)\n✓ Include deck name\n✓ Include notetype name",
            caption = "Then import the .txt here. Anki decks and sub-decks become decks and folders, and each cloze blank becomes its own card. Images and sounds are left out.",
        )
        GuideCard(
            title = "Folders",
            sample = "Science/\n  Biology/\n    Cells.txt\nSpanish.csv",
            caption = "Folders on your computer become folders in Cards. Lines starting with # are ignored, so headings are fine.",
        )
    }
}

@Composable
private fun GuideHeading(text: String) {
    Spacer(Modifier.height(36.dp))
    TextMMD(text = text, fontSize = 21.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(14.dp))
}

/** A numbered step: a black circle with the number, then a bold title and a line or two. */
@Composable
private fun Step(number: Int, title: String, body: String) {
    Row(Modifier.padding(bottom = 16.dp)) {
        Box(
            Modifier
                .size(30.dp)
                .background(MaterialTheme.colorScheme.onSurface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            TextMMD(text = number.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.surface)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            TextMMD(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            TextMMD(text = body, fontSize = 15.sp, lineHeight = 21.sp)
        }
    }
}

/** A side note with an icon, in a light outlined box. */
@Composable
private fun Tip(@DrawableRes icon: Int, text: String) {
    val ink = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                // A dotted outline, like the dotted dividers elsewhere.
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = ink,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(10.dp.toPx()),
                    style = Stroke(
                        width = stroke,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, 4.5.dp.toPx()), 0f),
                    ),
                )
            }
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        TextMMD(text = text, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
    }
}

/** One format: a title, the example in a grey code box, and a short caption. */
@Composable
private fun GuideCard(title: String, sample: String, caption: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.5.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        TextMMD(text = title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .background(CodeBackground, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            TextMMD(text = sample, fontSize = 14.sp, lineHeight = 20.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(10.dp))
        TextMMD(text = caption, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

private val CodeBackground: Color
    @Composable
    get() = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF262626) else Color(0xFFEFEFEF)
