package io.celox.flipperripper.di

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    /**
     * The app-wide background scope. It carries a [CoroutineExceptionHandler] on purpose: a
     * `SupervisorJob` only stops failures from cancelling *siblings* — an uncaught throwable still
     * reaches the thread's default handler and kills the process. Since this scope hosts start-up
     * warm-up and long-lived engine state, an unhandled failure here used to crash the app on launch.
     * Background housekeeping should degrade quietly instead.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ): CoroutineScope {
        val handler =
            CoroutineExceptionHandler { _, throwable ->
                Log.e("FlipperApp", "Unhandled failure in application scope", throwable)
            }
        return CoroutineScope(SupervisorJob() + dispatcher + handler)
    }
}
