package com.devbehindyou.atomicfilemanager

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId

/** A listing can have the fixture key before its row is composed in a short viewport. */
internal fun ComposeTestRule.revealBrowseItem(
    id: FileNodeId,
    timeoutMillis: Long,
): SemanticsNodeInteraction {
    val listingWithKey =
        (hasTestTag("file_list") or hasTestTag("file_grid")) and
            SemanticsMatcher("Browse listing contains fixture key ${id.raw}") { node ->
                val index = node.config.getOrNull(SemanticsProperties.IndexForKey)?.invoke(id.raw)
                index != null && index >= 0
            }
    waitUntil(timeoutMillis) { onAllNodes(listingWithKey).fetchSemanticsNodes().isNotEmpty() }
    onNode(listingWithKey).performScrollToKey(id.raw)
    return onNodeWithTag("file_item_${id.raw}").assertIsDisplayed()
}
