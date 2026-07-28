package io.celox.flipperripper.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OnDeviceEngine

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RemoteEngine

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WebViewEngine
