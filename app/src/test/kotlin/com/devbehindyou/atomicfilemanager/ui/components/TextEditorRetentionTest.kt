package com.devbehindyou.atomicfilemanager.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModelProvider
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.usecase.EditableText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34])
class TextEditorRetentionTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun actualActivityRecreationRetainsDraftSelectionAndFormatWithoutReopeningAndFinishClearsIt() {
        var opens = 0
        val factory =
            TextEditorSessionsViewModel.factory(dispatcher, {
                opens++
                FileResult.Success(EditableText("original", EDITOR_TEST_FORMAT))
            }, { node, _, _ -> FileResult.Success(node) })
        val controller = Robolectric.buildActivity(EditorRetentionActivity::class.java).setup()
        try {
            val firstActivity = controller.get()
            val firstModel = ViewModelProvider(firstActivity, factory)[TextEditorSessionsViewModel::class.java]
            val first = firstModel.session(editorTestNode())
            first.load()
            val draft = TextFieldValue("unsaved draft", TextRange(2, 8))
            first.edit(draft)
            controller.recreate()
            val secondActivity = controller.get()
            assertNotSame(firstActivity, secondActivity)
            val secondModel = ViewModelProvider(secondActivity, factory)[TextEditorSessionsViewModel::class.java]
            val second = secondModel.session(editorTestNode())
            second.load()
            assertSame(firstModel, secondModel)
            assertSame(first, second)
            assertEquals(draft, second.state.value.value)
            assertEquals(EDITOR_TEST_FORMAT, second.state.value.format)
            assertEquals(1, opens)
            controller.pause().stop().destroy()
            assertEquals("", second.state.value.value.text)
            assertEquals("", second.state.value.savedText)
            assertNull(second.state.value.current)
        } finally {
            if (!controller.get().isDestroyed) controller.pause().stop().destroy()
        }
    }

    @Test
    fun replacementIdentityReattachesTheSameSessionAndClosingStartsFreshOnReopen() {
        val replacement = editorTestNode("file:/new-opaque-id")
        val factory =
            TextEditorSessionsViewModel.factory(dispatcher, {
                FileResult.Success(EditableText("old", EDITOR_TEST_FORMAT))
            }, { _, _, _ -> FileResult.Success(replacement) })
        val controller = Robolectric.buildActivity(EditorRetentionActivity::class.java).setup()
        try {
            val model = ViewModelProvider(controller.get(), factory)[TextEditorSessionsViewModel::class.java]
            val first = model.session(editorTestNode())
            first.load()
            first.edit(TextFieldValue("new"))
            first.save()
            assertSame(first, model.session(replacement))
            model.release(first)
            assertEquals("", first.state.value.value.text)
            val reopened = model.session(replacement)
            assertNotSame(first, reopened)
            reopened.load()
            assertFalse(reopened.state.value.dirty)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}

class EditorRetentionActivity : ComponentActivity()
