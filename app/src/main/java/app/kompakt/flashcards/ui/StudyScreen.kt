package app.kompakt.flashcards.ui

import androidx.activity.compose.BackHandler
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kompakt.flashcards.R
import app.kompakt.flashcards.core.Card
import app.kompakt.flashcards.core.Fsrs
import app.kompakt.flashcards.core.LibraryData
import app.kompakt.flashcards.core.Rating
import app.kompakt.flashcards.core.ReviewState
import app.kompakt.flashcards.core.counts
import app.kompakt.flashcards.core.nextDue
import app.kompakt.flashcards.core.setReview
import app.kompakt.flashcards.core.studyQueue
import app.kompakt.flashcards.core.toggleStar
import app.kompakt.flashcards.data.SettingsStore
import app.kompakt.flashcards.data.TextSize
import androidx.compose.ui.unit.sp
import app.kompakt.flashcards.data.LibraryStore
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD

/**
 * One card at a time: read the front, tap to reveal the back, then say how well you knew it.
 * Nothing animates, so the e-ink screen only refreshes when the card actually changes.
 */
@Composable
fun StudyScreen(store: LibraryStore, settingsStore: SettingsStore, route: Route.Study, nav: Navigator) {
    val data by store.data.collectAsState()
    val settings by settingsStore.settings.collectAsState()
    val fsrs = remember { Fsrs() }

    var queue by remember { mutableStateOf(buildQueue(data, route, settings.shuffle)) }
    var revealed by remember { mutableStateOf(false) }
    var studied by remember { mutableIntStateOf(0) }
    var editingCardId by remember { mutableStateOf<String?>(null) }
    var choosingCard by remember { mutableStateOf(false) }
    // Every step forward (rate, skip, jump) is remembered so "Previous card" can undo it:
    // the line of cards goes back to how it was, and a rating is taken back.
    var history by remember { mutableStateOf(listOf<Step>()) }

    fun record(step: Step) {
        history = (history + step).takeLast(200)
    }

    fun previous() {
        val step = history.lastOrNull() ?: return
        history = history.dropLast(1)
        if (step.undoReview != null) {
            store.update { it.setReview(step.cardId, step.undoReview) }
        }
        if (step.counted) studied--
        queue = step.queueBefore
        revealed = false
    }

    // Look the card up in live data so edits made elsewhere show immediately.
    val current: Card? = queue.firstOrNull()?.let { id -> data.cards.firstOrNull { it.id == id } }
    LaunchedEffect(queue, current) {
        if (queue.isNotEmpty() && current == null) queue = queue.drop(1) // card was deleted
    }

    fun rate(card: Card, rating: Rating) {
        record(
            Step(
                cardId = card.id,
                queueBefore = queue,
                undoReview = if (route.practice) null else card.review,
                counted = rating != Rating.AGAIN,
            ),
        )
        if (!route.practice) {
            val now = System.currentTimeMillis()
            store.update { it.setReview(card.id, fsrs.next(card.review, rating, now)) }
        }
        queue = if (rating == Rating.AGAIN) queue.drop(1) + card.id else queue.drop(1)
        if (rating != Rating.AGAIN) studied++
        revealed = false
    }

    // Editing opens right here, so the study session carries on where it was afterwards.
    val editing = editingCardId
    if (editing != null) {
        BackHandler { editingCardId = null }
        CardEditor(
            store = store,
            deckId = null,
            cardId = editing,
            folderHint = null,
            onClose = { editingCardId = null },
            nav = null,
        )
        return
    }

    // "Go to card": every card in this session's decks, fronts only.
    if (choosingCard) {
        BackHandler { choosingCard = false }
        val scope = route.cardIds?.mapNotNull { id -> data.cards.firstOrNull { it.id == id } }
            ?: data.cards.filter { it.deckId in route.deckIds }
        CardPicker(
            cards = scope,
            currentId = current?.id,
            onPick = { id ->
                if (id != current?.id) record(Step(cardId = id, queueBefore = queue))
                queue = listOf(id) + queue.filter { it != id }
                revealed = false
                choosingCard = false
            },
            onClose = { choosingCard = false },
        )
        return
    }

    val left = queue.distinct().size

    // Full screen (the default): only the card, with tap zones — left = previous card,
    // middle = show the answer, right = next card. The phone's own bars are hidden too.
    // A double-tap opens full screen this once, whatever the usual view is.
    var forcedFullScreen by remember { mutableStateOf(route.fullScreen) }
    val fullScreen = (settings.studyFullScreen || forcedFullScreen) && current != null
    ImmersiveMode(fullScreen)

    fun next(card: Card) {
        when {
            revealed -> rate(card, Rating.GOOD) // "Good" in Study, "Got it" in Practice
            left > 1 -> {
                record(Step(cardId = card.id, queueBefore = queue))
                queue = queue.drop(1) + card.id
            }
            else -> revealed = true // the last card: nothing to skip to, so show its answer
        }
    }

    // The first Study session, and separately the first Practice session, open with a short
    // guide to full screen and its tap zones.
    val tipsSeen = if (route.practice) settings.seenPracticeTips else settings.seenStudyTips
    if (!tipsSeen) {
        StudyTipsDialog(practice = route.practice) {
            settingsStore.update {
                if (route.practice) it.copy(seenPracticeTips = true) else it.copy(seenStudyTips = true)
            }
        }
    }

    if (fullScreen && current != null) {
        key(current.id, queue.size) {
            FullScreenCard(
                card = current,
                frontSize = settings.frontSize,
                backSize = settings.backSize,
                revealed = revealed,
                leftAlign = settings.leftAlignCards,
                actions = {
                    // Optional buttons (Settings › Customization › Full-screen options), the
                    // All three appear only once the answer shows; the question side
                    // shows just the regular-view button.
                    if (settings.fullScreenEdit && revealed) {
                        IconAction(R.drawable.ic_edit, "Edit card") { editingCardId = current.id }
                    }
                    if (settings.fullScreenStar && revealed) {
                        StarButton(current.starred) { store.update { it.toggleStar(current.id) } }
                    }
                    if (settings.fullScreenAllCards && revealed) {
                        IconAction(R.drawable.ic_list, "Go to a card") { choosingCard = true }
                    }
                },
                onMiddle = { if (!revealed) revealed = true },
                onLeft = ::previous,
                onRight = { next(current) },
                onExit = {
                    forcedFullScreen = false
                    settingsStore.update { it.copy(studyFullScreen = false) }
                },
            )
        }
        return
    }

    ScreenScaffold(
        title = if (current != null) "$left left" else route.title,
        onBack = nav::back,
        backIcon = R.drawable.ic_close,
        actions = {
            if (current != null) {
                if (revealed) {
                    IconAction(R.drawable.ic_edit, "Edit card") { editingCardId = current.id }
                }
                StarButton(current.starred) { store.update { it.toggleStar(current.id) } }
                IconAction(R.drawable.ic_list, "Go to a card") { choosingCard = true }
                IconAction(R.drawable.ic_fullscreen, "Full screen") {
                    settingsStore.update { it.copy(studyFullScreen = true) }
                }
            }
        },
    ) {
        if (current == null) {
            Finished(
                data = data,
                route = route,
                studied = studied,
                onMore = {
                    queue = buildQueue(store.data.value, route, settingsStore.settings.value.shuffle)
                    studied = 0
                    history = emptyList()
                },
                onDone = nav::back,
            )
            return@ScreenScaffold
        }

        key(current.id, queue.size) {
            CardFace(
                card = current,
                frontSize = settings.frontSize,
                backSize = settings.backSize,
                revealed = revealed,
                onReveal = { revealed = true },
                modifier = Modifier.weight(1f),
                leftAlign = settings.leftAlignCards,
            )
        }

        Column(Modifier.padding(start = ScreenPadding, end = ScreenPadding, bottom = ScreenPadding, top = 8.dp)) {
            if (!revealed) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Previous card: step back to the card before, undoing its rating.
                    SecondaryButton(
                        text = "Previous card",
                        enabled = history.isNotEmpty(),
                        icon = R.drawable.ic_back,
                        compact = true,
                        modifier = Modifier.weight(1.7f),
                        onClick = ::previous,
                    )
                    // Skip: send this card to the back of the line without rating it.
                    SecondaryButton(
                        text = "Skip",
                        enabled = left > 1,
                        compact = true,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            record(Step(cardId = current.id, queueBefore = queue))
                            queue = queue.drop(1) + current.id
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Show answer", { revealed = true })
            } else if (route.practice) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RateButton("Again", null, filled = false) { rate(current, Rating.AGAIN) }
                    RateButton("Got it", null, filled = true) { rate(current, Rating.GOOD) }
                }
            } else {
                val now = remember(current.id) { System.currentTimeMillis() }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RateButton("Again", fsrs.previewLabel(current.review, Rating.AGAIN, now), filled = false) {
                        rate(current, Rating.AGAIN)
                    }
                    RateButton("Good", fsrs.previewLabel(current.review, Rating.GOOD, now), filled = true) {
                        rate(current, Rating.GOOD)
                    }
                    RateButton("Easy", fsrs.previewLabel(current.review, Rating.EASY, now), filled = false) {
                        rate(current, Rating.EASY)
                    }
                }
            }
        }
    }
}

/**
 * The card alone, filling the screen. Taps: left 30% = previous card, right 30% = next card,
 * middle = show the answer. A long card can still be scrolled. The only control is the
 * corner button back to the regular view.
 */
@Composable
private fun FullScreenCard(
    card: Card,
    frontSize: TextSize,
    backSize: TextSize,
    revealed: Boolean,
    leftAlign: Boolean,
    actions: @Composable RowScope.() -> Unit,
    onMiddle: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onExit: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .pointerInput(card.id, revealed) {
                    detectTapGestures { offset ->
                        val w = size.width
                        when {
                            offset.x < w * 0.3f -> onLeft()
                            offset.x > w * 0.7f -> onRight()
                            else -> onMiddle()
                        }
                    }
                },
        ) {
            val minHeight = maxHeight
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = minHeight)
                    .padding(horizontal = 28.dp, vertical = 56.dp),
                horizontalAlignment = if (leftAlign) Alignment.Start else Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CardText(card, frontSize, backSize, revealed, leftAlign)
            }
        }
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions()
            IconAction(R.drawable.ic_fullscreen_exit, "Regular view", onClick = onExit)
        }
    }
}

/** Hides the phone's status and navigation bars while [on] (swipe from an edge to peek). */
@Composable
private fun ImmersiveMode(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(on) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (controller != null) {
            if (on) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The full-screen guide. [practice] null = the general version (from a deck page). */
@Composable
fun StudyTipsDialog(practice: Boolean?, onOkay: () -> Unit) {
    DialogFrame(onDismiss = onOkay) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.ic_fullscreen),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(10.dp))
            TextMMD(
                text = when (practice) {
                    true -> "Practicing in full screen"
                    false -> "Studying in full screen"
                    null -> "Full-screen tips"
                },
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
        TextMMD(
            text = "Cards open full screen, with nothing but the card in view.",
            fontSize = 15.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(14.dp))
        TipLine("Middle", "show the answer")
        TipLine("Right side", "next card")
        TipLine("Left side", "previous card")
        Spacer(Modifier.height(6.dp))
        TextMMD(
            text = when (practice) {
                true -> "Moving on after seeing the answer counts as “Got it.”"
                false -> "Moving on after seeing the answer counts as “Good.” For Again or Easy, use the regular view."
                null -> "Moving on after seeing the answer counts as “Good” in Study and “Got it” in Practice. For Again or Easy, use the regular view."
            },
            fontSize = 15.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.ic_fullscreen_exit),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            TextMMD(
                text = "The corner button switches to the regular view, with buttons, the star, editing, and the card list.",
                fontSize = 15.sp,
                lineHeight = 21.sp,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Okay", onOkay)
    }
}

/** "Middle — show the answer": a bold zone name and what tapping it does. */
@Composable
private fun TipLine(zone: String, action: String) {
    Row(Modifier.padding(bottom = 6.dp)) {
        TextMMD(text = zone, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(96.dp))
        TextMMD(text = action, fontSize = 16.sp)
    }
}

/** One step forward in a session, kept so it can be undone. */
private data class Step(
    val cardId: String,
    val queueBefore: List<String>,
    /** The card's schedule before it was rated (null when nothing was saved). */
    val undoReview: ReviewState? = null,
    /** Whether the step added to the "went through N cards" total. */
    val counted: Boolean = false,
)

private fun buildQueue(data: LibraryData, route: Route.Study, shuffle: Boolean): List<String> {
    val ids = when {
        route.cardIds != null -> route.cardIds.filter { id -> data.cards.any { it.id == id } }
        route.practice -> data.cards.filter { it.deckId in route.deckIds }.map { it.id }
        else -> return data.studyQueue(
            route.deckIds, System.currentTimeMillis(), NEW_CARDS_PER_SESSION, shuffle, filters = route.filters,
        ).map { it.id }
    }
    return if (shuffle) ids.shuffled() else ids
}

/** Card text sizes for Small / Medium / Large (Settings). */
private fun TextSize.frontSp() = when (this) {
    TextSize.SMALL -> 22.sp
    TextSize.MEDIUM -> 28.sp
    TextSize.LARGE -> 34.sp
}

private fun TextSize.backSp() = when (this) {
    TextSize.SMALL -> 17.sp
    TextSize.MEDIUM -> 21.sp
    TextSize.LARGE -> 25.sp
}

@Composable
private fun CardFace(
    card: Card,
    frontSize: TextSize,
    backSize: TextSize,
    revealed: Boolean,
    onReveal: () -> Unit,
    modifier: Modifier,
    leftAlign: Boolean = false,
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clickable(
                enabled = !revealed,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Show answer",
                onClick = onReveal,
            ),
    ) {
        val minHeight = maxHeight
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = minHeight)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = if (leftAlign) Alignment.Start else Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CardText(card, frontSize, backSize, revealed, leftAlign)
        }
    }
}

/** The front in bold, and once revealed a short rule and the back beneath it. */
@Composable
private fun CardText(card: Card, frontSize: TextSize, backSize: TextSize, revealed: Boolean, leftAlign: Boolean) {
    val align = if (leftAlign) TextAlign.Start else TextAlign.Center
    // Left-aligned text fills the width, so its first line starts at the left edge.
    val textModifier = if (leftAlign) Modifier.fillMaxWidth() else Modifier
    TextMMD(
        text = card.front,
        fontSize = frontSize.frontSp(),
        lineHeight = frontSize.frontSp() * 1.25f,
        fontWeight = FontWeight.Bold,
        textAlign = align,
        modifier = textModifier,
    )
    if (revealed) {
        Spacer(Modifier.height(28.dp))
        Box(
            Modifier
                .size(width = 56.dp, height = 3.dp)
                .background(MaterialTheme.colorScheme.onSurface),
        )
        Spacer(Modifier.height(28.dp))
        TextMMD(
            text = card.back,
            fontSize = backSize.backSp(),
            lineHeight = backSize.backSp() * 1.3f,
            textAlign = align,
            modifier = textModifier,
        )
    }
}

@Composable
private fun RowScope.RateButton(label: String, hint: String?, filled: Boolean, onClick: () -> Unit) {
    val content: @Composable RowScope.() -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TextMMD(text = label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            if (hint != null) {
                TextMMD(text = hint, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    val modifier = Modifier
        .weight(1f)
        .heightIn(min = 60.dp)
    val padding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
    if (filled) {
        ButtonMMD(onClick = onClick, modifier = modifier, contentPadding = padding, content = content)
    } else {
        OutlinedButtonMMD(border = inkBorder(), onClick = onClick, modifier = modifier, contentPadding = padding, content = content)
    }
}

@Composable
private fun Finished(data: LibraryData, route: Route.Study, studied: Int, onMore: () -> Unit, onDone: () -> Unit) {
    val now = remember { System.currentTimeMillis() }
    val remaining = remember(data) { data.counts(route.deckIds, route.filters, now) }
    val moreAvailable = !route.practice && (remaining.due > 0 || remaining.new > 0)
    val next = remember(data) { data.nextDue(route.deckIds) }

    val message = when {
        studied == 0 && route.practice -> "This deck has no cards yet."
        studied == 0 -> "Nothing is due right now."
        else -> "You went through $studied ${if (studied == 1) "card" else "cards"}."
    }
    val nextLine = if (!route.practice && !moreAvailable && next != null && next > now) {
        "Next review in ${Fsrs.formatInterval(next - now)}."
    } else ""

    EmptyState(
        title = if (studied > 0) "Well done!" else "All caught up",
        message = listOf(message, nextLine).filter { it.isNotEmpty() }.joinToString("\n"),
    ) {
        if (moreAvailable) {
            PrimaryButton("Keep going · ${studyCount(remaining)}", onMore)
            Spacer(Modifier.height(12.dp))
            SecondaryButton("Done", onDone)
        } else if (route.practice && studied > 0) {
            PrimaryButton("Practice again", onMore)
            Spacer(Modifier.height(12.dp))
            SecondaryButton("Done", onDone)
        } else {
            PrimaryButton("Done", onDone)
        }
    }
}

/** "Go to card": the fronts of every card in the session; tap one to study it next. */
@Composable
private fun CardPicker(cards: List<Card>, currentId: String?, onPick: (String) -> Unit, onClose: () -> Unit) {
    ScreenScaffold(
        title = "Go to card",
        titleSuffix = "${cards.size} ${if (cards.size == 1) "card" else "cards"}",
        onBack = onClose,
        backIcon = R.drawable.ic_close,
    ) {
        LazyColumnMMD(modifier = Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(cards, key = { _, c -> c.id }) { index, card ->
                ListRow(
                    title = card.front.replace('\n', ' '),
                    subtitle = if (card.id == currentId) "Showing now" else null,
                    bold = card.id == currentId,
                    titleSize = 17,
                    onClick = { onPick(card.id) },
                    trailing = {},
                )
                if (index < cards.lastIndex) ListDivider()
            }
        }
    }
}
