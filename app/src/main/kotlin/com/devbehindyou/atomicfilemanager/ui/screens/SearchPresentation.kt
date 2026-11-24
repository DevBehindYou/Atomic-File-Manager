package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicDivider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicBorder
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicTypography
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.domain.model.SearchFilter
import com.devbehindyou.atomicfilemanager.domain.model.SearchIndexState
import com.devbehindyou.atomicfilemanager.ui.components.AtomicTopDialog
import com.devbehindyou.atomicfilemanager.ui.components.BalancedPopupHeader

private val searchFieldShape = RoundedCornerShape(14.dp)
private val primarySearchFilters =
    listOf(SearchFilter.ALL, SearchFilter.IMAGES, SearchFilter.DOCUMENTS, SearchFilter.FOLDERS)
private val moreSearchFilters = SearchFilter.entries.filterNot(primarySearchFilters::contains)

@Composable
internal fun SearchNameField(
    value: String,
    onValueChange: (String) -> Unit,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val colors = Atomic.colors
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(searchFieldShape)
                .background(colors.surfaceCard)
                .border(AtomicBorder.hair, if (focused) colors.accentText else colors.borderHair, searchFieldShape)
                .padding(start = AtomicSpacing.s16),
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AtomicIcons.Search, null, Modifier.size(AtomicSize.iconSmall), tint = colors.contentSecondary)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier =
                Modifier.weight(1f)
                    .focusRequester(focus)
                    .semantics { contentDescription = "Search file names" }
                    .testTag("search_field"),
            singleLine = true,
            textStyle = AtomicTypography.body.copy(color = colors.content),
            cursorBrush = SolidColor(colors.accentText),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            interactionSource = interactions,
            decorationBox = { textField ->
                Box(
                    Modifier.heightIn(min = AtomicSize.touchTarget).padding(vertical = AtomicSpacing.s12),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        AtomicText("Search file names", AtomicTextRole.BodySecondary, maxLines = 1)
                    }
                    textField()
                }
            },
        )
        AtomicIconButton(
            icon = AtomicIcons.Close,
            contentDescription = "Clear search",
            enabled = value.isNotEmpty(),
            onClick = { onValueChange("") },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SearchControls(
    filter: SearchFilter,
    onFilter: (SearchFilter) -> Unit,
    onMoreTypes: () -> Unit,
    onIndexStatus: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8)) {
        FlowRow(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
        ) {
            primarySearchFilters.forEach { option ->
                SearchTypeChip(option, filter == option) { onFilter(option) }
            }
            AtomicButton(
                text = if (filter in moreSearchFilters) "More types · ${filter.title}" else "More types",
                onClick = {
                    focusManager.clearFocus()
                    onMoreTypes()
                },
                variant = AtomicButtonVariant.Text,
                modifier = Modifier.testTag("search_more_types"),
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AtomicText(
                "File names · accessible storage",
                AtomicTextRole.BodySecondary,
                modifier = Modifier.weight(1f),
            )
            AtomicButton(
                "Index status",
                onClick = {
                    focusManager.clearFocus()
                    onIndexStatus()
                },
                variant = AtomicButtonVariant.Text,
            )
        }
    }
}

@Composable
private fun SearchTypeChip(
    option: SearchFilter,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = Atomic.colors
    val label =
        when (option) {
            SearchFilter.ALL -> "All types"
            SearchFilter.DOCUMENTS -> "Documents"
            else -> option.title
        }
    Box(
        modifier =
            Modifier
                .heightIn(min = AtomicSize.touchTarget)
                .clip(AtomicShape.pill)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                .padding(vertical = AtomicSpacing.s4),
        contentAlignment = Alignment.Center,
    ) {
        AtomicText(
            label,
            AtomicTextRole.Name,
            Modifier
                .background(if (selected) colors.accent else colors.surfaceInset, AtomicShape.pill)
                .padding(horizontal = AtomicSpacing.s12, vertical = AtomicSpacing.s6),
            color = if (selected) colors.onAccent else colors.content,
            maxLines = 1,
        )
    }
}

@Composable
internal fun SearchIndexDialog(
    state: SearchIndexState,
    hasRoots: Boolean,
    onRebuild: () -> Unit,
    onDismiss: () -> Unit,
) {
    AtomicTopDialog(onDismiss, "search_index_dialog") {
        BalancedPopupHeader(
            "Search index",
            "Close index status",
            onDismiss,
            "search_index",
            buttonVariant = AtomicIconButtonVariant.Inset,
        )
        AtomicDivider(Modifier.padding(vertical = AtomicSpacing.s8))
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
        ) {
            AtomicText("Search uses file names and metadata. File contents aren’t indexed.", AtomicTextRole.Body)
            AtomicText(SearchText.status(state), AtomicTextRole.BodySecondary, Modifier.testTag("search_index_status"))
            if (state.unreadable > 0) {
                AtomicText(
                    "Folders that couldn’t be read: ${state.unreadable}",
                    AtomicTextRole.BodySecondary,
                )
            }
            if (!hasRoots) {
                AtomicText("Rebuild is available when shared storage is accessible.", AtomicTextRole.BodySecondary)
            }
            AtomicButton(
                if (state.building) "Indexing…" else "Rebuild index",
                onClick = onRebuild,
                enabled = hasRoots && !state.building,
                modifier = Modifier.testTag("search_rebuild"),
            )
        }
    }
}

@Composable
internal fun SearchMoreTypesDialog(
    filter: SearchFilter,
    onFilter: (SearchFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    AtomicTopDialog(onDismiss, "search_more_types_dialog") {
        BalancedPopupHeader(
            "More file types",
            "Close more file types",
            onDismiss,
            "search_more_types",
            buttonVariant = AtomicIconButtonVariant.Inset,
        )
        AtomicDivider(Modifier.padding(vertical = AtomicSpacing.s8))
        Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
            moreSearchFilters.forEach { option ->
                SearchTypeChip(option, filter == option) { onFilter(option) }
            }
        }
    }
}
