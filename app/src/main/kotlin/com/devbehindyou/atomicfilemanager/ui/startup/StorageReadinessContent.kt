package com.devbehindyou.atomicfilemanager.ui.startup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicWarningBox

/** Initial loading/failure never masquerades as a denied permission or an empty storage list. */
@Composable
fun StorageReadinessContent(
    state: StorageReadinessState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state is StorageReadinessState.Ready) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12)) {
        if (state is StorageReadinessState.Loading) {
            AtomicText(
                stringResource(
                    if (state.snapshot == null) {
                        R.string.storage_readiness_loading
                    } else {
                        R.string.storage_readiness_refreshing
                    },
                ),
                AtomicTextRole.BodySecondary,
                modifier = Modifier.testTag("storage_readiness_loading"),
            )
        } else {
            AtomicWarningBox(
                title =
                    stringResource(
                        if (state.snapshot == null) {
                            R.string.storage_readiness_failed
                        } else {
                            R.string.storage_readiness_refresh_failed
                        },
                    ),
                body = stringResource(R.string.storage_readiness_failed_detail),
                modifier = Modifier.testTag("storage_readiness_failed"),
            )
            AtomicButton(
                stringResource(R.string.action_retry),
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().testTag("storage_readiness_retry"),
            )
        }
    }
}
