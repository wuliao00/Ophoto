package com.ophoto

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ophoto.data.repository.BuiltInPresets
import com.ophoto.data.repository.FieldAssistantCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OphotoInstrumentedSmokeTest {

    @Test
    fun appContextAndCatalogIntegrity() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.ophoto", appContext.packageName)
        assertEquals(12, BuiltInPresets.ALL.size)
        assertTrue(FieldAssistantCatalog.SHOT_CARDS.isNotEmpty())
        assertTrue(FieldAssistantCatalog.LIGHTING_GUIDES.isNotEmpty())
    }
}
