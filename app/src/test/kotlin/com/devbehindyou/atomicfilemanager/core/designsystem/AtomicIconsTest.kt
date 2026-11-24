package com.devbehindyou.atomicfilemanager.core.designsystem

import androidx.compose.ui.graphics.vector.ImageVector
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AtomicIconsTest {
    @Test
    fun everyBundledIconParsesAndReusesItsVector() {
        val getters = AtomicIcons::class.java.methods.filter { it.returnType == ImageVector::class.java }
        assertTrue("The shared icon catalogue must not be empty", getters.isNotEmpty())
        getters.forEach { getter ->
            val vector = getter.invoke(AtomicIcons) as ImageVector
            assertEquals(getter.name, 24f, vector.viewportWidth, 0f)
            assertEquals(getter.name, 24f, vector.viewportHeight, 0f)
            assertTrue("${getter.name} must contain paths", vector.root.size > 0)
            assertSame(getter.name, vector, getter.invoke(AtomicIcons))
        }
    }
}
