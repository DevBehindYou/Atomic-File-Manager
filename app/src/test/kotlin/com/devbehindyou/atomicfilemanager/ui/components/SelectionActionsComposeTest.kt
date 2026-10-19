package com.devbehindyou.atomicfilemanager.ui.components

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicActionStrip
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicStripAction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SelectionActionsComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun extraSelectionActionsRemainReachableWithoutCrowdingTheToolbar() {
        val actions = listOf("Copy", "Move", "Zip", "Delete", "Info", "Rename", "Hide", "Add to favourites")
        val invoked = mutableListOf<String>()
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                AtomicActionStrip(
                    actions.map {
                            label ->
                        AtomicStripAction(label, AtomicIcons.Document, { invoked += label })
                    },
                )
            }
        }
        compose.onNodeWithContentDescription("Copy").assertExists()
        compose.onNodeWithContentDescription("Rename").assertDoesNotExist()
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithContentDescription("Rename").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("Rename"), invoked) }
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithContentDescription("Hide").assertExists()
        compose.onNodeWithContentDescription("Add to favourites").assertExists()
    }
}
