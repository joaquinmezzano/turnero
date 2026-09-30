package com.turnero.app.core.format

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * El formateador se prueba pasando siempre el `locale` explicito: el metodo lo acepta
 * justo para que el test no dependa del locale por defecto de la maquina que lo corre
 * (que en CI puede ser cualquiera y haria fallar el test sin que cambie el codigo).
 *
 * Los simbolos de moneda cambian con los datos CLDR del JDK, asi que los locales cuyo
 * formato exacto es estable se comparan contra el texto completo y los demas se
 * comparan contra el separador decimal y de miles, que es lo que el formatter decide.
 */
class CurrencyFormatterTest {

    private val estadosUnidos = Locale.US
    private val argentina = Locale("es", "AR")
    private val alemania = Locale.GERMANY

    @Test
    fun `4500 centavos se formatean como 45 con dos decimales`() {
        assertEquals("\$45.00", CurrencyFormatter.formatear(4_500L, estadosUnidos))
    }

    @Test
    fun `450000 centavos se formatean con separador de miles`() {
        assertEquals("\$4,500.00", CurrencyFormatter.formatear(450_000L, estadosUnidos))
    }

    @Test
    fun `un centavo no se redondea a cero`() {
        assertEquals("\$0.01", CurrencyFormatter.formatear(1L, estadosUnidos))
    }

    @Test
    fun `noventa y nueve centavos conservan los dos decimales`() {
        assertEquals("\$0.99", CurrencyFormatter.formatear(99L, estadosUnidos))
    }

    @Test
    fun `cero centavos se formatea como cero con moneda`() {
        assertEquals("\$0.00", CurrencyFormatter.formatear(0L, estadosUnidos))
    }

    @Test
    fun `un precio negativo conserva el signo`() {
        assertEquals("-\$45.00", CurrencyFormatter.formatear(-4_500L, estadosUnidos))
    }

    @Test
    fun `el locale argentino usa coma decimal y punto de miles`() {
        val resultado = CurrencyFormatter.formatear(450_000L, argentina)

        assertTrue(resultado.contains("4.500,00"), "Esperaba coma decimal en: $resultado")
    }

    @Test
    fun `el locale argentino usa el simbolo de su moneda`() {
        assertTrue(CurrencyFormatter.formatear(4_500L, argentina).contains("$"))
    }

    @Test
    fun `el locale aleman usa coma decimal con el simbolo al final`() {
        val resultado = CurrencyFormatter.formatear(450_000L, alemania)

        assertTrue(resultado.contains("4.500,00"), "Esperaba coma decimal en: $resultado")
        assertTrue(resultado.endsWith("€"), "Esperaba el simbolo al final en: $resultado")
    }

    @Test
    fun `el mismo precio se formatea distinto segun el locale`() {
        val centavos = 450_000L

        assertTrue(
            CurrencyFormatter.formatear(centavos, estadosUnidos) !=
                CurrencyFormatter.formatear(centavos, argentina),
        )
    }
}