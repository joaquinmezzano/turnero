package com.turnero.app.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Reemplaza `Dispatchers.Main` por uno controlado durante el test.
 *
 * `viewModelScope` usa `Dispatchers.Main.immediate`. Sin esto, todo test de ViewModel
 * falla con `Module with the Main dispatcher had failed to initialize`, o peor: si el
 * `Main` real llegara a resolver (por el fallback de lifecycle ante un `not mocked`),
 * el `launch` correria sobre el dispatcher de la maquina y el test seria intermitente.
 *
 * Se declara con la forma doble a proposito:
 * - `TestWatcher` (JUnit 4) para poder usarla con `@get:Rule`.
 * - `BeforeEachCallback` / `AfterEachCallback` (Jupiter) para `@RegisterExtension` o
 *   `@ExtendWith`.
 *
 * JUnit 5 **ignora** `@Rule`, asi que un test Jupiter tiene que registrarla por
 * `@RegisterExtension`. La clase cumple los dos papeles para no duplicarla.
 *
 * `testDispatcher` es publico y hay que pasarlo a `runTest`:
 *
 * ```
 * @get:RegisterExtension
 * val mainDispatcherRule = MainDispatcherRule()
 *
 * fun prueba() = runTest(mainDispatcherRule.testDispatcher) { ... }
 * ```
 *
 * Asi el `runTest` y el ViewModel comparten el mismo `TestCoroutineScheduler`; si no,
 * `advanceUntilIdle()` avanza un reloj que no es el del ViewModel y el test espera por
 * algo que nunca ocurre.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher(), BeforeEachCallback, AfterEachCallback {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }

    override fun beforeEach(context: ExtensionContext) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun afterEach(context: ExtensionContext) {
        Dispatchers.resetMain()
    }
}