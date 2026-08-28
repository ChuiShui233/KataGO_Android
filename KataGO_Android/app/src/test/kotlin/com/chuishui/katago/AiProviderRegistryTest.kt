package com.chuishui.katago

import com.chuishui.katago.ai.registry.AiProviderRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderRegistryTest {

    @Test
    fun registerAndGet() {
        val registry = AiProviderRegistry()
        registry.register(FakeProvider("groq"))
        assertEquals("groq", registry.get("groq")?.id)
    }

    @Test
    fun getAllReturnsRegistered() {
        val registry = AiProviderRegistry()
        registry.register(FakeProvider("a"))
        registry.register(FakeProvider("b"))
        assertTrue(registry.getAll().any { it.id == "a" })
        assertTrue(registry.getAll().any { it.id == "b" })
    }

    @Test
    fun unregisterRemoves() {
        val registry = AiProviderRegistry()
        registry.register(FakeProvider("a"))
        registry.unregister("a")
        assertNull(registry.get("a"))
    }

    @Test
    fun isAvailableReflectsProvider() {
        val registry = AiProviderRegistry()
        registry.register(FakeProvider("a"))
        kotlinx.coroutines.runBlocking {
            assertTrue(registry.isAvailable("a"))
            assertTrue(!registry.isAvailable("missing"))
        }
    }
}