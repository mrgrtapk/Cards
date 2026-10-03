package app.kompakt.flashcards.ui

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kompakt.flashcards.BuildConfig
import app.kompakt.flashcards.R
import app.kompakt.flashcards.core.IncomingFile
import app.kompakt.flashcards.data.NavTab
import com.mudita.mmd.components.text.TextMMD

/** Raise this when the welcome tips change, so everyone sees them again after updating. */
const val WELCOME_VERSION = 2

/**
 * Public edition, first launch: the app icon, a short welcome and the handful of things
 * that aren't obvious from the screen — long-press and double-tap, the ▷ Study & Practice menu, the two
 * ways of studying, writing and importing cards.
 */
@Composable
fun WelcomeDialog(mentionExamples: Boolean = true, onDone: () -> Unit) {
    DialogFrame(onDismiss = onDone) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(64.dp)
                    .border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_cards_outline),
                    contentDescription = "Cards",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            TextMMD(text = "Welcome to Cards", fontSize = 23.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            TextMMD(
                text = "Flashcards for calm, focused study." + if (mentionExamples) " A few example decks are on Home to try." else "",
                fontSize = 15.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(18.dp))
        Column(
            Modifier
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            WelcomeTip(
                R.drawable.ic_touch,
                "Long-press and double-tap",
                "Long-press a folder or deck to rename, move, sort, export, or delete it. " +
                    "Double-tap one to jump straight into its cards in full screen, in Practice or Study (choose which in Settings).",
            )
            WelcomeTip(R.drawable.ic_play, "The ▷ Study & Practice menu", "in the top navigation starts studying or practicing just that page's contents.")
            WelcomeTip(R.drawable.ic_list, "Study or Practice?", "Study shows cards when they're due, so you remember them longer. Practice runs through cards any time without changing that schedule.")
            WelcomeTip(R.drawable.ic_add, "Tap +", "to write a card, deck, or folder right on your device.")
            WelcomeTip(
                NavTab.SYNC.icon(),
                NavTab.SYNC.title,
                if (BuildConfig.SYNC_ENABLED) "brings in decks you write on a computer, over Wi‑Fi. The Sync page shows how."
                else "brings in decks you write on a computer. The Import page shows how.",
            )
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Get started", onDone)
    }
}

@Composable
private fun WelcomeTip(@DrawableRes icon: Int, lead: String, rest: String) {
    Row(Modifier.padding(bottom = 14.dp)) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            TextMMD(text = lead, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            TextMMD(text = rest, fontSize = 15.sp, lineHeight = 20.sp)
        }
    }
}

/** The example decks bundled with the public edition (assets/examples, with their folders). */
fun readExampleDecks(context: Context): List<IncomingFile> {
    val assets = context.assets
    val out = mutableListOf<IncomingFile>()
    fun walk(dir: String, prefix: String) {
        for (name in assets.list(dir).orEmpty()) {
            val path = "$dir/$name"
            val children = assets.list(path).orEmpty()
            if (children.isNotEmpty()) {
                walk(path, "$prefix$name/")
            } else {
                out += IncomingFile(prefix + name, assets.open(path).bufferedReader().use { it.readText() })
            }
        }
    }
    walk("examples", "")
    return out
}
