package com.devbehindyou.atomicfilemanager.ui.startup

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing

/** A failed settings read cannot safely open editable app content with defaults. */
@Composable
internal fun SettingsStartupScreen(
    loading: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    AtomicTheme(darkTheme = isSystemInDarkTheme(), wallpaperAccent = false) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Atomic.colors.background)
                    .padding(AtomicSpacing.s24)
                    .testTag("settings_startup_screen"),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s16, Alignment.CenterVertically),
        ) {
            AtomicText(
                stringResource(if (loading) R.string.settings_startup_loading else R.string.settings_startup_failed),
                AtomicTextRole.DisplayTitle,
            )
            if (!loading) {
                AtomicText(stringResource(R.string.settings_startup_failed_detail), AtomicTextRole.Body)
                AtomicButton(
                    stringResource(R.string.action_retry),
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().testTag("settings_startup_retry"),
                )
            }
            AtomicButton(
                stringResource(R.string.action_close),
                onClick = onClose,
                variant = AtomicButtonVariant.Ghost,
                modifier = Modifier.fillMaxWidth().testTag("settings_startup_close"),
            )
        }
    }
}
