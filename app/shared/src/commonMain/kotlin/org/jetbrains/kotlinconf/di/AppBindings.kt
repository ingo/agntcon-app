package org.jetbrains.kotlinconf.di

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.kotlinconf.DeviceTimeProvider
import org.jetbrains.kotlinconf.TimeProvider
import org.jetbrains.kotlinconf.utils.Logger
import kotlin.reflect.KClass
import io.ktor.client.plugins.logging.Logger as KtorLogger

@BindingContainer
@ContributesTo(AppScope::class)
object AppBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun provideMetroViewModelFactory(
        viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>,
        manualAssistedFactoryProviders: Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>,
    ): MetroViewModelFactory = object : MetroViewModelFactory() {
        override val viewModelProviders get() = viewModelProviders
        override val manualAssistedFactoryProviders get() = manualAssistedFactoryProviders
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @SingleIn(AppScope::class)
    fun provideHttpClient(
        logger: Logger,
    ): HttpClient {
        return HttpClient {
            install(ContentNegotiation) {
                json()
            }

            install(Logging) {
                level = LogLevel.HEADERS
                this.logger = object : KtorLogger {
                    override fun log(message: String) {
                        logger.log("HttpClient") { message }
                    }
                }
            }

            expectSuccess = true
            install(HttpTimeout) {
                requestTimeoutMillis = 5000
            }

            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 3)
                exponentialDelay()
            }
        }
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideTimeProvider(): TimeProvider = DeviceTimeProvider()
}
