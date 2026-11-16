package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons

@Composable
internal fun AnalysisCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val inset = if (LocalConfiguration.current.screenWidthDp < 760) AtomicSpacing.screenMargin else AtomicSpacing.s24
    Column(
        modifier.fillMaxWidth().background(Atomic.colors.surfaceCard, AtomicShape.lg).padding(inset),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s16),
        content = content,
    )
}

@Composable
internal fun AnalysisHeading(title: String) {
    AtomicText(title, AtomicTextRole.DisplayCard, Modifier.semantics { heading() })
}

@Composable
internal fun AnalysisCoverageDisclosure() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val state = if (expanded) "Expanded" else "Collapsed"
    Column(
        Modifier.fillMaxWidth().background(Atomic.colors.surfaceCard, AtomicShape.lg)
            .padding(horizontal = AtomicSpacing.s16),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = AtomicSize.touchTarget)
                .clickable(role = Role.Button, onClick = { expanded = !expanded })
                .semantics { stateDescription = state },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
        ) {
            AtomicText("What’s included?", AtomicTextRole.Name, Modifier.weight(1f))
            Icon(
                AtomicIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp).rotate(if (expanded) 270f else 90f),
                tint = Atomic.colors.accentText,
            )
        }
        if (expanded) {
            AtomicText(
                "File types cover accessible shared files, including readable hidden files, across mounted storage. " +
                    "Percentages are based on readable bytes. App-private and inaccessible data are excluded. " +
                    "Cleanup suggestions cover the storage location opened for analysis.",
                AtomicTextRole.BodySecondary,
                Modifier.padding(bottom = AtomicSpacing.s16),
            )
        }
    }
}
