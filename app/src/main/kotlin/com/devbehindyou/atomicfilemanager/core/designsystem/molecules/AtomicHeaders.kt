package com.devbehindyou.atomicfilemanager.core.designsystem.molecules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicDivider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons

/**
 * Top-level header (spec §9.6): a mono label over a Display title, actions on the right, and a
 * full-width 1 dp rule. Example: "Atomic" over "File manager".
 */
@Composable
fun AtomicHomeHeader(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = AtomicSize.headerContent)
                    .padding(horizontal = AtomicSpacing.screenMargin, vertical = AtomicSpacing.s10),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                if (eyebrow.isNotEmpty()) AtomicText(eyebrow, AtomicTextRole.MonoMeta)
                AtomicText(title, AtomicTextRole.DisplayPushed)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s4), content = actions)
        }
    }
}

/**
 * Header for pushed screens: the ink back square, then either a Display [title] (app-authored)
 * or a mono [eyebrow] such as "Internal storage" when the title row below shows a folder name.
 */
@Composable
fun AtomicPushedHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    eyebrow: String? = null,
    backDescription: String = "Back",
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = AtomicSize.headerContent)
                    .padding(
                        start = AtomicSpacing.s10,
                        end = AtomicSpacing.screenMargin,
                        top = AtomicSpacing.s4,
                        bottom = AtomicSpacing.s4,
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s6),
        ) {
            AtomicIconButton(
                AtomicIcons.Back,
                backDescription,
                onClick = onBack,
                variant = AtomicIconButtonVariant.Back,
            )
            Row(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                when {
                    title != null -> AtomicText(title, AtomicTextRole.DisplayPushed, maxLines = 2)
                    eyebrow != null -> AtomicText(eyebrow, AtomicTextRole.MonoLabel, maxLines = 1)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s4), content = actions)
        }
    }
}

/**
 * Title row (spec §5.3): title on the left, mono counter on the right, 1 dp rule under both.
 * Use [AtomicTextRole.NameLarge] for folder names so they keep their case.
 */
@Composable
fun AtomicTitleRow(
    title: String,
    modifier: Modifier = Modifier,
    counter: String? = null,
    titleRole: AtomicTextRole = AtomicTextRole.DisplayTitle,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
        ) {
            AtomicText(title, titleRole, modifier = Modifier.weight(1f).semantics { heading() }, maxLines = 2)
            if (counter != null) AtomicText(counter, AtomicTextRole.MonoLabel, maxLines = 1)
        }
        AtomicDivider(strong = true)
    }
}

/** Section label (spec §10.3): mono label, optional text action on the right, 1 dp rule. */
@Composable
fun AtomicSectionLabel(
    label: String,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = AtomicSpacing.s32),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AtomicText(label, AtomicTextRole.DisplayCard, modifier = Modifier.weight(1f).semantics { heading() })
            if (actionLabel != null && onAction != null) {
                AtomicButton(actionLabel, onClick = onAction, variant = AtomicButtonVariant.Text)
            } else if (trailingText != null) {
                AtomicText(trailingText, AtomicTextRole.MonoMeta)
            }
        }
    }
}

/** Mono path breadcrumb, original case, horizontally scrollable; the last segment is current. */
@Composable
fun AtomicBreadcrumb(
    segments: List<String>,
    onSegmentClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Atomic.colors
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        segments.forEachIndexed { index, segment ->
            val last = index == segments.lastIndex
            if (index > 0) AtomicText("/", AtomicTextRole.MonoMeta)
            AtomicText(
                text = segment,
                role = AtomicTextRole.MonoMeta,
                color = if (last) colors.content else colors.contentSecondary,
                maxLines = 1,
                modifier =
                    if (last) {
                        Modifier
                    } else {
                        Modifier
                            .heightIn(min = AtomicSize.touchTarget)
                            .clickable(role = Role.Button) { onSegmentClick(index) }
                            .padding(vertical = AtomicSpacing.s12)
                    },
            )
        }
    }
}
