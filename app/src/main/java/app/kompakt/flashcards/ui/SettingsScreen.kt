package app.kompakt.flashcards.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kompakt.flashcards.BuildConfig
import app.kompakt.flashcards.R
import app.kompakt.flashcards.data.LibraryStore
import app.kompakt.flashcards.data.NavTab
import app.kompakt.flashcards.data.SettingsStore
import app.kompakt.flashcards.data.TextSize
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD

@Composable
fun SettingsScreen(store: LibraryStore, settingsStore: SettingsStore, nav: Navigator) {
    val settings by settingsStore.settings.collectAsState()
    val importer = rememberImporter(store, onExported = { settingsStore.markBackedUp() })

    ScreenScaffold(title = "Settings", onBack = null) {
        // One row per item, and the scroll-bar arrows move two rows at a time, so nothing is
        // skipped over unseen.
        LazyColumnMMD(modifier = Modifier.fillMaxSize(), scrollStep = 2) {
            item(key = "h-display") { SectionHeader("Display", Modifier.padding(top = 0.dp)) }
            item(key = "dark") {
                SwitchRow(
                    title = "Dark mode",
                    subtitle = null,
                    checked = settings.darkMode,
                ) { on -> settingsStore.update { it.copy(darkMode = on) } }
            }
            item(key = "h-text") { SectionHeader("Card text") }
            item(key = "front") {
                SizeChoice("Front", settings.frontSize) { size -> settingsStore.update { it.copy(frontSize = size) } }
            }
            item(key = "back") {
                SizeChoice("Back", settings.backSize) { size -> settingsStore.update { it.copy(backSize = size) } }
            }
            item(key = "align") {
                Column {
                    TextMMD(
                        text = "Alignment",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = ScreenPadding, end = ScreenPadding, top = 10.dp, bottom = 8.dp),
                    )
                    Choice(
                        options = listOf(false to "Centered", true to "Left"),
                        selected = settings.leftAlignCards,
                    ) { left -> settingsStore.update { it.copy(leftAlignCards = left) } }
                }
            }
            item(key = "h-double-tap") { SectionHeader("Double-tap") }
            item(key = "double-tap") {
                Column {
                    TextMMD(
                        text = "Double-tap a folder or deck to open its cards in full screen, in:",
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(start = ScreenPadding, end = ScreenPadding, top = 4.dp, bottom = 10.dp),
                    )
                    Choice(
                        options = listOf(true to "Practice", false to "Study"),
                        selected = settings.doubleTapPractice,
                    ) { practice -> settingsStore.update { it.copy(doubleTapPractice = practice) } }
                }
            }
            item(key = "h-study") { SectionHeader("Shuffle option") }
            item(key = "shuffle") {
                SwitchRow(
                    title = "Shuffle cards",
                    subtitle = "Mix up the order when studying a deck, a folder, or Starred.",
                    checked = settings.shuffle,
                ) { on -> settingsStore.update { it.copy(shuffle = on) } }
            }
            item(key = "show-shuffle") {
                SwitchRow(
                    title = "Show Shuffle button",
                    subtitle = null,
                    checked = settings.showShuffleButton,
                ) { on -> settingsStore.update { it.copy(showShuffleButton = on) } }
            }
            item(key = "h-buttons") { SectionHeader("Customization") }
            item(key = "customize") {
                Box(Modifier.fillMaxWidth().padding(start = ScreenPadding - 12.dp, end = ScreenPadding, top = 2.dp, bottom = 2.dp)) {
                    SeeMoreLink("Customize further", bold = false) { nav.go(Route.Customize) }
                }
            }
            item(key = "h-nav") { SectionHeader("Navigation bar") }
            item(key = "labels") {
                SwitchRow(
                    title = "Show labels",
                    subtitle = null,
                    checked = settings.showNavLabels,
                ) { on -> settingsStore.update { it.copy(showNavLabels = on) } }
            }
            item(key = "nav-help") {
                TextMMD(
                    text = "Use the arrows to reorder. Switch items off to hide them. Home and Settings always stay.",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = ScreenPadding, end = ScreenPadding, top = 4.dp, bottom = 8.dp),
                )
            }
            itemsIndexed(settings.tabOrder, key = { _, tab -> "tab" + tab.name }) { index, tab ->
                NavTabRow(
                    tab = tab,
                    visible = tab !in settings.hiddenTabs,
                    canMoveUp = index > 0,
                    canMoveDown = index < settings.tabOrder.lastIndex,
                    onMove = { delta ->
                        settingsStore.update { s ->
                            val order = s.tabOrder.toMutableList()
                            val target = index + delta
                            if (target in order.indices) {
                                order[index] = order[target].also { order[target] = order[index] }
                            }
                            s.copy(tabOrder = order)
                        }
                    },
                    onVisibleChange = { show ->
                        settingsStore.update { s ->
                            s.copy(hiddenTabs = if (show) s.hiddenTabs - tab else s.hiddenTabs + tab)
                        }
                    },
                )
                if (index < settings.tabOrder.lastIndex) DottedDivider()
            }
            item(key = "h-backup") { SectionHeader("Backup") }
            item(key = "backup-note") {
                val last = settings.lastBackupAt
                TextMMD(
                    text = if (last > 0) {
                        "Last backup: " + java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(last)) + "."
                    } else {
                        "Save every deck, in its folders, as one .zip. Import it on any device to bring everything back."
                    },
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(start = ScreenPadding, end = ScreenPadding, top = 4.dp, bottom = 10.dp),
                )
            }
            item(key = "backup-button") {
                Column(Modifier.padding(horizontal = ScreenPadding)) {
                    importer.exportNote?.let {
                        TextMMD(text = it, fontSize = 14.sp, color = MutedText)
                        Spacer(Modifier.height(8.dp))
                    }
                    PrimaryButton("Export all cards", importer::exportAll, enabled = !importer.busy)
                }
            }
            item(key = "backup-reminder") {
                SwitchRow(
                    title = "Backup reminder",
                    subtitle = null,
                    checked = settings.backupReminder,
                ) { on -> settingsStore.update { it.copy(backupReminder = on) } }
            }
            if (settings.backupReminder) {
                item(key = "backup-often") {
                    Choice(
                        options = listOf(14 to "Biweekly", 30 to "Monthly"),
                        selected = settings.backupEveryDays,
                    ) { days -> settingsStore.update { it.copy(backupEveryDays = days) } }
                }
            }
            item(key = "h-about") { SectionHeader("About") }
            item(key = "about") {
                Row(
                    Modifier.padding(start = ScreenPadding, end = ScreenPadding, top = 6.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_cards_outline),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(22.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    TextMMD(
                        text = "Cards — flashcards for calm, focused study. Built with Mudita Mindful Design.",
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item(key = "welcome-again") {
                Box(Modifier.fillMaxWidth().padding(start = ScreenPadding - 12.dp, bottom = 24.dp)) {
                    SeeMoreLink("Show welcome tips", bold = false) { settingsStore.update { it.copy(seenWelcome = false) } }
                }
            }
        }
    }
}

/** "Front: Small | Medium | Large" — the selected size is a filled MMD button. */
@Composable
private fun SizeChoice(label: String, selected: TextSize, onSelect: (TextSize) -> Unit) {
    Column(Modifier.padding(horizontal = ScreenPadding, vertical = 10.dp)) {
        TextMMD(text = label, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextSize.entries.forEach { size ->
                val modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                val padding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                if (size == selected) {
                    ButtonMMD(onClick = { onSelect(size) }, modifier = modifier, contentPadding = padding) {
                        TextMMD(text = size.label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButtonMMD(border = inkBorder(), onClick = { onSelect(size) }, modifier = modifier, contentPadding = padding) {
                        TextMMD(text = size.label, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            TextMMD(text = title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                TextMMD(text = subtitle, fontSize = 14.sp)
            }
        }
        SmallSwitch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun NavTabRow(
    tab: NavTab,
    visible: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onVisibleChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = ScreenPadding, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(tab.icon()),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        TextMMD(
            text = tab.title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (canMoveUp) {
            IconAction(R.drawable.ic_up, "Move ${tab.title} up", boxSize = 36.dp, iconSize = 20.dp) { onMove(-1) }
        } else {
            Spacer(Modifier.width(36.dp))
        }
        if (canMoveDown) {
            IconAction(R.drawable.ic_down, "Move ${tab.title} down", boxSize = 36.dp, iconSize = 20.dp) { onMove(1) }
        } else {
            Spacer(Modifier.width(36.dp))
        }
        Spacer(Modifier.width(4.dp))
        SmallSwitch(
            checked = visible,
            onCheckedChange = if (tab.hideable) onVisibleChange else null,
            enabled = tab.hideable,
        )
    }
}

/** Settings › Customize further: the Customization page — what shows on each page. */
@Composable
fun CustomizeScreen(settingsStore: SettingsStore, nav: Navigator) {
    val settings by settingsStore.settings.collectAsState()
    ScreenScaffold(title = "Customization", subtitle = "Settings", onBack = nav::back) {
        LazyColumnMMD(modifier = Modifier.fillMaxSize(), scrollStep = 2) {
            item(key = "h-menu") {
                IconSectionHeader("Study & Practice options") {
                    Icon(
                        painter = painterResource(R.drawable.ic_play_filled),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            item(key = "show-play") {
                SwitchRow(
                    title = "Show ▷ button",
                    subtitle = null,
                    checked = settings.showPlayButton,
                ) { on -> settingsStore.update { it.copy(showPlayButton = on) } }
            }
            item(key = "play-home") {
                SwitchRow(
                    title = "Show ▷ button in Home",
                    subtitle = null,
                    checked = settings.showPlayOnHome,
                ) { on -> settingsStore.update { it.copy(showPlayOnHome = on) } }
            }
            item(key = "menu-study") {
                SwitchRow(
                    title = "Show “Study” option in the ▷ menu",
                    subtitle = null,
                    checked = settings.menuStudy,
                ) { on -> settingsStore.update { it.copy(menuStudy = on) } }
            }
            item(key = "menu-practice") {
                SwitchRow(
                    title = "Show “Practice” option in the ▷ menu",
                    subtitle = null,
                    checked = settings.menuPractice,
                ) { on -> settingsStore.update { it.copy(menuPractice = on) } }
            }
            item(key = "tips-button") {
                SwitchRow(
                    title = "Show full-screen tips button",
                    subtitle = null,
                    checked = settings.showTipsButton,
                ) { on -> settingsStore.update { it.copy(showTipsButton = on) } }
            }
            item(key = "h-full-screen") {
                IconSectionHeader("Full-screen options") {
                    Icon(
                        painter = painterResource(R.drawable.ic_fullscreen),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            item(key = "fs-note") {
                TextMMD(
                    text = "These buttons appear once the answer shows.",
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = MutedText,
                    modifier = Modifier.padding(start = ScreenPadding, end = ScreenPadding, top = 2.dp, bottom = 4.dp),
                )
            }
            item(key = "fs-edit") {
                SwitchRow(
                    title = "Show Edit button",
                    subtitle = null,
                    checked = settings.fullScreenEdit,
                ) { on -> settingsStore.update { it.copy(fullScreenEdit = on) } }
            }
            item(key = "fs-star") {
                SwitchRow(
                    title = "Show Star button",
                    subtitle = null,
                    checked = settings.fullScreenStar,
                ) { on -> settingsStore.update { it.copy(fullScreenStar = on) } }
            }
            item(key = "fs-all-cards") {
                SwitchRow(
                    title = "Show All cards button",
                    subtitle = null,
                    checked = settings.fullScreenAllCards,
                ) { on -> settingsStore.update { it.copy(fullScreenAllCards = on) } }
            }
            item(key = "h-counts") { IconSectionHeader("Counts options") { NumberBadge() } }
            item(key = "show-cards") {
                SwitchRow(
                    title = "Show card counts",
                    subtitle = null,
                    checked = settings.showCardCount,
                ) { on -> settingsStore.update { it.copy(showCardCount = on) } }
            }
            item(key = "show-decks") {
                SwitchRow(
                    title = "Show deck counts",
                    subtitle = null,
                    checked = settings.showDeckCount,
                ) { on -> settingsStore.update { it.copy(showDeckCount = on) } }
            }
            item(key = "show-new") {
                SwitchRow(
                    title = "Show new-card counts",
                    subtitle = null,
                    checked = settings.showNewCount,
                ) { on -> settingsStore.update { it.copy(showNewCount = on) } }
            }
            item(key = "show-due") {
                SwitchRow(
                    title = "Show due-card counts",
                    subtitle = null,
                    checked = settings.showDueCount,
                ) { on -> settingsStore.update { it.copy(showDueCount = on) } }
            }
            item(key = "end") { Spacer(Modifier.height(32.dp)) }
        }
    }
}

/** A section heading with a small picture before it. */
@Composable
private fun IconSectionHeader(text: String, top: androidx.compose.ui.unit.Dp = 24.dp, icon: @Composable () -> Unit) {
    Row(
        Modifier.padding(start = ScreenPadding, end = ScreenPadding, top = top, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A fixed-width slot, so the heading text lines up whatever the picture's width.
        Box(Modifier.width(34.dp), contentAlignment = Alignment.CenterStart) { icon() }
        Spacer(Modifier.width(6.dp))
        TextMMD(text = text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

/** A little filled "123" tile, standing in for numbers. */
@Composable
private fun NumberBadge() {
    Box(
        Modifier
            .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(5.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        TextMMD(text = "123", fontSize = 12.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.surface)
    }
}

/** Two or three side-by-side choices; the chosen one is filled (like the text sizes). */
@Composable
private fun <T> Choice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier.padding(start = ScreenPadding, end = ScreenPadding, top = 2.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            val modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
            if (value == selected) {
                ButtonMMD(onClick = { onSelect(value) }, modifier = modifier, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    TextMMD(text = label, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            } else {
                OutlinedButtonMMD(border = inkBorder(), onClick = { onSelect(value) }, modifier = modifier, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    TextMMD(text = label, fontSize = 16.sp, maxLines = 1)
                }
            }
        }
    }
}
