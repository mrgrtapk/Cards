package app.kompakt.flashcards.data

import android.content.Context
import app.kompakt.flashcards.core.PracticeFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class TextSize(val label: String) { SMALL("Small"), MEDIUM("Medium"), LARGE("Large") }

/** Items in the bottom navigation strip. Home and Settings can't be hidden. */
enum class NavTab(val label: String, val hideable: Boolean) {
    LIBRARY("Home", false),
    STARRED("Starred", true),
    NEW("New", true),
    SEARCH("Search", true),
    SYNC("Sync", true),
    SETTINGS("Settings", false),
}

data class AppSettings(
    val frontSize: TextSize = TextSize.MEDIUM,
    val backSize: TextSize = TextSize.MEDIUM,
    val shuffle: Boolean = true,
    val tabOrder: List<NavTab> = listOf(
        NavTab.STARRED, NavTab.NEW, NavTab.LIBRARY, NavTab.SEARCH, NavTab.SYNC, NavTab.SETTINGS,
    ),
    // Five items fit comfortably on the Kompakt; Sync is also reachable from the Home screen.
    val hiddenTabs: Set<NavTab> = setOf(NavTab.SYNC),
    val showNavLabels: Boolean = true,
    /** White on black instead of black on white. */
    val darkMode: Boolean = false,
    /** Folders arranged by hand rather than A–Z ("" = Home). */
    val manualFolders: Set<String> = emptySet(),
    val showShuffleButton: Boolean = true,
    /** The ▷ menu in the top bar of folder pages. */
    val showPlayButton: Boolean = true,
    /** The ▷ menu on the Home page too. */
    val showPlayOnHome: Boolean = true,
    /** "Study" inside the ▷ menu. */
    val menuStudy: Boolean = true,
    /** "Practice" (and its All / Due / New / Starred choices) inside the ▷ menu. */
    val menuPractice: Boolean = true,
    /** The full-screen tips button in a deck page's top bar. */
    val showTipsButton: Boolean = true,
    /** "N cards" in folder and deck lists. */
    val showCardCount: Boolean = true,
    /** "N decks" in folder lists. */
    val showDeckCount: Boolean = true,
    /** Study with only the card on screen (tap zones instead of buttons). */
    val studyFullScreen: Boolean = true,
    /** The full-screen how-to has been shown once. */
    val seenStudyTips: Boolean = false,
    /** The welcome pop-up has been shown. */
    val seenWelcome: Boolean = false,
    /** Which version of the welcome tips was last shown (a newer one shows again after an update). */
    val welcomeVersionSeen: Int = 0,
    /** Double-tapping a folder or deck opens its cards full screen in Practice (true) or Study (false). */
    val doubleTapPractice: Boolean = true,
    /** Card text and the rule between question and answer: left-aligned instead of centered. */
    val leftAlignCards: Boolean = false,
    /** Display size: 100 = the device's normal size; 115 / 130 make everything in Cards larger. */
    val displayScale: Int = 100,
    /** Full screen: also show the Edit (after the answer), Star, and All cards buttons. */
    val fullScreenEdit: Boolean = false,
    val fullScreenStar: Boolean = false,
    val fullScreenAllCards: Boolean = false,
    /** Public edition: the example decks have been added (once, on first launch). */
    val seededExamples: Boolean = false,
    /** The full-screen how-to has been shown before a first Practice session. */
    val seenPracticeTips: Boolean = false,
    /** The "Study or Practice?" note on a deck page has been shown. */
    val seenModesTips: Boolean = false,
    /** A gentle prompt to export a backup every [backupEveryDays] days. */
    val backupReminder: Boolean = true,
    /** 14 (every two weeks) or 30 (monthly). */
    val backupEveryDays: Int = 30,
    /** When "Export all cards" last saved a backup (0 = never). */
    val lastBackupAt: Long = 0,
    /** When the backup reminder was last shown (or first counted from). */
    val backupPromptAt: Long = 0,
    /** Show the "N new" counts in folder/deck lists and on the deck page. */
    val showNewCount: Boolean = true,
    /** Show the "N due" counts in folder/deck lists and on the deck page. */
    val showDueCount: Boolean = true,
    /** Practice chips: empty = All. */
    val practiceFilters: Set<PracticeFilter> = emptySet(),
) {
    val visibleTabs: List<NavTab> get() = tabOrder.filter { it !in hiddenTabs }

    fun isManual(folderId: String?): Boolean = (folderId ?: "") in manualFolders

    fun withManual(folderId: String?, manual: Boolean): AppSettings {
        val key = folderId ?: ""
        return copy(manualFolders = if (manual) manualFolders + key else manualFolders - key)
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings

    /** Remembers that a full backup was just saved, so the reminder can wait another month. */
    fun markBackedUp() = update { it.copy(lastBackupAt = System.currentTimeMillis()) }

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit()
            .putString(KEY_FRONT, next.frontSize.name)
            .putString(KEY_BACK, next.backSize.name)
            .putBoolean(KEY_SHUFFLE, next.shuffle)
            .putString(KEY_ORDER, next.tabOrder.joinToString(",") { it.name })
            .putString(KEY_HIDDEN, next.hiddenTabs.joinToString(",") { it.name })
            .putBoolean(KEY_LABELS, next.showNavLabels)
            .putBoolean(KEY_DARK, next.darkMode)
            .putStringSet(KEY_MANUAL, next.manualFolders)
            .putBoolean(KEY_SHOW_SHUFFLE, next.showShuffleButton)
            .putBoolean(KEY_PLAY, next.showPlayButton)
            .putBoolean(KEY_PLAY_HOME, next.showPlayOnHome)
            .putBoolean(KEY_MENU_STUDY, next.menuStudy)
            .putBoolean(KEY_MENU_PRACTICE, next.menuPractice)
            .putBoolean(KEY_TIPS_BUTTON, next.showTipsButton)
            .putBoolean(KEY_SHOW_CARDS, next.showCardCount)
            .putBoolean(KEY_SHOW_DECKS, next.showDeckCount)
            .putBoolean(KEY_FULL_SCREEN, next.studyFullScreen)
            .putBoolean(KEY_SEEN_TIPS, next.seenStudyTips)
            .putBoolean(KEY_SEEN_WELCOME, next.seenWelcome)
            .putInt(KEY_WELCOME_VERSION, next.welcomeVersionSeen)
            .putBoolean(KEY_DOUBLE_TAP_PRACTICE, next.doubleTapPractice)
            .putBoolean(KEY_LEFT_ALIGN, next.leftAlignCards)
            .putInt(KEY_DISPLAY_SCALE, next.displayScale)
            .putBoolean(KEY_FS_EDIT, next.fullScreenEdit)
            .putBoolean(KEY_FS_STAR, next.fullScreenStar)
            .putBoolean(KEY_FS_ALL_CARDS, next.fullScreenAllCards)
            .putBoolean(KEY_SEEDED, next.seededExamples)
            .putBoolean(KEY_SEEN_PRACTICE_TIPS, next.seenPracticeTips)
            .putBoolean(KEY_SEEN_MODES, next.seenModesTips)
            .putBoolean(KEY_BACKUP_REMINDER, next.backupReminder)
            .putInt(KEY_BACKUP_DAYS, next.backupEveryDays)
            .putLong(KEY_LAST_BACKUP, next.lastBackupAt)
            .putLong(KEY_BACKUP_PROMPT, next.backupPromptAt)
            .putBoolean(KEY_SHOW_NEW, next.showNewCount)
            .putBoolean(KEY_SHOW_DUE, next.showDueCount)
            .putInt(KEY_DEFAULTS, DEFAULTS_VERSION)
            .putStringSet(KEY_PRACTICE_FILTERS, next.practiceFilters.map { it.name }.toSet())
            .apply()
    }

    private fun load(): AppSettings {
        val defaults = AppSettings()
        fun size(key: String, fallback: TextSize) =
            runCatching { TextSize.valueOf(prefs.getString(key, null) ?: fallback.name) }.getOrDefault(fallback)
        fun tabs(csv: String?) = csv.orEmpty().split(',').mapNotNull { name ->
            NavTab.entries.firstOrNull { it.name == name }
        }
        val saved = tabs(prefs.getString(KEY_ORDER, null)).distinct()
        // A fresh install starts with Home in the middle; saved orders keep any tabs added later.
        val order = if (saved.isEmpty()) defaults.tabOrder else saved + NavTab.entries.filter { it !in saved }
        val hidden = if (prefs.contains(KEY_HIDDEN)) {
            tabs(prefs.getString(KEY_HIDDEN, null)).filter { it.hideable }.toSet()
        } else {
            defaults.hiddenTabs
        }
        return AppSettings(
            frontSize = size(KEY_FRONT, defaults.frontSize),
            backSize = size(KEY_BACK, defaults.backSize),
            // Shuffle became on-by-default in defaults version 2; switch it on once for earlier installs.
            shuffle = if (prefs.getInt(KEY_DEFAULTS, 1) < 2) true else prefs.getBoolean(KEY_SHUFFLE, defaults.shuffle),
            tabOrder = order,
            hiddenTabs = hidden,
            showNavLabels = prefs.getBoolean(KEY_LABELS, defaults.showNavLabels),
            darkMode = prefs.getBoolean(KEY_DARK, defaults.darkMode),
            manualFolders = prefs.getStringSet(KEY_MANUAL, emptySet())?.toSet() ?: emptySet(),
            showShuffleButton = prefs.getBoolean(KEY_SHOW_SHUFFLE, defaults.showShuffleButton),
            showPlayButton = prefs.getBoolean(KEY_PLAY, defaults.showPlayButton),
            showPlayOnHome = prefs.getBoolean(KEY_PLAY_HOME, defaults.showPlayOnHome),
            menuStudy = prefs.getBoolean(KEY_MENU_STUDY, defaults.menuStudy),
            menuPractice = prefs.getBoolean(KEY_MENU_PRACTICE, defaults.menuPractice),
            showTipsButton = prefs.getBoolean(KEY_TIPS_BUTTON, defaults.showTipsButton),
            showCardCount = prefs.getBoolean(KEY_SHOW_CARDS, defaults.showCardCount),
            showDeckCount = prefs.getBoolean(KEY_SHOW_DECKS, defaults.showDeckCount),
            studyFullScreen = prefs.getBoolean(KEY_FULL_SCREEN, defaults.studyFullScreen),
            seenStudyTips = prefs.getBoolean(KEY_SEEN_TIPS, defaults.seenStudyTips),
            seenWelcome = prefs.getBoolean(KEY_SEEN_WELCOME, defaults.seenWelcome),
            welcomeVersionSeen = prefs.getInt(KEY_WELCOME_VERSION, defaults.welcomeVersionSeen),
            doubleTapPractice = prefs.getBoolean(KEY_DOUBLE_TAP_PRACTICE, defaults.doubleTapPractice),
            leftAlignCards = prefs.getBoolean(KEY_LEFT_ALIGN, defaults.leftAlignCards),
            displayScale = prefs.getInt(KEY_DISPLAY_SCALE, defaults.displayScale),
            fullScreenEdit = prefs.getBoolean(KEY_FS_EDIT, defaults.fullScreenEdit),
            fullScreenStar = prefs.getBoolean(KEY_FS_STAR, defaults.fullScreenStar),
            fullScreenAllCards = prefs.getBoolean(KEY_FS_ALL_CARDS, defaults.fullScreenAllCards),
            seededExamples = prefs.getBoolean(KEY_SEEDED, defaults.seededExamples),
            seenPracticeTips = prefs.getBoolean(KEY_SEEN_PRACTICE_TIPS, defaults.seenPracticeTips),
            seenModesTips = prefs.getBoolean(KEY_SEEN_MODES, defaults.seenModesTips),
            backupReminder = prefs.getBoolean(KEY_BACKUP_REMINDER, defaults.backupReminder),
            backupEveryDays = prefs.getInt(KEY_BACKUP_DAYS, defaults.backupEveryDays),
            lastBackupAt = prefs.getLong(KEY_LAST_BACKUP, defaults.lastBackupAt),
            backupPromptAt = prefs.getLong(KEY_BACKUP_PROMPT, defaults.backupPromptAt),
            showNewCount = prefs.getBoolean(KEY_SHOW_NEW, defaults.showNewCount),
            showDueCount = prefs.getBoolean(KEY_SHOW_DUE, defaults.showDueCount),
            practiceFilters = prefs.getStringSet(KEY_PRACTICE_FILTERS, emptySet()).orEmpty()
                .mapNotNull { n -> PracticeFilter.entries.firstOrNull { it.name == n } }.toSet(),
        )
    }

    private companion object {
        const val KEY_FRONT = "frontSize"
        const val KEY_BACK = "backSize"
        const val KEY_SHUFFLE = "shuffle"
        const val KEY_ORDER = "tabOrder"
        const val KEY_HIDDEN = "hiddenTabs"
        const val KEY_LABELS = "showNavLabels"
        const val KEY_DARK = "darkMode"
        const val KEY_MANUAL = "manualFolders"
        const val KEY_SHOW_SHUFFLE = "showShuffleButton"
        const val KEY_PLAY = "showPlayButton"
        const val KEY_PLAY_HOME = "showPlayOnHome"
        const val KEY_MENU_STUDY = "menuStudy"
        const val KEY_MENU_PRACTICE = "menuPractice"
        const val KEY_TIPS_BUTTON = "showTipsButton"
        const val KEY_SHOW_CARDS = "showCardCount"
        const val KEY_SHOW_DECKS = "showDeckCount"
        const val KEY_FULL_SCREEN = "studyFullScreen"
        const val KEY_SEEN_TIPS = "seenStudyTips"
        const val KEY_SEEN_WELCOME = "seenWelcome"
        const val KEY_WELCOME_VERSION = "welcomeVersionSeen"
        const val KEY_DOUBLE_TAP_PRACTICE = "doubleTapPractice"
        const val KEY_LEFT_ALIGN = "leftAlignCards"
        const val KEY_DISPLAY_SCALE = "displayScale"
        const val KEY_FS_EDIT = "fullScreenEdit"
        const val KEY_FS_STAR = "fullScreenStar"
        const val KEY_FS_ALL_CARDS = "fullScreenAllCards"
        const val KEY_SEEDED = "seededExamples"
        const val KEY_SEEN_PRACTICE_TIPS = "seenPracticeTips"
        const val KEY_SEEN_MODES = "seenModesTips"
        const val KEY_BACKUP_REMINDER = "backupReminder"
        const val KEY_BACKUP_DAYS = "backupEveryDays"
        const val KEY_LAST_BACKUP = "lastBackupAt"
        const val KEY_BACKUP_PROMPT = "backupPromptAt"
        const val KEY_SHOW_NEW = "showNewCount"
        const val KEY_SHOW_DUE = "showDueCount"
        const val KEY_DEFAULTS = "defaultsVersion"
        const val DEFAULTS_VERSION = 2
        const val KEY_PRACTICE_FILTERS = "practiceFilters"
    }
}
