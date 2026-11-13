package com.devbehindyou.atomicfilemanager.core.designsystem.atoms

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.core.designsystem.preview.AtomicAtomsCatalog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Behaviour of the Atomic atoms on Robolectric. Visual checks are the previews and the device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AtomicAtomsComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun displayLabelsRemainAccessibleAndFileNamesKeepTheirCase() {
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                Column {
                    AtomicText("Storage", AtomicTextRole.DisplayTitle)
                    AtomicText("README.md", AtomicTextRole.Name)
                }
            }
        }
        compose.onNodeWithContentDescription("Storage").assertExists()
        compose.onNodeWithText("README.md").assertExists()
    }

    @Test
    fun buttonsClickOnlyWhenEnabled() {
        var clicks = 0
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                Column {
                    AtomicButton("Create folder", onClick = { clicks++ })
                    AtomicButton("Delete forever", onClick = { clicks++ }, enabled = false)
                }
            }
        }
        compose.onNodeWithContentDescription("Create folder").performClick()
        compose.onNodeWithContentDescription("Delete forever").assertIsNotEnabled()
        assertEquals(1, clicks)
    }

    @Test
    fun chipAndCheckboxToggle() {
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                var hidden by remember { mutableStateOf(false) }
                var picked by remember { mutableStateOf(false) }
                Column {
                    AtomicChip("Hidden", selected = hidden, onSelectedChange = { hidden = it })
                    AtomicCheckbox(checked = picked, onCheckedChange = { picked = it })
                }
            }
        }
        val chip = compose.onNodeWithContentDescription("Hidden")
        chip.assertIsOff()
        chip.performClick()
        chip.assertIsOn()
    }

    @Test
    fun textFieldShowsItsErrorAndMeterReportsProgress() {
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                Column {
                    AtomicTextField(
                        value = "Tickets/2026",
                        onValueChange = {},
                        label = "Name",
                        errorText = "A folder name can't contain a slash.",
                    )
                    AtomicMeter(fraction = 0.32f, contentDescription = "Phone storage")
                }
            }
        }
        compose.onNodeWithText("A folder name can't contain a slash.").assertExists()
        compose.onNodeWithText("Tickets/2026").assertExists()
        compose
            .onNodeWithContentDescription("Phone storage")
            .assert(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.32f, 0f..1f)))
    }

    @Test
    fun catalogRendersInLightAndDark() {
        var dark by mutableStateOf(false)
        compose.setContent { AtomicAtomsCatalog(darkTheme = dark) }
        compose.onNodeWithContentDescription("Move to trash").assertExists()
        dark = true
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Move to trash").assertExists()
    }
}
