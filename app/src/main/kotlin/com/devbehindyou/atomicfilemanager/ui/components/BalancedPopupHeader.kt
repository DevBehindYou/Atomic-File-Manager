package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons

/** Equal control slots keep an anchored popup's title centered, including pages without Back. */
@Composable
internal fun BalancedPopupHeader(
    title: String,
    closeDescription: String,
    onClose: () -> Unit,
    testTagPrefix: String,
    onBack: (() -> Unit)? = null,
    backDescription: String = "Back",
    subtitle: String? = null,
    buttonVariant: AtomicIconButtonVariant = AtomicIconButtonVariant.Ghost,
    controlsGap: Dp = 0.dp,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(controlsGap),
    ) {
        Box(
            Modifier.size(AtomicSize.touchTarget).testTag("${testTagPrefix}_leading"),
            contentAlignment = Alignment.Center,
        ) {
            if (onBack != null) AtomicIconButton(AtomicIcons.Back, backDescription, onBack, variant = buttonVariant)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            AtomicText(
                title,
                AtomicTextRole.DisplayCard,
                modifier = Modifier.testTag("${testTagPrefix}_heading"),
                color = Atomic.colors.content,
                textAlign = TextAlign.Center,
            )
            if (subtitle != null) AtomicText(subtitle, AtomicTextRole.MonoMeta, textAlign = TextAlign.Center)
        }
        Box(
            Modifier.size(AtomicSize.touchTarget).testTag("${testTagPrefix}_trailing"),
            contentAlignment = Alignment.Center,
        ) {
            AtomicIconButton(AtomicIcons.Close, closeDescription, onClose, variant = buttonVariant)
        }
    }
}
