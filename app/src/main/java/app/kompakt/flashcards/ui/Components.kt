package app.kompakt.flashcards.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.kompakt.flashcards.BuildConfig
import app.kompakt.flashcards.R
import app.kompakt.flashcards.core.PracticeFilter
import app.kompakt.flashcards.data.AppSettings
import com.mudita.mmd.components.chips.FilterChipMMD
import app.kompakt.flashcards.data.NavTab
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.menus.DropdownMenuItemMMD
import com.mudita.mmd.components.menus.DropdownMenuMMD
import com.mudita.mmd.components.nav_bar.NavigationBarItemMMD
import com.mudita.mmd.components.nav_bar.NavigationBarMMD
import androidx.compose.ui.draw.scale
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD

/** Side margin used throughout (same as CalmCast). */
val ScreenPadding = 16.dp

/** Grey used for secondary numbers (counts, the "http://" prefix). */
val MutedText = Color(0xFF777777)

/** Light grey (dark grey in dark mode) for buttons that can't be used right now. */
val DisabledInk: Color
    @Composable @ReadOnlyComposable
    get() = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF555555) else Color(0xFFAAAAAA)

/**
 * A full screen: MMD top app bar above the content, styled like CalmCast — a 24sp bold
 * title on main screens, an 18sp bold title with a small subtitle on detail screens,
 * and a 3dp rule under the bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    subtitle: String? = null,
    /** Small text shown on the same line, right after the title (e.g. "All cards · 5"). */
    titleSuffix: String? = null,
    @DrawableRes backIcon: Int = R.drawable.ic_back,
    /** A small picture before the title (the Cards icon on Home). */
    @DrawableRes titleIcon: Int? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBarMMD(
            title = {
                Column {
                    // One line each, ending in "…" when too long.
                    // Title and suffix share one baseline, so they read as a single line.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (titleIcon != null) {
                            Icon(
                                painter = painterResource(titleIcon),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(if (onBack == null) 26.dp else 20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        TextMMD(
                            text = title,
                            fontSize = if (onBack == null) 24.sp else 18.sp,
                            lineHeight = if (onBack == null) 28.sp else 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .alignByBaseline(),
                        )
                        if (!titleSuffix.isNullOrEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            TextMMD(
                                text = titleSuffix,
                                fontSize = if (onBack == null) 20.sp else 16.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.alignByBaseline(),
                            )
                        }
                    }
                    if (!subtitle.isNullOrEmpty()) {
                        Spacer(Modifier.height(3.dp))
                        TextMMD(
                            text = subtitle,
                            fontSize = 14.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            // One fixed height for every screen, with or without a second line.
            expandedHeight = 72.dp,
            navigationIcon = {
                if (onBack != null) {
                    IconAction(backIcon, if (backIcon == R.drawable.ic_close) "Close" else "Back", onClick = onBack)
                }
            },
            actions = actions,
            showDivider = false,
        )
        HorizontalDividerMMD(thickness = 3.dp)
        content()
    }
}

/**
 * A 48dp touch target with a 24dp icon. When [selected] the icon is shown white on a
 * black rounded square — used for on/off actions such as shuffle.
 */
@Composable
fun IconAction(
    @DrawableRes icon: Int,
    description: String,
    selected: Boolean = false,
    boxSize: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(boxSize)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(iconSize + 12.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                    RoundedCornerShape(8.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = description,
                tint = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/** A full-width button with a dotted outline, for lighter, secondary actions. */
@Composable
fun DottedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val color = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .drawBehind {
                val stroke = 2.dp.toPx()
                val dot = 2.dp.toPx()
                drawRoundRect(
                    color = if (enabled) color else color.copy(alpha = 0.5f),
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(
                        width = stroke,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, dot + 3.dp.toPx()), 0f),
                    ),
                )
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        TextMMD(text = text, fontSize = 18.sp, textAlign = TextAlign.Center)
    }
}

/** Roomy, even padding for every item in the ⋮ and ▷ menus. */
val MenuItemPadding = PaddingValues(start = 28.dp, end = 24.dp)

/** "⋮" button that opens an MMD dropdown menu. */
@Composable
fun OverflowMenu(items: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconAction(R.drawable.ic_more, "More options") { open = true }
        DropdownMenuMMD(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { (label, action) ->
                DropdownMenuItemMMD(
                    text = { TextMMD(text = label, fontSize = 18.sp) },
                    onClick = {
                        open = false
                        action()
                    },
                    contentPadding = MenuItemPadding,
                )
            }
        }
    }
}

/**
 * A dotted rule (Mudita's separator guide allows black solid or dotted lines; dotted is for
 * soft grouping like list items). Inset from the left like CalmCast's list dividers.
 */
@Composable
fun DottedDivider(modifier: Modifier = Modifier.padding(start = ScreenPadding)) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier
            .fillMaxWidth()
            .height(2.dp),
    ) {
        val dot = 1.5.dp.toPx()
        val gap = 3.dp.toPx()
        drawLine(
            color = color,
            start = Offset(dot / 2, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = dot,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, dot + gap), 0f),
        )
    }
}

/** A thin solid line between rows of folders, decks, and cards. */
@Composable
fun ListDivider() {
    HorizontalDividerMMD(thickness = 1.dp, modifier = Modifier.padding(start = ScreenPadding))
}

/** One row in a list (CalmCast layout): optional icon, 20sp title, 14sp subtitle, trailing slot. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    @DrawableRes icon: Int? = null,
    bold: Boolean = true,
    titleSize: Int = 20,
    titleMaxLines: Int = 2,
    subtitleMaxLines: Int = 2,
    /** An extra small line under the subtitle (e.g. where a card lives). */
    detail: String? = null,
    onLongClick: (() -> Unit)? = null,
    /** Double-tap action (e.g. open a folder's or deck's cards straight away). */
    onDoubleClick: (() -> Unit)? = null,
    /**
     * A tappable 48dp control in place of [icon] (e.g. a star). The row starts closer to the
     * edge so its 24dp glyph lines up exactly with the icons on other rows.
     */
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit = { Chevron() },
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onDoubleClick = onDoubleClick)
            .padding(
                start = if (leading != null) ScreenPadding - 12.dp else ScreenPadding,
                end = 8.dp,
                top = 16.dp,
                bottom = 16.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            // 48dp control − 12dp overhang on each side + 14dp gap = same text start as icon rows.
            Spacer(Modifier.width(2.dp))
        } else if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(14.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .padding(end = 8.dp),
        ) {
            TextMMD(
                text = title,
                fontSize = titleSize.sp,
                lineHeight = (titleSize + 4).sp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                TextMMD(
                    text = subtitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = subtitleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!detail.isNullOrEmpty()) {
                Spacer(Modifier.height(2.dp))
                TextMMD(
                    text = detail,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
    }
}

@Composable
fun Chevron() {
    Icon(
        painter = painterResource(R.drawable.ic_chevron),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(28.dp),
    )
}

/** Star toggle used on card rows and in the study/edit top bars. */
@Composable
fun StarButton(starred: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(role = Role.Button, onClickLabel = if (starred) "Unstar" else "Star", onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (starred) R.drawable.ic_star_filled else R.drawable.ic_star),
            contentDescription = if (starred) "Starred" else "Not starred",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** The 2dp outline for outlined buttons, in the page's ink color (Mudita's default is always black). */
@Composable
fun inkBorder(enabled: Boolean = true) = BorderStroke(2.dp, if (enabled) MaterialTheme.colorScheme.onSurface else DisabledInk)

/** Mudita's switch drawn at 80% in a 44×28dp box, so it sits neatly at the end of a row. */
@Composable
fun SmallSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean = true) {
    Box(Modifier.size(width = 44.dp, height = 28.dp), contentAlignment = Alignment.Center) {
        SwitchMMD(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.scale(0.8f),
        )
    }
}

/** A square check box for picking rows in select mode; the 48dp box is the touch target. */
@Composable
fun SelectBox(selected: Boolean, onToggle: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    val paper = MaterialTheme.colorScheme.surface
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(role = Role.Checkbox, onClickLabel = if (selected) "Deselect" else "Select", onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .background(if (selected) ink else paper, RoundedCornerShape(4.dp))
                .border(2.dp, ink, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = paper,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** A word in the top bar that works like a button (e.g. "Select all"). */
@Composable
fun TextAction(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        TextMMD(text = text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

/** A centered bold link with a chevron, e.g. "See all cards ›". */
@Composable
fun SeeMoreLink(text: String, bold: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextMMD(
            text = text,
            fontSize = if (bold) 17.sp else 18.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            // Wrap onto a second line on narrow screens, keeping the chevron in view.
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(
            painterResource(R.drawable.ic_chevron),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** A 20sp bold section heading, used above lists inside a screen. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    TextMMD(
        text = text,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = ScreenPadding, end = ScreenPadding, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    ButtonMMD(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        // Mudita's default only fades a disabled button to 75%, which barely shows on e-ink.
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = DisabledInk,
            disabledContentColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        ButtonIcon(icon)
        TextMMD(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.surface,
        )
    }
}

/** Optional small icon before a button's label; takes the button's text colour. */
@Composable
private fun ButtonIcon(@DrawableRes icon: Int?) {
    if (icon == null) return
    Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(10.dp))
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
    /** Tighter side padding, so a longer label fits on one line beside another button. */
    compact: Boolean = false,
) {
    OutlinedButtonMMD(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        contentPadding = if (compact) PaddingValues(horizontal = 10.dp, vertical = 8.dp) else ButtonDefaults.ContentPadding,
        // Greyed clearly when it can't be used (e.g. "Previous card" on the first card).
        border = BorderStroke(2.dp, if (enabled) MaterialTheme.colorScheme.onSurface else DisabledInk),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = DisabledInk,
        ),
    ) {
        ButtonIcon(icon)
        TextMMD(
            text = text,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else DisabledInk,
            maxLines = if (compact) 1 else Int.MAX_VALUE,
            softWrap = !compact,
        )
    }
}

/** Centered message for empty screens. */
@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier, action: @Composable ColumnScope.() -> Unit = {}) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TextMMD(text = title, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        TextMMD(text = message, fontSize = 16.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        action()
    }
}

// ---------------------------------------------------------------- bottom navigation

/** Outline icon normally; the filled version when the tab is selected. */
@DrawableRes
fun NavTab.icon(selected: Boolean = false): Int = when (this) {
    NavTab.LIBRARY -> if (selected) R.drawable.ic_deck_filled else R.drawable.ic_deck
    NavTab.STARRED -> if (selected) R.drawable.ic_star_filled else R.drawable.ic_star
    NavTab.NEW -> R.drawable.ic_add
    NavTab.SEARCH -> if (selected) R.drawable.ic_search_filled else R.drawable.ic_search
    NavTab.SYNC -> when {
        !BuildConfig.SYNC_ENABLED -> if (selected) R.drawable.ic_import_filled else R.drawable.ic_import
        selected -> R.drawable.ic_sync_filled
        else -> R.drawable.ic_sync
    }
    NavTab.SETTINGS -> if (selected) R.drawable.ic_settings_filled else R.drawable.ic_settings
}

/** A tab's name as shown: the public edition has "Import" where the personal one has "Sync". */
val NavTab.title: String
    get() = if (this == NavTab.SYNC && !BuildConfig.SYNC_ENABLED) "Import" else label

/** Turns black into white and white into black (for black-and-white icons in dark mode). */
private val InvertColors = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

/** Mudita's bottom navigation strip, styled like CalmCast's (bold label on the selected tab). */
@Composable
fun BottomNav(tabs: List<NavTab>, current: NavTab, showLabels: Boolean, onSelect: (NavTab) -> Unit) {
    NavigationBarMMD {
        tabs.forEach { tab ->
            val selected = tab == current
            NavigationBarItemMMD(
                selected = selected,
                onClick = { onSelect(tab) },
                icon = {
                    // The icons carry their own black and white, so filled ones keep their
                    // cut-outs; in dark mode they're inverted to match.
                    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                    Image(
                        painter = painterResource(tab.icon(selected)),
                        contentDescription = tab.title,
                        colorFilter = if (dark) InvertColors else null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = if (showLabels) {
                    {
                        TextMMD(
                            text = tab.title,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    null
                },
                alwaysShowLabel = showLabels,
            )
        }
    }
}

// ---------------------------------------------------------------- dialogs

/** White card with a black outline: an e-ink friendly dialog surface. */
@Composable
fun DialogFrame(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    // Our own width (not the platform's), so there's clear space from the screen edges.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(12.dp))
                .padding(horizontal = 22.dp, vertical = 20.dp),
            content = content,
        )
    }
}

@Composable
fun DialogButtons(confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, confirmEnabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButtonMMD(border = inkBorder(), onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            TextMMD(text = "Cancel", fontSize = 17.sp)
        }
        ButtonMMD(onClick = onConfirm, enabled = confirmEnabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            TextMMD(text = confirm, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    validate: (String) -> String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    fun submit() {
        val e = validate(value.text.trim())
        if (e == null) onConfirm(value.text.trim()) else error = e
    }
    DialogFrame(onDismiss) {
        TextMMD(text = title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        TextFieldMMD(
            value = value,
            onValueChange = { value = it; error = null },
            singleLine = true,
            isError = error != null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            TextMMD(text = error!!, fontSize = 14.sp)
        }
        Spacer(Modifier.height(24.dp))
        DialogButtons(confirmLabel, ::submit, onDismiss, confirmEnabled = value.text.isNotBlank())
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    DialogFrame(onDismiss) {
        TextMMD(text = title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        TextMMD(text = message, fontSize = 16.sp)
        Spacer(Modifier.height(24.dp))
        DialogButtons(confirmLabel, onConfirm, onDismiss)
    }
}

/** A short list of actions, used for long-press menus and the "New" button. */
@Composable
fun ActionsDialog(title: String, actions: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    DialogFrame(onDismiss) {
        TextMMD(text = title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        actions.forEachIndexed { index, (label, action) ->
            if (index > 0) DottedDivider(Modifier)
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { action() }
                    .padding(start = 6.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                TextMMD(text = label, fontSize = 18.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButtonMMD(border = inkBorder(), onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
            TextMMD(text = "Cancel", fontSize = 17.sp)
        }
    }
}

/**
 * "Practicing All cards ▾" — tap to choose what Practice goes through, in a menu styled like
 * the ▷ menu: All, Due, New, Starred with grey counts and a check on what's chosen. Due, New
 * and Starred can be combined; the menu stays open so the counts can be watched.
 */
@Composable
fun PracticeFilterPicker(
    /** The grey words before the choice: "Studying or practicing", or just "Practicing". */
    prefix: String,
    filters: Set<PracticeFilter>,
    count: (Set<PracticeFilter>) -> Int,
    onChange: (Set<PracticeFilter>) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val label = if (filters.isEmpty()) {
        "All cards"
    } else {
        PracticeFilter.entries.filter { it in filters }.joinToString(" + ") { it.label }
    }
    Box {
        Row(
            Modifier
                .clickable(role = Role.Button, onClickLabel = "Choose what to practice") { open = true }
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextMMD(text = prefix, fontSize = 15.sp, color = MutedText)
            Spacer(Modifier.width(6.dp))
            TextMMD(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline,
                maxLines = 1,
            )
            Icon(
                painter = painterResource(R.drawable.ic_caret_down),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenuMMD(expanded = open, onDismissRequest = { open = false }) {
            PlayMenuItem("All", count = count(emptySet()), checked = filters.isEmpty()) { onChange(emptySet()) }
            PracticeFilter.entries.forEach { f ->
                PlayMenuItem(f.label, count = count(setOf(f)), checked = f in filters) {
                    onChange(if (f in filters) filters - f else filters + f)
                }
            }
        }
    }
}

/**
 * The ▷ menu on folder pages: Study, Practice (with All / Due / New / Starred) and Shuffle,
 * styled like the ⋮ menu — text only, counts in grey, a check mark on what's selected.
 * Choosing a filter or Shuffle keeps the menu open so the Practice count can be watched.
 */
@Composable
fun PlayMenu(
    settings: AppSettings,
    studyCount: Int,
    practiceCount: (Set<PracticeFilter>) -> Int,
    /** Study's count for a card choice (used when Practice is hidden from the menu). */
    studyCountFor: (Set<PracticeFilter>) -> Int,
    onStudy: () -> Unit,
    onPractice: (Set<PracticeFilter>) -> Unit,
    onFiltersChange: (Set<PracticeFilter>) -> Unit,
    onToggleShuffle: () -> Unit,
) {
    val showStudy = settings.menuStudy
    val showPractice = settings.menuPractice
    val showShuffle = settings.showShuffleButton
    if (!showStudy && !showPractice && !showShuffle) return
    var open by remember { mutableStateOf(false) }
    val filters = settings.practiceFilters
    Box {
        IconAction(R.drawable.ic_play, "Study or practice") { open = true }
        DropdownMenuMMD(expanded = open, onDismissRequest = { open = false }) {
            // Study and Practice first (each with ▷), then Shuffle, then what to practice.
            if (showStudy) {
                PlayMenuItem("Study", count = studyCount, bold = true, enabled = studyCount > 0, mark = R.drawable.ic_play_filled) {
                    open = false
                    onStudy()
                }
            }
            val n = if (showPractice) practiceCount(filters) else 0
            if (showPractice) {
                PlayMenuItem("Practice", count = n, bold = true, enabled = n > 0, mark = R.drawable.ic_play_filled) {
                    open = false
                    onPractice(filters)
                }
            }
            if (showShuffle) {
                PlayMenuItem("Shuffle", count = null, checked = settings.shuffle, bold = true, mark = R.drawable.ic_shuffle) {
                    onToggleShuffle()
                }
            }
            if (showPractice || showStudy) {
                // With Practice shown, counts are Practice's; with only Study, they're Study's.
                val countFor: (Set<PracticeFilter>) -> Int = if (showPractice) practiceCount else studyCountFor
                // Even space above and below the dotted line.
                DottedDivider(Modifier.padding(start = 28.dp, end = 24.dp, top = 6.dp, bottom = 6.dp))
                TextMMD(
                    text = when {
                        showStudy && showPractice -> "Study or practice which cards"
                        showStudy -> "Study which cards"
                        else -> "Practice which cards"
                    },
                    fontSize = 14.sp,
                    color = MutedText,
                    modifier = Modifier.padding(start = 28.dp, end = 24.dp, top = 13.dp, bottom = 2.dp),
                )
                PlayMenuItem("All", count = countFor(emptySet()), checked = filters.isEmpty()) {
                    onFiltersChange(emptySet())
                }
                PracticeFilter.entries.forEach { f ->
                    PlayMenuItem(f.label, count = countFor(setOf(f)), checked = f in filters) {
                        onFiltersChange(if (f in filters) filters - f else filters + f)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayMenuItem(
    label: String,
    count: Int?,
    checked: Boolean = false,
    bold: Boolean = false,
    enabled: Boolean = true,
    /** A small icon before the label: ▷ for Study and Practice, the shuffle arrows for Shuffle. */
    @DrawableRes mark: Int? = null,
    onClick: () -> Unit,
) {
    DropdownMenuItemMMD(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (mark != null) {
                    Icon(
                        painter = painterResource(mark),
                        contentDescription = null,
                        // Greyed with the label when there's nothing to practice.
                        tint = if (enabled) MaterialTheme.colorScheme.onSurface else MutedText,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                TextMMD(
                    text = label,
                    fontSize = 18.sp,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                )
                if (count != null) {
                    Spacer(Modifier.width(10.dp))
                    TextMMD(text = count.toString(), fontSize = 16.sp, color = MutedText, maxLines = 1, softWrap = false)
                }
            }
        },
        trailingIcon = {
            Box(Modifier.size(22.dp)) {
                if (checked) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        },
        enabled = enabled,
        onClick = onClick,
        contentPadding = MenuItemPadding,
    )
}

/** A compact chip that never wraps: filled black when selected, outlined when not. */
@Composable
fun SmallChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    val paper = MaterialTheme.colorScheme.surface
    Box(
        modifier
            .heightIn(min = 34.dp)
            .background(if (selected) ink else paper, RoundedCornerShape(17.dp))
            .border(1.5.dp, ink, RoundedCornerShape(17.dp))
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        TextMMD(
            text = label,
            fontSize = 14.sp,
            color = if (selected) paper else ink,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}
