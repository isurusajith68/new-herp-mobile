package com.example.mobile_app_herp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.mobile_app_herp.ui.theme.HerpType

/** The gutter every screen shares. One number, so nothing drifts. */
val Gutter = 20.dp

/** Cards get a small radius — enough to not read as a broadsheet rule grid. */
val CardShape = RoundedCornerShape(10.dp)

/**
 * The masthead every screen opens with: a small uppercase label saying where you
 * are, the title, and a short brass rule. Repeating it verbatim on all five
 * screens is the point — you always know which room you're standing in.
 */
@Composable
fun ScreenHeader(
    eyebrow: String,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    eyebrow.uppercase(),
                    style = HerpType.Eyebrow,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    title,
                    style = HerpType.Display,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
        Spacer(Modifier.height(10.dp))
        BrassRule()
        if (subtitle != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A short accent rule — a masthead device, not a divider. */
@Composable
fun BrassRule(width: Int = 44) {
    Box(
        Modifier
            .width(width.dp)
            .height(3.dp)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
    )
}

/**
 * A card with a coloured spine down its left edge — the signature of this app.
 * The spine is readable at arm's length, which is how these screens actually get
 * used: phone on a shelf, glanced at between lifting boxes.
 */
@Composable
fun SpineCard(
    spine: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(spine)
            )
            Box(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 14.dp)) {
                content()
            }
        }
    }
}

/**
 * Pull-to-refresh, in one place.
 *
 * Wrapped rather than used directly at each call site: the opt-in annotation and
 * the indicator colours would otherwise be repeated on every screen, and a
 * refresh gesture that looks different from page to page reads as a bug.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Refreshable(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = modifier,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = refreshing,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) { content() }
}

/** A status set like something stamped on a docket. */
@Composable
fun Stamp(label: String, tint: Color) {
    Text(
        label.uppercase(),
        style = HerpType.Stamp,
        color = tint,
    )
}

/**
 * A brass-edged plate holding initials — the key tag. Used for a person, so an
 * assignee is recognisable before the name is read.
 */
@Composable
fun KeyTag(name: String, tint: Color = MaterialTheme.colorScheme.primary, size: Int = 30) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.Transparent,
        modifier = Modifier.size(size.dp).border(1.dp, tint, RoundedCornerShape(6.dp)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(initialsOf(name), style = HerpType.Stamp, color = tint)
        }
    }
}

/** "Nuwan Perera" → "NP"; single names give one letter, blanks give a dash. */
fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when (parts.size) {
        0 -> "–"
        1 -> parts[0].take(1).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}

/**
 * The last four characters of a ULID, as a spoken reference. Staff say "do 7ZPJ"
 * across a store room, so the id has to be short enough to say and stable enough
 * to trust — the tail of a ULID is its random component, which is exactly that.
 */
fun ticketId(id: String): String = "#" + id.takeLast(4).uppercase()

