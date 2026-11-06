package com.example

import com.example.core.model.LanguageMode
import com.example.core.model.PersonalityTraits
import com.example.core.model.SimulatedEmotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ExampleModelTest {
    @Test
    fun testLanguageModes() {
        assertEquals("ar", LanguageMode.ARABIC_MSA.code)
        assertEquals("ar_iq", LanguageMode.ARABIC_IRAQI.code)
        assertEquals("en", LanguageMode.ENGLISH.code)
    }

    @Test
    fun testPersonalityBaseline() {
        val traits = PersonalityTraits()
        assertNotNull(traits.communicationStyle)
        assertEquals(0.85f, traits.curiosity, 0.01f)
    }

    @Test
    fun testSimulatedEmotions() {
        val emotions = SimulatedEmotion.values()
        assertEquals(8, emotions.size)
    }
}
