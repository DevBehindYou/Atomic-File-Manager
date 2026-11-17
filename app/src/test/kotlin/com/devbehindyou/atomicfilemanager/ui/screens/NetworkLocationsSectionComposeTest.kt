package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.NetworkProtocol
import com.devbehindyou.atomicfilemanager.domain.model.NetworkServerConfig
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h740dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NetworkLocationsSectionComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun forwardsSavedIdentityAndStartsSetupWithTheChosenProtocol() {
        val server = NetworkServerConfig("opaque", "Office NAS", NetworkProtocol.SMB, "nas.example", remotePath = "/Share")
        var opened: FileNodeId? = null
        var removed: NetworkServerConfig? = null
        var added: NetworkProtocol? = null

        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    NetworkLocationsSection(
                        servers = listOf(server),
                        loadingFailed = false,
                        busy = false,
                        onRetry = {},
                        onBrowse = { opened = it.toRootNodeId() },
                        onRemove = { removed = it },
                        onAdd = { added = it },
                    )
                }
            }
        }

        compose.onNodeWithText(server.name).performClick()
        compose.onNodeWithContentDescription("Remove Office NAS").performClick()
        compose.onNodeWithText(NetworkProtocol.FTPS.displayName).performScrollTo().performClick()
        assertEquals(server.toRootNodeId(), opened)
        assertEquals(server, removed)
        assertEquals(NetworkProtocol.FTPS, added)
    }

    @Test
    fun readErrorOnlyOffersRetryUntilTheSavedListIsReady() {
        var retries = 0
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                NetworkLocationsSection(
                    servers = null,
                    loadingFailed = true,
                    busy = false,
                    onRetry = { retries++ },
                    onBrowse = {},
                    onRemove = {},
                    onAdd = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Could not load saved servers.").assertIsDisplayed()
        compose.onNodeWithText(NetworkProtocol.SMB.displayName).assertDoesNotExist()
        compose.onNodeWithContentDescription("Try again").performClick()
        assertEquals(1, retries)
    }
}
