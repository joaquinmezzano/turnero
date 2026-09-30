package com.turnero.app

import com.turnero.app.ui.theme.LightColors
import com.turnero.app.ui.theme.TurneroTheme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolchainSmokeTest {

    @Test
    fun `JUnit 5 corre en unit tests`() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun `el tema declara un color primario`() {
        assertTrue(LightColors.primary.value > 0uL)
    }
}
